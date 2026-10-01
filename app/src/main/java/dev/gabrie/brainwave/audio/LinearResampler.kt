package dev.gabrie.brainwave.audio

import kotlin.math.floor

/**
 * Streaming linear resampler for 16-bit mono PCM.
 *
 * Vosk models are trained at one sample rate and degrade badly when fed
 * anything else. The recorder asks for 16 kHz, but not every device's AAC
 * encoder honours that, so whatever comes back out of the decoder is resampled
 * to the rate the model actually wants.
 *
 * State is carried between calls (the fractional read position and the last
 * sample of the previous chunk) so a stream can be resampled chunk by chunk
 * without clicks at the boundaries.
 */
class LinearResampler(private val inputRate: Int, private val outputRate: Int) {

    private val step = inputRate.toDouble() / outputRate.toDouble()

    /** Read position within the current chunk; may start negative, see below. */
    private var position = 0.0
    private var previous: Short = 0
    private var primed = false

    val passthrough: Boolean get() = inputRate == outputRate

    fun resample(input: ShortArray, length: Int): ShortArray {
        if (length <= 0) return ShortArray(0)
        if (passthrough) return input.copyOf(length)

        val output = ShortArray(((length / step) + 2).toInt())
        var count = 0

        while (true) {
            val index = floor(position).toInt()
            // Interpolating into input[index + 1] means we stop one short of the
            // end; the leftover fraction is carried into the next chunk.
            if (index + 1 >= length) break

            val start = if (index < 0) previous else input[index]
            val end = input[index + 1]
            val fraction = position - index

            if (count == output.size) break
            output[count++] = (start + (end - start) * fraction).toInt().toShort()
            position += step
        }

        // Rebase the position for the next chunk, where index -1 refers to the
        // sample we are about to stash in `previous`.
        position -= length
        previous = input[length - 1]
        primed = true

        return if (count == output.size) output else output.copyOf(count)
    }

    /** True once at least one chunk has been consumed. */
    fun hasStarted(): Boolean = primed
}
