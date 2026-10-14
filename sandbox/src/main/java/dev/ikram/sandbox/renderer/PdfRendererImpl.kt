package dev.ikram.sandbox.renderer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.IOException
import kotlin.math.roundToInt

/** PDFium, through the platform PdfRenderer. Isolated process only. */
internal object PdfRendererImpl {
    /** PdfRenderer takes ownership of [fd] and closes it. */
    fun pageCount(fd: ParcelFileDescriptor): Int = open(fd)?.use { it.pageCount } ?: -1

    fun render(fd: ParcelFileDescriptor, pageIndex: Int, width: Int): ImageDecoderImpl.Decoded? {
        val document = open(fd) ?: return null
        document.use {
            if (pageIndex !in 0 until document.pageCount) return null
            document.openPage(pageIndex).use { page ->
                if (page.width <= 0 || page.height <= 0) return null
                var scale = width.toFloat() / page.width
                // A very tall page renders narrower rather than past the size limit.
                if (page.height * scale > RendererLimits.MAX_OUTPUT_SIDE) {
                    scale = RendererLimits.MAX_OUTPUT_SIDE.toFloat() / page.height
                }
                val outWidth = (page.width * scale).roundToInt().coerceIn(1, RendererLimits.MAX_OUTPUT_SIDE)
                val outHeight = (page.height * scale).roundToInt().coerceIn(1, RendererLimits.MAX_OUTPUT_SIDE)
                val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                // Pages are transparent where nothing is drawn; paper is white.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return ImageDecoderImpl.Decoded(bitmap, page.width, page.height)
            }
        }
    }

    /** Encrypted, damaged and non-PDF files all come back as null. */
    private fun open(fd: ParcelFileDescriptor): PdfRenderer? = try {
        PdfRenderer(fd)
    } catch (@Suppress("SwallowedException") unreadable: IOException) {
        fd.close()
        null
    } catch (@Suppress("SwallowedException") encrypted: SecurityException) {
        fd.close()
        null
    }
}
