package dev.ikram.sandbox.renderer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor

/**
 * The isolated renderer (manifest: isolatedProcess, process ":renderer", not exported).
 * Validates every input; anything malformed is refused rather than parsed.
 */
class RendererService : Service() {
    private val binder = object : IRenderer.Stub() {
        override fun decodeImage(fd: ParcelFileDescriptor?, maxWidth: Int, maxHeight: Int): ImageResult? {
            requireNotNull(fd) { "No file" }
            fd.use { descriptor ->
                require(isValidSide(maxWidth) && isValidSide(maxHeight)) { "Output size out of range" }
                val decoded = ImageDecoderImpl.decode(descriptor.fileDescriptor, maxWidth, maxHeight) ?: return null
                return SharedMemoryBitmap.toResult(decoded)
            }
        }

        override fun pdfPageCount(fd: ParcelFileDescriptor?): Int {
            requireNotNull(fd) { "No file" }
            return PdfRendererImpl.pageCount(fd)
        }

        override fun renderPdfPage(fd: ParcelFileDescriptor?, pageIndex: Int, width: Int): ImageResult? {
            requireNotNull(fd) { "No file" }
            if (pageIndex < 0 || !isValidSide(width)) {
                fd.close()
                throw IllegalArgumentException("Page or width out of range")
            }
            val rendered = PdfRendererImpl.render(fd, pageIndex, width) ?: return null
            return SharedMemoryBitmap.toResult(rendered)
        }
    }

    private fun isValidSide(side: Int) = side in 1..RendererLimits.MAX_OUTPUT_SIDE

    override fun onBind(intent: Intent): IBinder = binder
}
