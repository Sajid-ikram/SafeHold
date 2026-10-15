package dev.ikram.sandbox.feature.viewer.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDocumentTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun document(text: String) = TextDecoder.layout(DecodedText(text, "UTF-8"), truncated = false)

    private fun TextDocument.rows() = List(rowCount) { row(it) }

    private fun TextDocument.numbers() = List(rowCount) { lineNumber(it) }

    // Decoding

    @Test
    fun readsUtf8() {
        val decoded = TextDecoder.decode("héllo ✓".toByteArray(Charsets.UTF_8))
        assertEquals("héllo ✓", decoded.text)
        assertEquals("UTF-8", decoded.encoding)
    }

    @Test
    fun fallsBackToWindows1252WhenTheBytesAreNotUtf8() {
        // "café" and a curly quote in Windows-1252: 0xE9 and 0x93 are not valid UTF-8 here.
        val decoded = TextDecoder.decode(bytes(0x63, 0x61, 0x66, 0xE9, 0x20, 0x93, 0x78))
        assertEquals("café “x", decoded.text)
        assertEquals("Windows-1252", decoded.encoding)
    }

    @Test
    fun skipsAUtf8ByteOrderMark() {
        val decoded = TextDecoder.decode(bytes(0xEF, 0xBB, 0xBF) + "hi".toByteArray())
        assertEquals("hi", decoded.text)
    }

    @Test
    fun readsUtf16InBothByteOrders() {
        assertEquals("hé", TextDecoder.decode(bytes(0xFF, 0xFE, 0x68, 0x00, 0xE9, 0x00)).text)
        assertEquals("hé", TextDecoder.decode(bytes(0xFE, 0xFF, 0x00, 0x68, 0x00, 0xE9)).text)
    }

    @Test
    fun aCharacterCutOffByTheSizeLimitIsDroppedNotMisread() {
        // "aé" is 61 C3 A9; the limit fell between C3 and A9.
        val decoded = TextDecoder.decode(bytes(0x61, 0xC3), complete = false)
        assertEquals("a", decoded.text)
        assertEquals("UTF-8", decoded.encoding)
    }

    @Test
    fun anIncompleteCharacterInAWholeFileIsNotUtf8() {
        assertEquals("Windows-1252", TextDecoder.decode(bytes(0x61, 0xC3), complete = true).encoding)
    }

    // Lines and rows

    @Test
    fun splitsOnEveryKindOfLineBreak() {
        assertEquals(listOf("a", "b", "c", "d"), document("a\r\nb\nc\rd").rows())
    }

    @Test
    fun aFinalLineBreakDoesNotAddAnEmptyLine() {
        assertEquals(2, document("a\nb\n").lineCount)
        assertEquals(listOf("a", ""), document("a\n\n").rows())
    }

    @Test
    fun anEmptyFileHasOneEmptyLine() {
        val empty = document("")
        assertEquals(1, empty.lineCount)
        assertEquals(listOf(""), empty.rows())
    }

    @Test
    fun linesAreJoinedByLineFeedsSoSearchCannotSpanThem() {
        val text = document("foo\r\nbar")
        assertEquals("foo\nbar", text.text)
        assertTrue(TextSearch.find(text.text, "obar").isEmpty())
    }

    @Test
    fun tabsExpandToFourColumnStops() {
        assertEquals(listOf("a   b", "    x", "abcd    y"), document("a\tb\n\tx\nabcd\ty").rows())
    }

    @Test
    fun controlCharactersAndDirectionOverridesAreShownNotObeyed() {
        // A right-to-left override could make "exe.txt" read as "txt.exe".
        assertEquals("a\uFFFDb\uFFFDc\uFFFD", document("a\u0007b\u202Ec\u2066").rows().single())
    }

    @Test
    fun ordinaryDirectionMarksAndJoinersAreKept() {
        val text = "שלום\u200F 👩\u200D💻"
        assertEquals(text, document(text).rows().single())
    }

    @Test
    fun terminalColourCodesAreDropped() {
        assertEquals("red plain", document("\u001B[31mred\u001B[0m plain").rows().single())
    }

    @Test
    fun longLinesAreCutIntoRowsThatKeepOneLineNumber() {
        val long = "x".repeat(TextDecoder.ROW_CHARS * 2 + 500)
        val text = document("$long\nnext")
        assertEquals(4, text.rowCount)
        assertEquals(listOf(1, null, null, 2), text.numbers())
        assertEquals(500, text.row(2).length)
        assertEquals(2, text.lineCount)
    }

    @Test
    fun rowsNeverSplitASurrogatePair() {
        val line = "a".repeat(TextDecoder.ROW_CHARS - 1) + "😀b"
        val text = document(line)
        assertEquals("a".repeat(TextDecoder.ROW_CHARS - 1), text.row(0))
        assertEquals("😀b", text.row(1))
    }

    @Test
    fun wideCharactersCountAsTwoColumns() {
        assertEquals(4, document("漢字").widestRow)
        assertEquals(5, document("abc\nhello").widestRow)
    }

    @Test
    fun hugeLineCountsAreCutAndMarkedTruncated() {
        val text = document("\n".repeat(TextDecoder.MAX_ROWS + 10))
        assertEquals(TextDecoder.MAX_ROWS, text.rowCount)
        assertTrue(text.truncated)
    }

    @Test
    fun smallFilesAreNotTruncated() = assertFalse(document("a\nb").truncated)

    @Test
    fun findsTheRowHoldingAnOffset() {
        val text = document("ab\ncd\n" + "x".repeat(TextDecoder.ROW_CHARS + 5))
        assertEquals(0, text.rowAt(0))
        assertEquals(0, text.rowAt(2))
        assertEquals(1, text.rowAt(3))
        assertEquals(2, text.rowAt(6))
        assertEquals(3, text.rowAt(6 + TextDecoder.ROW_CHARS))
        assertNull(text.lineNumber(3))
    }

    // Search

    @Test
    fun searchIgnoresCaseAndDoesNotOverlap() {
        assertEquals(
            listOf(TextMatch(0, 2), TextMatch(2, 4), TextMatch(7, 9)),
            TextSearch.find("AaaA b aa", "aa"),
        )
    }

    @Test
    fun emptySearchFindsNothing() = assertTrue(TextSearch.find("abc", "").isEmpty())

    @Test
    fun searchStopsAtTheCap() {
        assertEquals(TextSearch.MAX_MATCHES, TextSearch.find("a".repeat(TextSearch.MAX_MATCHES * 2), "a").size)
    }

    @Test
    fun highlightsAreClippedToEachRow() {
        val line = "x".repeat(TextDecoder.ROW_CHARS - 2) + "need" + "le"
        val text = document("needle\n$line")
        val matches = TextSearch.find(text.text, "needle")
        assertEquals(2, matches.size)
        assertEquals(
            listOf(TextSearch.Highlight(0, 6, isCurrent = false)),
            TextSearch.highlights(text, 0, matches, current = 1),
        )
        assertEquals(
            listOf(TextSearch.Highlight(TextDecoder.ROW_CHARS - 2, TextDecoder.ROW_CHARS, isCurrent = true)),
            TextSearch.highlights(text, 1, matches, current = 1),
        )
        assertEquals(
            listOf(TextSearch.Highlight(0, 4, isCurrent = true)),
            TextSearch.highlights(text, 2, matches, current = 1),
        )
    }
}
