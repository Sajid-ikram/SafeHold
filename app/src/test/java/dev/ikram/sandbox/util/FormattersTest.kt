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
}
