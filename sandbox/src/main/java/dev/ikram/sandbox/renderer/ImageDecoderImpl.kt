package dev.ikram.sandbox.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor

/** Decodes inside the isolated process only. Never call this from the app process. */
internal object ImageDecoderImpl {
    fun decode(fd: FileDescriptor, maxWidth: Int, maxHeight: Int): Decoded? {
        val orientation = orientationOf(fd)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        rewind(fd)
        BitmapFactory.decodeFileDescriptor(fd, null, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        // A small file can claim enormous dimensions; refuse before allocating anything.
        if (width.toLong() * height > RendererLimits.MAX_SOURCE_PIXELS) return null

        // Turned a quarter, the stored picture must fit the requested box the other way round.
        val (boxWidth, boxHeight) = if (orientation.swapsSides) maxHeight to maxWidth else maxWidth to maxHeight
        val options = BitmapFactory.Options().apply {
            inSampleSize = RendererLimits.sampleSize(width, height, boxWidth, boxHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        rewind(fd)
        val sampled = BitmapFactory.decodeFileDescriptor(fd, null, options) ?: return null
        val upright = orientation.apply(fitWithin(sampled, boxWidth, boxHeight))
        return if (orientation.swapsSides) Decoded(upright, height, width) else Decoded(upright, width, height)
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

    /**
     * Cameras store pictures sideways and say so in EXIF; BitmapFactory ignores that.
     * Anything unreadable counts as upright.
     */
    private fun orientationOf(fd: FileDescriptor): Orientation = try {
        rewind(fd)
        Orientation.fromExif(
            ExifInterface(fd).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
        )
    } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") unreadable: Exception) {
        Orientation.NORMAL
    }

    private fun rewind(fd: FileDescriptor) {
        Os.lseek(fd, 0, OsConstants.SEEK_SET)
    }

    class Decoded(val bitmap: Bitmap, val sourceWidth: Int, val sourceHeight: Int)

    /** The eight EXIF orientations, as the turn and mirror that make the picture upright. */
    internal enum class Orientation(val degrees: Float, val mirror: Boolean) {
        NORMAL(0f, false),
        MIRRORED(0f, true),
        ROTATED_180(180f, false),
        MIRRORED_180(180f, true),
        TRANSPOSED(90f, true),
        ROTATED_90(90f, false),
        TRANSVERSED(-90f, true),
        ROTATED_270(-90f, false),
        ;

        val swapsSides: Boolean get() = degrees == 90f || degrees == -90f

        fun apply(bitmap: Bitmap): Bitmap {
            if (this == NORMAL) return bitmap
            val matrix = Matrix().apply {
                setRotate(degrees)
                if (mirror) postScale(-1f, 1f)
            }
            val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (turned !== bitmap) bitmap.recycle()
            return turned
        }

        companion object {
            /** EXIF values 1 to 8, in order; anything else is upright. */
            fun fromExif(value: Int): Orientation = entries.getOrElse(value - 1) { NORMAL }
        }
    }
}
