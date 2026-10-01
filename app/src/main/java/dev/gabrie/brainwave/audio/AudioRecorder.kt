package dev.gabrie.brainwave.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records mono AAC into an MP4/`.m4a` container.
 *
 * 16 kHz mono at 32 kbps is transparent for speech, keeps a five-minute
 * brainwave under 1.2 MB (mail-attachment friendly) and matches what
 * speech-to-text services downsample to anyway.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): File {
        check(recorder == null) { "Already recording" }

        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "brainwave-${System.currentTimeMillis()}.m4a")

        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

        mr.apply {
            // VOICE_RECOGNITION skips the aggressive tuning MIC applies and is
            // the source a Bluetooth headset's SCO mic actually feeds.
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16_000)
            setAudioChannels(1)
            setAudioEncodingBitRate(32_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = mr
        outputFile = file
        startedAt = System.currentTimeMillis()
        return file
    }

    /** Returns the finished file, or null if the clip was too short to be valid. */
    fun stop(): File? {
        val mr = recorder ?: return null
        recorder = null
        val file = outputFile
        outputFile = null

        // MediaRecorder throws if stopped before it has written a usable frame.
        val stopped = runCatching { mr.stop() }.isSuccess
        runCatching { mr.release() }

        if (!stopped || file == null || !file.exists() || file.length() < MIN_VALID_BYTES) {
            file?.delete()
            return null
        }
        return file
    }

    fun cancel() {
        val mr = recorder ?: return
        recorder = null
        runCatching { mr.stop() }
        runCatching { mr.release() }
        outputFile?.delete()
        outputFile = null
    }

    fun elapsedMillis(): Long = if (isRecording) System.currentTimeMillis() - startedAt else 0L

    /** 0f..1f, for the waveform. Returns 0 when not recording. */
    fun amplitude(): Float {
        val mr = recorder ?: return 0f
        val raw = runCatching { mr.maxAmplitude }.getOrDefault(0)
        return (raw / MAX_AMPLITUDE).coerceIn(0f, 1f)
    }

    private companion object {
        const val MIN_VALID_BYTES = 1024L
        const val MAX_AMPLITUDE = 20_000f
    }
}
