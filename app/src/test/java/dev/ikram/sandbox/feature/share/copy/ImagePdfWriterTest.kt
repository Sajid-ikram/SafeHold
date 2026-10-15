package dev.ikram.sandbox.feature.share.copy

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagePdfWriterTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    private fun write(pages: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImagePdfWriter(out, pages).use { writer ->
            repeat(pages) {
                writer.addPage(jpeg, pixelWidth = 1240, pixelHeight = 1754, pointWidth = 595, pointHeight = 842)
            }
        }
        return out.toByteArray()
    }

    private fun ByteArray.text() = String(this, Charsets.ISO_8859_1)

    @Test
    fun startsAndEndsLikeAPdf() {
        val text = write(1).text()
        assertTrue(text.startsWith("%PDF-1.4\n%"))
        assertTrue(text.endsWith("%%EOF\n"))
    }

    @Test
    fun everyCrossReferencePointsAtItsObject() {
        val bytes = write(3)
        val text = bytes.text()
        val table = text.substringAfterLast("startxref\n").substringBefore('\n').toInt()
        assertTrue(text.startsWith("xref\n", table))
        val entries = text.substring(table).lines().drop(2).takeWhile { it.endsWith(" n ") || it.endsWith(" f ") }
        assertEquals(3 * 3 + 3, entries.size)
        entries.drop(1).forEachIndexed { index, entry ->
            assertEquals(20, entry.length + 1)
            val offset = entry.substring(0, 10).toInt()
            assertTrue("object ${index + 1}", text.startsWith("${index + 1} 0 obj\n", offset))
        }
    }

    @Test
    fun pagesHoldThePictureAtThePageSize() {
        val text = write(2).text()
        assertTrue(text.contains("/Type /Pages /Kids [3 0 R 6 0 R] /Count 2"))
        assertTrue(text.contains("/MediaBox [0 0 595 842]"))
        assertTrue(text.contains("/Width 1240 /Height 1754"))
        assertTrue(text.contains("/Filter /DCTDecode /Length ${jpeg.size}"))
        assertTrue(text.contains("q 595 0 0 842 0 0 cm /Page Do Q"))
    }

    @Test
    fun nothingButPagesAndPictures() {
        val text = write(1).text()
        listOf("/JavaScript", "/JS", "/OpenAction", "/AA", "/URI", "/Launch", "/EmbeddedFile", "/Info", "/AcroForm")
            .forEach { assertFalse(it, text.contains(it)) }
    }

    @Test(expected = IllegalStateException::class)
    fun aMissingPageIsAnError() {
        ImagePdfWriter(ByteArrayOutputStream(), 2).use { it.addPage(jpeg, 1, 1, 1, 1) }
    }

    @Test(expected = IllegalStateException::class)
    fun anExtraPageIsAnError() {
        ImagePdfWriter(ByteArrayOutputStream(), 1).use { writer -> repeat(2) { writer.addPage(jpeg, 1, 1, 1, 1) } }
    }
}
