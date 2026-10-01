package dev.gabrie.brainwave

import dev.gabrie.brainwave.audio.LinearResampler
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinearResamplerTest {

    @Test
    fun `matching rates pass straight through`() {
        val resampler = LinearResampler(16_000, 16_000)
        assertTrue(resampler.passthrough)
        val input = shortArrayOf(1, 2, 3, 4)
        assertEquals(4, resampler.resample(input, input.size).size)
    }

    @Test
    fun `halving the rate halves the sample count`() {
        val resampler = LinearResampler(32_000, 16_000)
        val input = ShortArray(1000) { (it % 100).toShort() }
        val output = resampler.resample(input, input.size)
        // One sample of slack: the tail fraction carries into the next chunk.
        assertTrue("got ${output.size}", abs(output.size - 500) <= 1)
    }

    @Test
    fun `upsampling produces more samples`() {
        val resampler = LinearResampler(8_000, 16_000)
        val input = ShortArray(500) { (it % 50).toShort() }
        val output = resampler.resample(input, input.size)
        assertTrue("got ${output.size}", abs(output.size - 1000) <= 2)
    }

    /**
     * The whole point of carrying state: resampling a stream in chunks must
     * yield the same count as resampling it in one go, with no drift.
     */
    @Test
    fun `chunked resampling matches a single pass`() {
        val samples = ShortArray(4800) { ((it * 7) % 1000 - 500).toShort() }

        val single = LinearResampler(48_000, 16_000).resample(samples, samples.size).size

        val chunked = LinearResampler(48_000, 16_000)
        var total = 0
        var offset = 0
        while (offset < samples.size) {
            val length = minOf(512, samples.size - offset)
            total += chunked.resample(samples.copyOfRange(offset, offset + length), length).size
            offset += length
        }

        assertTrue("single=$single chunked=$total", abs(single - total) <= 2)
    }

    @Test
    fun `a linear ramp stays linear after resampling`() {
        val resampler = LinearResampler(32_000, 16_000)
        val ramp = ShortArray(200) { (it * 10).toShort() }
        val output = resampler.resample(ramp, ramp.size)
        // Downsampling a ramp by two should step by twice as much per sample.
        for (i in 1 until output.size - 1) {
            assertEquals(20, output[i] - output[i - 1])
        }
    }
}
