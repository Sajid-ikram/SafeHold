package dev.ikram.sandbox.core.filetype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSanitizerTest {
    @Test fun keepsOrdinaryNames() = assertEquals("north-coast-map.pdf", NameSanitizer.sanitize("north-coast-map.pdf"))

    @Test fun nullOrBlankFallsBack() {
        assertEquals(NameSanitizer.FALLBACK_NAME, NameSanitizer.sanitize(null))
        assertEquals(NameSanitizer.FALLBACK_NAME, NameSanitizer.sanitize("   "))
    }

    @Test fun keepsOnlyTheLastPathSegment() {
        assertEquals("passwd", NameSanitizer.sanitize("../../etc/passwd"))
        assertEquals("evil.exe", NameSanitizer.sanitize("C:\\Windows\\System32\\evil.exe"))
    }

    @Test fun dotOnlyNamesFallBack() {
        assertEquals(NameSanitizer.FALLBACK_NAME, NameSanitizer.sanitize(".."))
        assertEquals(NameSanitizer.FALLBACK_NAME, NameSanitizer.sanitize("a/.."))
    }

    @Test fun stripsRightToLeftOverride() = assertEquals("photogpj.exe", NameSanitizer.sanitize("photo\u202Egpj.exe"))

    @Test fun stripsZeroWidthAndBidiIsolates() =
        assertEquals("invoice.pdf", NameSanitizer.sanitize("in\u200Bvoice\u2066.pdf\u2069"))

    @Test fun stripsControlCharacters() = assertEquals("report.pdf", NameSanitizer.sanitize("report\u0000\u0007.pdf"))

    @Test fun collapsesWhitespaceThatHidesExtensions() =
        assertEquals("invoice.pdf .exe", NameSanitizer.sanitize("invoice.pdf\u0020\u0020\u0020\t\t\n.exe"))

    @Test fun trimsLeadingAndTrailingDots() = assertEquals("hidden.txt", NameSanitizer.sanitize(".hidden.txt."))

    @Test
    fun truncatesLongNamesButKeepsTheExtension() {
        val result = NameSanitizer.sanitize("a".repeat(500) + ".PDF")
        assertTrue(result.codePointCount(0, result.length) <= NameSanitizer.MAX_LENGTH)
        assertTrue(result.endsWith("\u2026.PDF"))
    }

    @Test
    fun truncationDoesNotSplitSurrogatePairs() {
        val result = NameSanitizer.sanitize("🙂".repeat(300) + ".txt")
        assertTrue(result.endsWith(".txt"))
        result.forEachIndexed { index, char ->
            if (Character.isHighSurrogate(char)) assertTrue(Character.isLowSurrogate(result[index + 1]))
        }
    }

    @Test
    fun extensions() {
        assertEquals("exe", NameSanitizer.extensionOf("movie.mp4.exe"))
        assertEquals("pdf", NameSanitizer.extensionOf("Report.PDF"))
        assertEquals("exe", NameSanitizer.extensionOf("trailing.exe."))
        assertEquals("exe", NameSanitizer.extensionOf("photo\u202Egpj.exe"))
        assertNull(NameSanitizer.extensionOf("README"))
        assertNull(NameSanitizer.extensionOf(".bashrc"))
        assertNull(NameSanitizer.extensionOf("name."))
        assertNull(NameSanitizer.extensionOf("weird.ext with spaces"))
        assertNull(NameSanitizer.extensionOf(null))
    }
}
