package dev.gabrie.brainwave.ai

import dev.gabrie.brainwave.settings.NoteLanguage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data class Downloading(val fraction: Float) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}

/**
 * Downloads, verifies and tracks the on-device speech models.
 *
 * The models used to ship inside the APK. That made the app ~90 MB, and it is
 * why they are no longer bundled: F-Droid builds from source and does not take
 * large binary blobs, and the proven pattern for apps that use Vosk there is to
 * fetch the model on first use. Downloads here are opt-in (the UI asks first),
 * and nothing downloaded is trusted until [ModelInstaller] has matched it
 * against the checksum pinned in [SpeechModelCatalog].
 *
 * Android-free on purpose — directories and the coroutine scope are passed in —
 * so the whole flow runs against a local mock server in plain unit tests.
 *
 * Models already on a phone from the bundled builds sit in the same folders, and
 * are recognised without a new download.
 */
class SpeechModelManager(
    private val root: File,
    private val scratch: File,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val catalog: (NoteLanguage) -> SpeechModel = SpeechModelCatalog::forLanguage,
    private val installer: ModelInstaller = ModelInstaller(),
) {

    private val _status = MutableStateFlow(NoteLanguage.entries.associateWith { initialStatus(it) })
    val status: StateFlow<Map<NoteLanguage, ModelStatus>> = _status.asStateFlow()

    private val jobs = ConcurrentHashMap<NoteLanguage, Job>()

    fun directory(language: NoteLanguage): File = File(root, language.modelDir)

    fun model(language: NoteLanguage): SpeechModel = catalog(language)

    /**
     * True when a usable copy is on disk. A copy carrying a checksum marker that
     * no longer matches the catalogue is stale and counts as not installed; a
     * copy with no marker came from a bundled build, which shipped the same
     * upstream version, and is accepted.
     */
    fun isInstalled(language: NoteLanguage): Boolean {
        val dir = directory(language)
        if (!File(dir, ModelInstaller.REQUIRED_FILES.first()).isFile) return false
        val marker = File(dir, ModelInstaller.MARKER)
        return !marker.isFile || marker.readText().trim().equals(catalog(language).sha256, ignoreCase = true)
    }

    private fun initialStatus(language: NoteLanguage): ModelStatus =
        if (isInstalled(language)) ModelStatus.Ready else ModelStatus.NotInstalled

    /** Starts a download; does nothing if one is running or the model is already here. */
    fun download(language: NoteLanguage) {
        if (jobs[language]?.isActive == true || isInstalled(language)) return
        // Set before launching, so anything waiting on the status sees the
        // download rather than a momentary "not installed".
        set(language, ModelStatus.Downloading(0f))
        jobs[language] = scope.launch(Dispatchers.IO) { runDownload(language) }
    }

    fun cancel(language: NoteLanguage) {
        jobs.remove(language)?.cancel()
        set(language, if (isInstalled(language)) ModelStatus.Ready else ModelStatus.NotInstalled)
    }

    /** Waits out a running download. True if the model is usable afterwards. */
    suspend fun awaitReady(language: NoteLanguage): Boolean {
        val settled = _status.first { it[language] !is ModelStatus.Downloading }
        return settled[language] is ModelStatus.Ready
    }

    private suspend fun runDownload(language: NoteLanguage) {
        val model = catalog(language)
        val part = File(scratch, "${model.upstreamName}.zip.part")
        try {
            scratch.mkdirs()
            val call = http.newCall(Request.Builder().url(model.url).build())
            val handle = kotlin.coroutines.coroutineContext.job.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("The server answered ${response.code}.")
                    val body = response.body ?: throw IOException("The server sent no data.")
                    copyWithProgress(body.byteStream(), part, model, language)
                }
            } finally {
                handle.dispose()
            }

            installer.install(part, model, directory(language))
            set(language, ModelStatus.Ready)
        } catch (e: CancellationException) {
            set(language, ModelStatus.NotInstalled)
            throw e
        } catch (e: Exception) {
            set(language, ModelStatus.Failed(describe(e)))
        } finally {
            part.delete()
        }
    }

    private fun copyWithProgress(input: java.io.InputStream, part: File, model: SpeechModel, language: NoteLanguage) {
        var read = 0L
        var reported = -1
        input.use { source ->
            FileOutputStream(part).use { sink ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = source.read(buffer)
                    if (n < 0) break
                    read += n
                    // The size is pinned, so a longer stream is wrong, not generous.
                    if (read > model.sizeBytes) throw IOException("The download was larger than expected.")
                    sink.write(buffer, 0, n)

                    val percent = (read * 100 / model.sizeBytes).toInt()
                    if (percent != reported) {
                        reported = percent
                        set(language, ModelStatus.Downloading(read.toFloat() / model.sizeBytes))
                    }
                }
            }
        }
        if (read != model.sizeBytes) {
            throw IOException("The download was incomplete ($read of ${model.sizeBytes} bytes).")
        }
    }

    private fun describe(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "No connection to the download server. Check your internet and try again."
        else -> e.message ?: "The download failed."
    }

    private fun set(language: NoteLanguage, status: ModelStatus) {
        _status.update { it + (language to status) }
    }
}
