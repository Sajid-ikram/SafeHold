package dev.ikram.sandbox.renderer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** The renderer crashed, hung or misbehaved on a file. The file is treated as unsafe. */
class RendererCrashedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A rendered PDF page and the page's size in points (1/72 inch). */
class RenderedPage(val bitmap: Bitmap, val widthPoints: Int, val heightPoints: Int)

/**
 * The app's handle on the isolated renderer. Binds on demand, runs one call at a time,
 * times out every call, and restarts the renderer after any crash. A crash or timeout is
 * reported as [RendererCrashedException]: the app never falls back to decoding a file
 * itself.
 */
class SandboxRenderer(private val context: Context) {
    private companion object {
        /** More pages than any real document; a renderer claiming this many is misbehaving. */
        const val MAX_PDF_PAGES = 100_000

        /** 200 inches, the largest page the PDF format allows. */
        const val MAX_PAGE_POINTS = 14_400
    }

    private val mutex = Mutex()
    private val executor = Executors.newCachedThreadPool()

    // Written from service callbacks on the main thread as well as from calls.
    @Volatile private var bound: Bound? = null

    private class Bound(val renderer: IRenderer, val connection: ServiceConnection)

    /**
     * Decodes [file], read only, into a bitmap no larger than [maxWidth] x [maxHeight].
     * Null when it is not a decodable image.
     */
    suspend fun decodeImage(file: File, maxWidth: Int, maxHeight: Int): Bitmap? {
        val result = call { renderer ->
            open(file).use { fd -> renderer.decodeImage(fd, maxWidth, maxHeight) }
        } ?: return null
        return SharedMemoryBitmap.toBitmap(result, maxWidth, maxHeight)
            ?: throw RendererCrashedException("The renderer returned malformed pixels")
    }

    /** Number of pages, or null when [file] is not a PDF the platform can open (damaged, encrypted). */
    suspend fun pdfPageCount(file: File): Int? {
        val count = call { renderer -> open(file).use { fd -> renderer.pdfPageCount(fd) } }
        if (count > MAX_PDF_PAGES) throw RendererCrashedException("The renderer reported $count pages")
        return count.takeIf { it >= 0 }
    }

    /** One page, [width] pixels wide (narrower if the page is very tall). Null if it cannot be drawn. */
    suspend fun renderPdfPage(file: File, pageIndex: Int, width: Int): Bitmap? =
        renderPdfPageSized(file, pageIndex, width)?.bitmap

    /** As [renderPdfPage], with the page's own size in points, for rebuilding the document. */
    suspend fun renderPdfPageSized(file: File, pageIndex: Int, width: Int): RenderedPage? {
        require(pageIndex >= 0 && width in 1..RendererLimits.MAX_OUTPUT_SIDE) { "Page or width out of range" }
        val result = call { renderer -> open(file).use { fd -> renderer.renderPdfPage(fd, pageIndex, width) } }
            ?: return null
        val pageWidth = result.sourceWidth
        val pageHeight = result.sourceHeight
        val bitmap = SharedMemoryBitmap.toBitmap(result, width, RendererLimits.MAX_OUTPUT_SIDE)
        if (bitmap == null || pageWidth !in 1..MAX_PAGE_POINTS || pageHeight !in 1..MAX_PAGE_POINTS) {
            bitmap?.recycle()
            throw RendererCrashedException("The renderer returned a malformed page")
        }
        return RenderedPage(bitmap, pageWidth, pageHeight)
    }

    /** Vault files are only ever handed over read only. */
    private fun open(file: File): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    /**
     * A renderer that died before or during a call gets one fresh retry: it may have died
     * of something else, and a file is only judged unsafe if it kills two renderers.
     * A timeout is never retried.
     */
    private suspend fun <T> call(block: (IRenderer) -> T): T = mutex.withLock {
        try {
            attempt(block)
        } catch (dead: RemoteException) {
            reset()
            try {
                attempt(block)
            } catch (deadAgain: RemoteException) {
                reset()
                deadAgain.addSuppressed(dead)
                throw RendererCrashedException("The renderer died twice", deadAgain)
            }
        }
    }

    private suspend fun <T> attempt(block: (IRenderer) -> T): T {
        val renderer = bind()
        return try {
            withTimeout(RendererLimits.CALL_TIMEOUT_MILLIS) { runOffThread { block(renderer) } }
        } catch (timeout: TimeoutCancellationException) {
            reset()
            throw RendererCrashedException("The renderer timed out", timeout)
        }
    }

    /** Binder calls block; run them elsewhere so a hung renderer can be abandoned. */
    private suspend fun <T> runOffThread(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        executor.execute {
            val result = runCatching(block)
            if (continuation.isActive) continuation.resumeWith(result)
        }
    }

    private suspend fun bind(): IRenderer {
        bound?.let { return it.renderer }
        val ready = CompletableDeferred<IRenderer>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                ready.complete(IRenderer.Stub.asInterface(service))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                bound = null
            }

            override fun onBindingDied(name: ComponentName) {
                reset()
            }
        }
        val intent = Intent(context, RendererService::class.java)
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            context.unbindService(connection)
            throw RendererCrashedException("The renderer could not be started")
        }
        val renderer = try {
            withTimeout(RendererLimits.CALL_TIMEOUT_MILLIS) { ready.await() }
        } catch (timeout: TimeoutCancellationException) {
            context.unbindService(connection)
            throw RendererCrashedException("The renderer did not start", timeout)
        }
        bound = Bound(renderer, connection)
        return renderer
    }

    /** Unbinding the last client makes the system kill the isolated process, stuck or not. */
    private fun reset() {
        val current = bound ?: return
        bound = null
        runCatching { context.unbindService(current.connection) }
    }
}
