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

/**
 * The app's handle on the isolated renderer. Binds on demand, runs one call at a time,
 * times out every call, and restarts the renderer after any crash. A crash or timeout is
 * reported as [RendererCrashedException]: the app never falls back to decoding a file
 * itself.
 */
class SandboxRenderer(private val context: Context) {
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
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                renderer.decodeImage(fd, maxWidth, maxHeight)
            }
        } ?: return null
        return SharedMemoryBitmap.toBitmap(result, maxWidth, maxHeight)
            ?: throw RendererCrashedException("The renderer returned malformed pixels")
    }

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
