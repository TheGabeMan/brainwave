package dev.gabrie.brainwave.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodes a recorded audio file to 16-bit mono PCM at a fixed sample rate.
 *
 * Recordings are kept as AAC in an MP4 container so the email attachment stays
 * small, but Vosk needs raw PCM — this bridges the two using the platform
 * decoder, so no audio codec is bundled with the app.
 *
 * Output is delivered in chunks rather than one big array: a five-minute note
 * is around 10 MB of PCM, and the recogniser wants to consume it incrementally
 * anyway.
 */
object PcmDecoder {

    private const val TIMEOUT_US = 10_000L

    class DecodeException(message: String, cause: Throwable? = null) : IOException(message, cause)

    /**
     * @param onPcm receives mono 16-bit samples at [targetSampleRate]. The array
     *   passed in is reused between calls — copy it if you need to keep it.
     */
    fun decode(file: File, targetSampleRate: Int, onPcm: (ShortArray, Int) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(file.absolutePath)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw DecodeException("No audio track in ${file.name}")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw DecodeException("Audio track has no MIME type")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var channels = inputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var sampleRate = inputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, targetSampleRate)
            var resampler = LinearResampler(sampleRate, targetSampleRate)

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEnd = false
            var sawOutputEnd = false
            var mono = ShortArray(0)

            while (!sawOutputEnd) {
                if (!sawInputEnd) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEnd = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // The real rate and channel count are only trustworthy
                        // here — the track format can under-report both.
                        val output = codec.outputFormat
                        channels = output.optInt(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        val newRate = output.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        if (newRate != sampleRate || !resampler.hasStarted()) {
                            sampleRate = newRate
                            resampler = LinearResampler(sampleRate, targetSampleRate)
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputIndex < 0) continue

                        if (bufferInfo.size > 0) {
                            val buffer = codec.getOutputBuffer(outputIndex)!!
                            buffer.position(bufferInfo.offset)
                            buffer.limit(bufferInfo.offset + bufferInfo.size)
                            val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()

                            val frames = shorts.remaining() / channels
                            if (mono.size < frames) mono = ShortArray(frames)
                            downmix(shorts, channels, frames, mono)

                            val resampled = resampler.resample(mono, frames)
                            if (resampled.isNotEmpty()) onPcm(resampled, resampled.size)
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEnd = true
                        }
                    }
                }
            }
        } catch (e: DecodeException) {
            throw e
        } catch (e: Exception) {
            throw DecodeException("Could not decode ${file.name}: ${e.message}", e)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /** Averages interleaved channels into [out]; a no-op copy when already mono. */
    private fun downmix(
        source: java.nio.ShortBuffer,
        channels: Int,
        frames: Int,
        out: ShortArray,
    ) {
        if (channels <= 1) {
            source.get(out, 0, frames)
            return
        }
        for (frame in 0 until frames) {
            var sum = 0
            for (channel in 0 until channels) sum += source.get().toInt()
            out[frame] = (sum / channels).toShort()
        }
    }

    private fun MediaFormat.optInt(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}
