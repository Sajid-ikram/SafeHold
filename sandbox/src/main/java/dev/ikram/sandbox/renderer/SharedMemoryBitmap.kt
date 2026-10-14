package dev.ikram.sandbox.renderer

import android.graphics.Bitmap
import android.os.SharedMemory
import android.system.OsConstants

/**
 * Moves pixels between processes through SharedMemory: binder transactions are capped
 * near 1 MB, and Bitmap objects must never cross the process boundary.
 */
object SharedMemoryBitmap {
    /** Renderer side: copies the pixels out and makes the memory read-only before it is sent. */
    internal fun toResult(decoded: ImageDecoderImpl.Decoded): ImageResult {
        val bitmap = decoded.bitmap
        val size = bitmap.width * bitmap.height * RendererLimits.BYTES_PER_PIXEL
        val memory = SharedMemory.create("image", size)
        val buffer = memory.mapReadWrite()
        try {
            bitmap.copyPixelsToBuffer(buffer)
        } finally {
            SharedMemory.unmap(buffer)
        }
        memory.setProtect(OsConstants.PROT_READ)
        val result = ImageResult(memory, bitmap.width, bitmap.height, decoded.sourceWidth, decoded.sourceHeight)
        bitmap.recycle()
        return result
    }

    /**
     * App side: checks the renderer's answer against what was asked for, then builds a
     * bitmap from it. Returns null for anything that doesn't add up.
     */
    fun toBitmap(result: ImageResult, maxWidth: Int, maxHeight: Int): Bitmap? = result.pixels.use { memory ->
        if (!RendererLimits.isValidResult(result.width, result.height, memory.size, maxWidth, maxHeight)) {
            return@use null
        }
        val buffer = memory.mapReadOnly()
        try {
            Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888).apply {
                copyPixelsFromBuffer(buffer)
            }
        } finally {
            SharedMemory.unmap(buffer)
        }
    }
}
