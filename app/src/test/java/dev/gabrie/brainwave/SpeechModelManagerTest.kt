package dev.gabrie.brainwave

import dev.gabrie.brainwave.ai.ModelInstaller
import dev.gabrie.brainwave.ai.ModelStatus
import dev.gabrie.brainwave.ai.SpeechModel
import dev.gabrie.brainwave.ai.SpeechModelManager
import dev.gabrie.brainwave.settings.NoteLanguage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Runs the whole download-verify-install flow against a local server. */
class SpeechModelManagerTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val lang = NoteLanguage.DUTCH

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun root() = File(tmp.root, "vosk")

    private fun manager(model: (SpeechModel) -> SpeechModel = { it }, archive: File): SpeechModelManager {
        val base = TestZips.modelFor(archive, lang).copy(url = server.url("/model.zip").toString())
        return SpeechModelManager(
            root = root(),
            scratch = File(tmp.root, "cache"),
            http = OkHttpClient(),
            scope = scope,
            catalog = { model(base) },
        )
    }

    private fun serve(file: File, code: Int = 200) {
        val body = Buffer().write(file.readBytes())
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    private fun settle(manager: SpeechModelManager): ModelStatus = runBlocking {
        withTimeout(15_000) { manager.awaitReady(lang) }
        manager.status.value.getValue(lang)
    }

    @Test fun `a good download is verified, installed and reported ready`() {
        val zip = TestZips.valid(File(tmp.root, "src.zip"))
        serve(zip)
        val m = manager(archive = zip)

        assertFalse(m.isInstalled(lang))
        m.download(lang)

        assertEquals(ModelStatus.Ready, settle(m))
        assertTrue(m.isInstalled(lang))
        assertEquals("acoustic-model", File(m.directory(lang), "am/final.mdl").readText())
        assertFalse("the downloaded zip is not kept", File(tmp.root, "cache").listFiles().orEmpty().any { it.name.endsWith(".part") })
    }

    @Test fun `a download that does not match the pinned checksum is rejected`() {
        val zip = TestZips.valid(File(tmp.root, "src.zip"))
        serve(zip)
        val m = manager(model = { it.copy(sha256 = "f".repeat(64)) }, archive = zip)

        m.download(lang)

        val status = settle(m)
        assertTrue(status.toString(), status is ModelStatus.Failed)
        assertTrue((status as ModelStatus.Failed).reason.contains("checksum"))
        assertFalse(m.isInstalled(lang))
    }

    @Test fun `an http error is reported and installs nothing`() {
        val zip = TestZips.valid(File(tmp.root, "src.zip"))
        server.enqueue(MockResponse().setResponseCode(404))
        val m = manager(archive = zip)

        m.download(lang)

        val status = settle(m)
        assertTrue(status is ModelStatus.Failed)
        assertTrue((status as ModelStatus.Failed).reason.contains("404"))
        assertFalse(m.isInstalled(lang))
    }

    @Test fun `a truncated download is rejected as incomplete`() {
        val zip = TestZips.valid(File(tmp.root, "src.zip"))
        // The pinned size says one thing; the server sends less.
        server.enqueue(MockResponse().setBody(Buffer().write(zip.readBytes().copyOf((zip.length() / 2).toInt()))))
        val m = manager(archive = zip)

        m.download(lang)

        val status = settle(m)
        assertTrue(status.toString(), status is ModelStatus.Failed)
        assertTrue((status as ModelStatus.Failed).reason.contains("incomplete"))
        assertFalse(m.isInstalled(lang))
    }

    @Test fun `a download longer than the pinned size is rejected`() {
        val zip = TestZips.valid(File(tmp.root, "src.zip"))
        serve(zip)
        val m = manager(model = { it.copy(sizeBytes = zip.length() - 10) }, archive = zip)

        m.download(lang)

        val status = settle(m)
        assertTrue(status is ModelStatus.Failed)
        assertTrue((status as ModelStatus.Failed).reason.contains("larger"))
    }

    @Test fun `an already installed model is not downloaded again`() {
        val zip = TestZips.valid(File(tmp.root, "src.zip"))
        val m = manager(archive = zip)
        serve(zip)
        m.download(lang)
        settle(m)
        assertEquals(1, server.requestCount)

        m.download(lang)
        Thread.sleep(200)
        assertEquals("no second request", 1, server.requestCount)
    }

    @Test fun `a model from a bundled build with no marker counts as installed`() {
        val dir = File(root(), lang.modelDir)
        File(dir, "am").mkdirs()
        File(dir, "am/final.mdl").writeText("legacy")
        val zip = TestZips.valid(File(tmp.root, "src.zip"))

        assertTrue(manager(archive = zip).isInstalled(lang))
    }

    @Test fun `a copy whose marker no longer matches the catalogue is stale`() {
        val dir = File(root(), lang.modelDir)
        File(dir, "am").mkdirs()
        File(dir, "am/final.mdl").writeText("old")
        File(dir, ModelInstaller.MARKER).writeText("an-older-checksum")
        val zip = TestZips.valid(File(tmp.root, "src.zip"))

        assertFalse(manager(archive = zip).isInstalled(lang))
    }

    @Test fun `a folder without the acoustic model is not installed`() {
        File(root(), lang.modelDir).mkdirs()
        val zip = TestZips.valid(File(tmp.root, "src.zip"))
        assertFalse(manager(archive = zip).isInstalled(lang))
    }
}
