package dev.ikram.sandbox.renderer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererLimitsTest {
    @Test
    fun sampleSizes() {
        assertEquals(1, RendererLimits.sampleSize(800, 600, 2048, 2048))
        assertEquals(2, RendererLimits.sampleSize(4000, 3000, 2048, 2048))
        assertEquals(4, RendererLimits.sampleSize(8000, 6000, 2048, 2048))
        // Thumbnails: 4000 / 64 = 62, the first power of two that fits 96.
        assertEquals(64, RendererLimits.sampleSize(4000, 3000, 96, 96))
        assertEquals(1, RendererLimits.sampleSize(2048, 2048, 2048, 2048))
    }

    @Test
    fun validResults() {
        assertTrue(RendererLimits.isValidResult(100, 50, 100 * 50 * 4, 2048, 2048))
    }

    @Test
    fun resultsThatLieAreRejected() {
        // Bigger than requested.
        assertFalse(RendererLimits.isValidResult(4000, 10, 4000 * 10 * 4, 2048, 2048))
        // Byte count does not match the dimensions.
        assertFalse(RendererLimits.isValidResult(100, 100, 100, 2048, 2048))
        // Nonsense dimensions.
        assertFalse(RendererLimits.isValidResult(0, 100, 0, 2048, 2048))
        assertFalse(RendererLimits.isValidResult(-1, -1, 4, 2048, 2048))
    }

    @Test
    fun oversizedSourcesExceedTheLimit() {
        assertTrue(60_000L * 60_000 > RendererLimits.MAX_SOURCE_PIXELS)
        assertTrue(8_000L * 6_000 <= RendererLimits.MAX_SOURCE_PIXELS)
    }
}
