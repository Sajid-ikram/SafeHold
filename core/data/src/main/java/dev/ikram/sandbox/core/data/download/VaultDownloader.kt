package dev.ikram.sandbox.core.data.download

import dev.ikram.sandbox.core.data.db.VaultFileDao
import dev.ikram.sandbox.core.data.db.toEntity
import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.filetype.DownloadFileName
import dev.ikram.sandbox.core.filetype.FileByteSource
import dev.ikram.sandbox.core.filetype.FileClassifier
import dev.ikram.sandbox.core.model.DownloadFailure
import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.ScanStatus
import dev.ikram.sandbox.core.model.VaultFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.UnknownServiceException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync

/**
 * The only way a file gets into the vault (CLAUDE.md, download flow): stream into a
 * private partial file while hashing, sniff the bytes, move it into the vault
 * atomically, then record it. Nothing is ever written anywhere else.
 */
@Singleton
class VaultDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val storage: VaultStorage,
    private val dao: VaultFileDao,
    private val limits: DownloadLimits,
    private val newFiles: NewFilesCounter,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DownloadQueue {
    private val states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    private val savedIds = MutableSharedFlow<String>(extraBufferCapacity = SAVED_BUFFER)

    /** The id of each file as it lands in the vault. */
    val saved: SharedFlow<String> = savedIds.asSharedFlow()
    private val jobs = ConcurrentHashMap<String, Job>()

    override val downloads: StateFlow<List<DownloadState>> = states
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun enqueue(request: DownloadRequest): String {
        val id = UUID.randomUUID().toString()
        val name = DownloadFileName.resolve(request.url, request.contentDisposition, request.mimeType)
        publish(DownloadState.Running(id, name, 0, request.contentLength.takeIf { it > 0 }))
        jobs[id] = scope.launch {
            val outcome = try {
                download(id, name, request)
            } catch (cancelled: CancellationException) {
                publish(DownloadState.Cancelled(id, name))
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") error: Exception) {
                // Whatever went wrong, the download must not stay "running" forever. The
                // reason is not shown: it can carry the URL, which stays out of logs.
                DownloadState.Failed(id, name, DownloadFailure.UNKNOWN)
            }
            publish(outcome)
            jobs.remove(id)
        }
        return id
    }

    override fun cancel(downloadId: String) {
        jobs.remove(downloadId)?.cancel()
    }

    override fun dismiss(downloadId: String) {
        if (states.value[downloadId] is DownloadState.Running) return
        states.update { it - downloadId }
    }

    /** Runs one download to the end. Partial files never outlive this call. */
    suspend fun download(id: String, name: String, request: DownloadRequest): DownloadState = withContext(io) {
        val partial = storage.newPartial(id)
        try {
            fetch(id, name, request, partial)
        } catch (error: IOException) {
            DownloadState.Failed(id, name, failureOf(error))
        } finally {
            partial.delete()
        }
    }

    private suspend fun fetch(id: String, name: String, request: DownloadRequest, partial: File): DownloadState {
        if (limits.requireHttps && !request.url.startsWith("https://", ignoreCase = true)) {
            return DownloadState.Failed(id, name, DownloadFailure.INSECURE_CONNECTION)
        }
        client.newCall(httpRequest(request)).executeAsync().use { response ->
            val declared = response.body.contentLength().takeIf { it >= 0 }
            refusal(response, declared)?.let { return DownloadState.Failed(id, name, it) }
            val written = response.body.byteStream().use { stream(it, partial, id, name, declared) }
                ?: return DownloadState.Failed(id, name, DownloadFailure.TOO_LARGE)
            val mimeType = response.body.contentType()?.toString() ?: request.mimeType
            return save(id, name, partial, written, mimeType, response.request.url)
        }
    }

    /** Checks what the bytes really are, moves them into the vault, then records them. */
    private suspend fun save(
        id: String,
        name: String,
        partial: File,
        written: Written,
        mimeType: String?,
        finalUrl: HttpUrl,
    ): DownloadState {
        val classification = FileByteSource.open(partial).use { FileClassifier.classify(it, name, mimeType) }
        val file = VaultFile(
            id = id,
            displayName = name,
            sizeBytes = written.size,
            detectedType = classification.detectedType,
            claimedType = classification.claimedType,
            category = classification.category,
            sha256 = written.sha256,
            sourceUrl = finalUrl.toString(),
            sourceHost = finalUrl.host.removePrefix("www."),
            createdAt = Instant.now(),
            scanStatus = ScanStatus.NotChecked,
            isMismatch = classification.isMismatch,
            mismatchAcknowledged = false,
            openFailed = false,
        )
        storage.commit(partial, id)
        try {
            dao.insert(file.toEntity())
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            storage.delete(id)
            throw IOException("Could not record the file", error)
        }
        newFiles.increment()
        savedIds.tryEmit(id)
        return DownloadState.Completed(id, name, id, classification.category)
    }

    private fun httpRequest(request: DownloadRequest): Request = Request.Builder()
        .url(request.url)
        .apply {
            request.userAgent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
            request.referer?.takeIf { it.startsWith("https://") }?.let { header("Referer", it) }
        }
        .get()
        .build()

    /**
     * Why a response is not saved, or null if it may be: errors, redirects to plain http,
     * and declared sizes over the limit or beyond the free space.
     */
    private fun refusal(response: Response, declaredBytes: Long?): DownloadFailure? = when {
        !response.isSuccessful -> DownloadFailure.HTTP_ERROR
        limits.requireHttps && !response.request.url.isHttps -> DownloadFailure.INSECURE_CONNECTION
        declaredBytes == null -> null
        declaredBytes > limits.maxBytes -> DownloadFailure.TOO_LARGE
        storage.usableSpace() < declaredBytes + limits.reserveBytes -> DownloadFailure.STORAGE_FULL
        else -> null
    }

    private class Written(val size: Long, val sha256: String)

    /** Copies [input] to [target], hashing as it goes. Null if it grew past the size limit. */
    private suspend fun stream(input: InputStream, target: File, id: String, name: String, total: Long?): Written? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var size = 0L
        var lastReport = 0L
        FileOutputStream(target).use { output ->
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                size += read
                if (size > limits.maxBytes) return null
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
                val now = System.nanoTime()
                if (now - lastReport > REPORT_INTERVAL_NANOS) {
                    lastReport = now
                    publish(DownloadState.Running(id, name, size, total))
                }
            }
            output.fd.sync()
        }
        return Written(size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun failureOf(error: IOException): DownloadFailure = when {
        error is UnknownServiceException || error is SSLException -> DownloadFailure.INSECURE_CONNECTION
        error is SocketTimeoutException -> DownloadFailure.TIMEOUT
        error.message?.contains("ENOSPC") == true -> DownloadFailure.STORAGE_FULL
        else -> DownloadFailure.NETWORK
    }

    private fun publish(state: DownloadState) {
        states.update { it + (state.downloadId to state) }
    }

    private companion object {
        /** Saved ids wait here if nobody is listening at that moment. */
        const val SAVED_BUFFER = 64

        const val BUFFER_SIZE = 64 * 1024
        const val REPORT_INTERVAL_NANOS = 150_000_000L
    }
}
