package dev.ikram.sandbox.feature.viewer.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {
    private val hash = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"

    @Test
    fun theSameFileAlwaysGetsTheSameShape() = assertEquals(waveform(hash), waveform(hash))

    @Test
    fun differentFilesGetDifferentShapes() = assertNotEquals(waveform(hash), waveform(hash.reversed()))

    @Test
    fun barsStayVisibleAndWithinTheStrip() {
        val bars = waveform(hash)
        assertEquals(40, bars.size)
        assertTrue(bars.all { it in 0.25f..1f })
    }

    @Test
    fun aMissingHashStillDraws() = assertEquals(40, waveform("").size)
}
