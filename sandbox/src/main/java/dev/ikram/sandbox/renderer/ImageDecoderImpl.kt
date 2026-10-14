package dev.ikram.sandbox.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileDescriptor

/** Decodes inside the isolated process only. Never call this from the app process. */
internal object ImageDecoderImpl {
    fun decode(fd: FileDescriptor, maxWidth: Int, maxHeight: Int): Decoded? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(fd, null, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        // A small file can claim enormous dimensions; refuse before allocating anything.
        if (width.toLong() * height > RendererLimits.MAX_SOURCE_PIXELS) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = RendererLimits.sampleSize(width, height, maxWidth, maxHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val sampled = BitmapFactory.decodeFileDescriptor(fd, null, options) ?: return null
        return Decoded(fitWithin(sampled, maxWidth, maxHeight), width, height)
    }

    /** Sampling works in powers of two; finish with an exact scale if still too big. */
    private fun fitWithin(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight && bitmap.config == Bitmap.Config.ARGB_8888) {
            return bitmap
        }
        val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height, 1f)
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val argb = if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false)
        if (argb !== bitmap) bitmap.recycle()
        return argb
    }

    class Decoded(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)
}
