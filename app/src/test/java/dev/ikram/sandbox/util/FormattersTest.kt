package dev.ikram.sandbox.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    private fun size(bytes: Long) = Formatters.size(bytes, Locale.US)

    @Test
    fun sizes() {
        assertEquals("0 B", size(0))
        assertEquals("980 B", size(980))
        assertEquals("2 KB", size(2_048))
        assertEquals("2.4 MB", size(2_400_000))
        assertEquals("41.0 MB", size(41_000_000))
        assertEquals("1.5 GB", size(1_500_000_000))
    }

    @Test
    fun usesTheLocaleDecimalSeparator() = assertEquals("2,4 MB", Formatters.size(2_400_000, Locale.GERMANY))

    @Test
    fun durations() {
        assertEquals("00:00", Formatters.duration(0, Locale.US))
        assertEquals("00:00", Formatters.duration(-500, Locale.US))
        assertEquals("04:12", Formatters.duration(252_999, Locale.US))
        assertEquals("59:59", Formatters.duration(3_599_000, Locale.US))
        assertEquals("1:02:03", Formatters.duration(3_723_000, Locale.US))
    }

    @Test
    fun speeds() {
        assertEquals("1×", Formatters.speed(1f, Locale.US))
        assertEquals("1.25×", Formatters.speed(1.25f, Locale.US))
        assertEquals("1.5×", Formatters.speed(1.5f, Locale.US))
        assertEquals("0.75×", Formatters.speed(0.75f, Locale.US))
        assertEquals("1,5×", Formatters.speed(1.5f, Locale.GERMANY))
        assertEquals("2×", Formatters.speed(2f, Locale.GERMANY))
    }
}
