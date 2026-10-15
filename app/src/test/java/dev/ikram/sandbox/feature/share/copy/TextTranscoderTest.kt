package dev.ikram.sandbox.feature.share.copy

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTranscoderTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun transcode(input: ByteArray): String {
        var out = ByteArrayOutputStream()
        TextTranscoder.transcode(
            open = { ByteArrayInputStream(input) },
            create = { ByteArrayOutputStream().also { out = it } },
        )
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    private fun clean(text: String) = StringWriter().also { TextTranscoder.clean(StringReader(text), it) }.toString()

    @Test
    fun utf8StaysAsItIs() = assertEquals("héllo ✓\nline two", transcode("héllo ✓\nline two".toByteArray()))

    @Test
    fun windows1252BecomesUtf8() = assertEquals("café", transcode(bytes(0x63, 0x61, 0x66, 0xE9)))

    @Test
    fun aLateInvalidByteStillSwitchesTheWholeFile() {
        // Valid UTF-8 for 100 KB, then a Windows-1252 é: the restart must not keep the first attempt.
        val text = "a".repeat(100_000).toByteArray() + bytes(0xE9)
        assertEquals("a".repeat(100_000) + "é", transcode(text))
    }

    @Test
    fun byteOrderMarksAreReadAndNotWritten() {
        assertEquals("hi", transcode(bytes(0xEF, 0xBB, 0xBF) + "hi".toByteArray()))
        assertEquals("hé", transcode(bytes(0xFF, 0xFE, 0x68, 0x00, 0xE9, 0x00)))
        assertEquals("hé", transcode(bytes(0xFE, 0xFF, 0x00, 0x68, 0x00, 0xE9)))
    }

    @Test
    fun layoutCharactersAreKept() = assertEquals("a\tb\r\nc\u000Cd", clean("a\tb\r\nc\u000Cd"))

    @Test
    fun hiddenCharactersAreDropped() {
        assertEquals("abcdef", clean("a\u0000b\u0007c\u202Ed\u2066e\uFEFFf\u0085"))
    }

    @Test
    fun terminalColourCodesAreDropped() {
        assertEquals("red plain!", clean("\u001B[1;31mred\u001B[0m plain\u001Bc!"))
    }

    @Test
    fun ordinaryUnicodeIsKept() {
        val text = "שלום \u200F 👩\u200D💻 漢字"
        assertEquals(text, clean(text))
    }
}
