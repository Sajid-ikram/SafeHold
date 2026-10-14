package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.filetype.Fixtures.ascii
import dev.ikram.sandbox.core.filetype.Fixtures.bytes
import dev.ikram.sandbox.core.model.DetectedType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDetectionTest {
    private fun sniff(bytes: ByteArray) = MagicSniffer.sniff(bytes)

    @Test fun asciiText() = assertEquals(DetectedType.TEXT, sniff(ascii("Hello,\r\nworld.\tTabs are fine.\n")))

    @Test fun utf8Text() = assertEquals(DetectedType.TEXT, sniff("Café, naïve, 日本語, emoji 🙂\n".toByteArray()))

    @Test fun utf8WithBom() = assertEquals(DetectedType.TEXT, sniff(bytes(0xEF, 0xBB, 0xBF) + ascii("hi")))

    @Test fun windows1252Text() = assertEquals(DetectedType.TEXT, sniff(ascii("Café crème \u0093quoted\u0094\n")))

    @Test fun utf16LittleEndianWithBomIsTextNotMp3() =
        assertEquals(DetectedType.TEXT, sniff(bytes(0xFF, 0xFE) + "Hello".toByteArray(Charsets.UTF_16LE)))

    @Test fun utf16BigEndianWithBom() =
        assertEquals(DetectedType.TEXT, sniff(bytes(0xFE, 0xFF) + "Hello".toByteArray(Charsets.UTF_16BE)))

    @Test fun html() = assertEquals(DetectedType.TEXT, sniff(ascii("<!doctype html><html><body>Hi</body></html>")))

    @Test fun svgIsTextNeverAnImage() =
        assertEquals(DetectedType.TEXT, sniff(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>")))

    @Test fun json() = assertEquals(DetectedType.TEXT, sniff(ascii("{\"a\": [1, 2, 3]}")))

    @Test fun ansiColouredLog() = assertEquals(DetectedType.TEXT, sniff(ascii("\u001B[31mERROR\u001B[0m failed\n")))

    @Test fun nulByteMeansBinary() =
        assertEquals(DetectedType.UNKNOWN, sniff(ascii("looks like text") + bytes(0) + ascii("x")))

    @Test fun randomBytesAreUnknown() {
        val random = Random(42)
        val noise = ByteArray(4096) { (random.nextInt(255) + 1).toByte() }
        assertEquals(DetectedType.UNKNOWN, sniff(noise))
    }

    @Test fun manyControlCharactersMeanBinary() {
        val text = ascii("a".repeat(100)) + ByteArray(5) { 0x01 }
        assertEquals(DetectedType.UNKNOWN, sniff(text))
    }

    @Test fun onlyTheHeadIsSampled() {
        // A large text file with binary junk far past the head is still judged on its start.
        val text = ascii("line\n".repeat(4000)) + bytes(0, 0, 0)
        assertEquals(DetectedType.TEXT, sniff(text))
    }
}
