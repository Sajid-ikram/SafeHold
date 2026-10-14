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
                require(
                    maxWidth in 1..RendererLimits.MAX_OUTPUT_SIDE && maxHeight in 1..RendererLimits.MAX_OUTPUT_SIDE,
                ) {
                    "Output size out of range"
                }
                val decoded = ImageDecoderImpl.decode(descriptor.fileDescriptor, maxWidth, maxHeight) ?: return null
                return SharedMemoryBitmap.toResult(decoded)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder = binder
}
