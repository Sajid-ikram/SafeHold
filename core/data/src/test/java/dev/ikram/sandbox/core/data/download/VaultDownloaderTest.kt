package dev.ikram.sandbox.core.data.download

import dev.ikram.sandbox.core.data.FakeVaultFileDao
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.DownloadFailure
import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultDownloaderTest {
    @get:Rule val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val dao = FakeVaultFileDao()
    private val counter = NewFilesCounter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var storage: VaultStorage

    private val pdf = "%PDF-1.7\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n".toByteArray()

    @Before
    fun setUp() {
        server.start()
        storage = VaultStorage(folder.newFolder("files"))
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun downloader(limits: DownloadLimits = DownloadLimits(10_000_000, 0, requireHttps = false)) =
        VaultDownloader(OkHttpClient(), storage, dao, limits, counter, scope, Dispatchers.IO)

    private fun request(path: String, disposition: String? = null, referer: String? = null) = DownloadRequest(
        url = server.url(path).toString(),
        userAgent = "SafeHoldTest/1.0",
        contentDisposition = disposition,
        mimeType = null,
        contentLength = -1,
        referer = referer,
    )

    private fun respond(body: ByteArray, contentType: String = "application/octet-stream", code: Int = 200) =
        server.enqueue(
            MockResponse.Builder().code(code).setHeader("Content-Type", contentType).body(Buffer().write(body)).build(),
        )

    private fun download(
        request: DownloadRequest,
        name: String = "file",
        limits: DownloadLimits? = null,
    ): DownloadState = runBlocking {
        val id = UUID.randomUUID().toString()
        (limits?.let(::downloader) ?: downloader()).download(id, name, request)
    }

    private fun partials(): List<File> = File(storage.vaultDir, ".partial").listFiles().orEmpty().toList()

    @Test
    fun savesAFileIntoTheVault() {
        respond(pdf, "application/pdf")
        val state = download(request("/guides/north-coast-map.pdf"), name = "north-coast-map.pdf")

        state as DownloadState.Completed
        assertEquals(FileCategory.PDF, state.category)
        val stored = storage.fileFor(state.fileId)
        assertTrue(stored.exists())
        assertTrue(pdf.contentEquals(stored.readBytes()))

        val row = dao.rows.value.single()
        assertEquals(state.fileId, row.id)
        assertEquals("north-coast-map.pdf", row.displayName)
        assertEquals(DetectedType.PDF.name, row.detectedType)
        assertEquals(pdf.size.toLong(), row.sizeBytes)
        assertEquals(sha256(pdf), row.sha256)
        assertEquals(server.url("/guides/north-coast-map.pdf").toString(), row.sourceUrl)
        assertFalse(row.isMismatch)
        assertEquals(1, counter.count.value)
        assertTrue(partials().isEmpty())
    }

    @Test
    fun vaultFilesHaveNoExtensionFromTheServer() {
        respond(pdf, "application/pdf")
        val state = download(request("/evil.exe"), name = "evil.exe") as DownloadState.Completed
        assertEquals("${state.fileId}.bin", storage.fileFor(state.fileId).name)
    }

    @Test
    fun anAppNamedLikeAVideoIsStoredBlockedAndFlagged() {
        respond(apk(), "video/mp4")
        val state = download(request("/movie.mp4"), name = "movie.mp4") as DownloadState.Completed
        assertEquals(FileCategory.BLOCKED, state.category)
        val row = dao.rows.value.single()
        assertEquals(DetectedType.APK.name, row.detectedType)
        assertEquals(DetectedType.MP4.name, row.claimedType)
        assertTrue(row.isMismatch)
    }

    @Test
    fun sendsTheBrowsersUserAgentAndSecureReferer() {
        respond(pdf)
        download(request("/a.pdf", referer = "https://page.example/article"))
        val recorded = server.takeRequest()
        assertEquals("SafeHoldTest/1.0", recorded.headers["User-Agent"])
        assertEquals("https://page.example/article", recorded.headers["Referer"])
    }

    @Test
    fun plainHttpRefererIsNotSent() {
        respond(pdf)
        download(request("/a.pdf", referer = "http://page.example/article"))
        assertNull(server.takeRequest().headers["Referer"])
    }

    @Test
    fun httpErrorsSaveNothing() {
        respond("Not found".toByteArray(), "text/html", code = 404)
        val state = download(request("/missing.pdf"))
        assertEquals(DownloadFailure.HTTP_ERROR, (state as DownloadState.Failed).reason)
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(storage.storedIds().isEmpty())
        assertTrue(partials().isEmpty())
    }

    @Test
    fun declaredSizeOverTheLimitIsRefusedBeforeDownloading() {
        respond(ByteArray(5_000))
        val state = download(request("/big.bin"), limits = DownloadLimits(1_000, 0, requireHttps = false))
        assertEquals(DownloadFailure.TOO_LARGE, (state as DownloadState.Failed).reason)
        assertTrue(storage.storedIds().isEmpty())
    }

    @Test
    fun undeclaredSizeOverTheLimitIsCutOff() {
        server.enqueue(
            MockResponse.Builder().chunkedBody(Buffer().write(ByteArray(5_000)), maxChunkSize = 512).build(),
        )
        val state = download(request("/stream.bin"), limits = DownloadLimits(1_000, 0, requireHttps = false))
        assertEquals(DownloadFailure.TOO_LARGE, (state as DownloadState.Failed).reason)
        assertTrue(partials().isEmpty())
    }

    @Test
    fun plainHttpIsRefusedWithoutConnecting() {
        val state = download(request("/a.pdf"), limits = DownloadLimits())
        assertEquals(DownloadFailure.INSECURE_CONNECTION, (state as DownloadState.Failed).reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unreachableServerIsANetworkFailure() {
        val dead = request("/a.pdf")
        server.close()
        assertEquals(DownloadFailure.NETWORK, (download(dead) as DownloadState.Failed).reason)
    }

    @Test
    fun enqueuedDownloadsReportProgressAndFinish() = runBlocking {
        respond(pdf, "application/pdf")
        val queue = downloader()
        val id = queue.enqueue(request("/a.pdf"))
        val done = withTimeout(10_000) {
            queue.downloads.first { list -> list.any { it.downloadId == id && it !is DownloadState.Running } }
        }.single { it.downloadId == id }
        assertTrue(done is DownloadState.Completed)
        queue.dismiss(id)
        assertTrue(queue.downloads.first { it.isEmpty() }.isEmpty())
    }

    @Test
    fun cancellingStopsTheDownloadAndKeepsNothing() = runBlocking {
        server.enqueue(
            MockResponse.Builder()
                .body(Buffer().write(ByteArray(200_000)))
                .throttleBody(1_024, 50, TimeUnit.MILLISECONDS)
                .build(),
        )
        val queue = downloader()
        val id = queue.enqueue(request("/slow.bin"))
        withTimeout(10_000) {
            queue.downloads.first { list -> list.any { it is DownloadState.Running && it.bytesDownloaded > 0 } }
        }
        queue.cancel(id)
        val state = withTimeout(10_000) {
            queue.downloads.first { list -> list.any { it.downloadId == id && it is DownloadState.Cancelled } }
        }
        assertTrue(state.single() is DownloadState.Cancelled)
        withTimeout(10_000) { while (partials().isNotEmpty()) Thread.sleep(20) }
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(storage.storedIds().isEmpty())
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun apk(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            listOf("AndroidManifest.xml", "classes.dex").forEach {
                zip.putNextEntry(ZipEntry(it))
                zip.write(it.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
