package dev.ikram.sandbox.feature.share.copy

import java.io.Closeable
import java.io.OutputStream
import java.util.Locale

/**
 * Writes a PDF whose every page is a single JPEG picture, one page at a time, so memory
 * stays flat however long the document. The file holds pages and pictures and nothing
 * else: no text, links, scripts, forms, attachments or document information.
 *
 * Object numbers are fixed up front: 1 is the catalog, 2 the page tree, and page `i`
 * uses `3i + 3` (page), `3i + 4` (picture) and `3i + 5` (drawing instructions).
 */
class ImagePdfWriter(output: OutputStream, private val pageCount: Int) : Closeable {
    private val out = CountingStream(output)
    private val offsets = LongArray(pageCount * OBJECTS_PER_PAGE + FIRST_PAGE_OBJECT)
    private var pagesWritten = 0

    init {
        require(pageCount > 0) { "A PDF needs a page" }
        out.write(HEADER)
        writeObject(CATALOG, "<< /Type /Catalog /Pages $PAGE_TREE 0 R >>")
        val kids = (0 until pageCount).joinToString(" ") { "${pageObject(it)} 0 R" }
        writeObject(PAGE_TREE, "<< /Type /Pages /Kids [$kids] /Count $pageCount >>")
    }

    /**
     * Adds the next page: [jpeg] holds a [pixelWidth] x [pixelHeight] picture, stretched
     * over a [pointWidth] x [pointHeight] page (points are 1/72 inch).
     */
    fun addPage(jpeg: ByteArray, pixelWidth: Int, pixelHeight: Int, pointWidth: Int, pointHeight: Int) {
        check(pagesWritten < pageCount) { "All $pageCount pages are written" }
        require(pixelWidth > 0 && pixelHeight > 0 && pointWidth > 0 && pointHeight > 0) { "Empty page" }
        val page = pageObject(pagesWritten)
        writeObject(
            page,
            "<< /Type /Page /Parent $PAGE_TREE 0 R /MediaBox [0 0 $pointWidth $pointHeight] " +
                "/Resources << /XObject << /Page ${page + 1} 0 R >> >> /Contents ${page + 2} 0 R >>",
        )
        writeStream(
            page + 1,
            "<< /Type /XObject /Subtype /Image /Width $pixelWidth /Height $pixelHeight " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>",
            jpeg,
        )
        val draw = "q $pointWidth 0 0 $pointHeight 0 0 cm /Page Do Q".toByteArray(Charsets.US_ASCII)
        writeStream(page + 2, "<< /Length ${draw.size} >>", draw)
        pagesWritten++
    }

    /** Writes the cross-reference table and trailer. Every page must have been added. */
    override fun close() {
        check(pagesWritten == pageCount) { "Only $pagesWritten of $pageCount pages were written" }
        val table = out.count
        val entries = StringBuilder("xref\n0 ${offsets.size}\n0000000000 65535 f \n")
        // Each entry is exactly 20 bytes, as the format requires.
        for (number in 1 until offsets.size) {
            entries.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[number]))
        }
        entries.append("trailer\n<< /Size ${offsets.size} /Root $CATALOG 0 R >>\nstartxref\n$table\n%%EOF\n")
        out.write(entries.toString().toByteArray(Charsets.US_ASCII))
        out.flush()
    }

    private fun writeObject(number: Int, body: String) {
        offsets[number] = out.count
        out.write("$number 0 obj\n$body\nendobj\n".toByteArray(Charsets.US_ASCII))
    }

    private fun writeStream(number: Int, dictionary: String, data: ByteArray) {
        offsets[number] = out.count
        out.write("$number 0 obj\n$dictionary\nstream\n".toByteArray(Charsets.US_ASCII))
        out.write(data)
        out.write("\nendstream\nendobj\n".toByteArray(Charsets.US_ASCII))
    }

    private fun pageObject(index: Int) = FIRST_PAGE_OBJECT + index * OBJECTS_PER_PAGE

    private class CountingStream(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }

        override fun flush() = target.flush()
    }

    private companion object {
        const val CATALOG = 1
        const val PAGE_TREE = 2
        const val FIRST_PAGE_OBJECT = 3
        const val OBJECTS_PER_PAGE = 3

        /** The version line, then a comment of high bytes that marks the file as binary. */
        val HEADER = "%PDF-1.4\n%".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte())
    }
}
