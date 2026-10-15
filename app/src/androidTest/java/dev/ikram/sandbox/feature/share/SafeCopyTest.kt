package dev.ikram.sandbox.feature.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ikram.sandbox.core.data.db.SandboxDatabase
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.storage.TempShareCleaner
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.filetype.MagicSniffer
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.ScanStatus
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.copy.CopyProgress
import dev.ikram.sandbox.feature.share.copy.ImageCopy
import dev.ikram.sandbox.feature.share.copy.MediaCopy
import dev.ikram.sandbox.feature.share.copy.PdfCopy
import dev.ikram.sandbox.feature.share.copy.PreparedShare
import dev.ikram.sandbox.feature.share.copy.ShareFiles
import dev.ikram.sandbox.feature.share.copy.TextCopy
import dev.ikram.sandbox.renderer.SandboxRenderer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The real safe copies: isolated renderer, Transformer and FileProvider on a device. */
@RunWith(AndroidJUnit4::class)
class SafeCopyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "safe-copy-test-${UUID.randomUUID()}")
    private val storage = VaultStorage(root)
    private val database = Room.inMemoryDatabaseBuilder(context, SandboxDatabase::class.java).build()
    private val repository = VaultRepository(database.vaultFileDao(), storage, Dispatchers.IO)
    private val files = ShareFiles(storage, TempShareCleaner(storage, Clock.systemUTC()))
    private val renderer = SandboxRenderer(context)

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun aPhotoComesOutUprightWithoutItsHiddenDetails() = runBlocking {
        // 40 x 20 as stored, with EXIF saying "turn a quarter" and a location.
        val source = store(DetectedType.JPEG, "holiday.jpg") { file ->
            Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
                .compress(Bitmap.CompressFormat.JPEG, 90, file.outputStream())
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "51/1,30/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_MAKE, "SecretCam")
                saveAttributes()
            }
        }
        val share = finished(ImageCopy(renderer, files, repository, Dispatchers.IO).copy(source))

        assertEquals(DetectedType.JPEG, MagicSniffer.sniff(share.file.readBytes()))
        assertEquals("holiday.jpg", share.displayName)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(share.file.path, bounds)
        assertEquals(20, bounds.outWidth)
        assertEquals(40, bounds.outHeight)
        val exif = ExifInterface(share.file.path)
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        )
    }

    @Test
    fun aPdfKeepsEveryPageAndNothingElse() = runBlocking {
        val source = store(DetectedType.PDF, "report.pdf") { file ->
            PdfDocument().apply {
                repeat(3) { index ->
                    val page = startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    page.canvas.drawText("Page ${index + 1}", 72f, 72f, Paint().apply { textSize = 24f })
                    finishPage(page)
                }
                file.outputStream().use { writeTo(it) }
                close()
            }
        }
        val share = finished(PdfCopy(renderer, files, repository, Dispatchers.IO).copy(source))

        assertEquals(DetectedType.PDF, MagicSniffer.sniff(share.file.readBytes()))
        // The new file opens in the isolated renderer with the same pages.
        assertEquals(3, renderer.pdfPageCount(share.file))
        assertFalse(String(share.file.readBytes(), Charsets.ISO_8859_1).contains("/Font"))
    }

    @Test
    fun soundIsReencodedIntoANewFile() = runBlocking {
        val source = store(DetectedType.WAV, "voice.wav") { it.writeBytes(wav()) }
        val share = finished(MediaCopy(context, files).copy(source), timeoutMs = 60_000)

        assertEquals("voice.m4a", share.displayName)
        assertEquals("audio/mp4", share.mimeType)
        // An MP4 holding only sound; either brand is right.
        assertTrue(MagicSniffer.sniff(share.file.readBytes()) in setOf(DetectedType.M4A, DetectedType.MP4))
    }

    @Test
    fun textComesOutAsCleanUtf8() = runBlocking {
        // A direction override and a bell character, in markup a browser would run.
        val source = store(DetectedType.TEXT, "page.html") { it.writeText("<p>CAFE_E\u202E</p>\u0007") }
        val share = finished(TextCopy(files, Dispatchers.IO).copy(source))

        assertEquals("page.txt", share.displayName)
        assertEquals("text/plain; charset=utf-8", share.mimeType)
        assertEquals("<p>CAFE_E</p>", share.file.readText())
    }

    @Test
    fun theProviderServesSharedCopiesOnly() {
        val shared = File(context.filesDir, "share_tmp").apply { mkdirs() }.resolve("${UUID.randomUUID()}.txt")
        try {
            shared.writeText("hello")
            val uri = FileProvider.getUriForFile(context, ShareFileProvider.authority(context), shared, "notes.txt")
            context.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("notes.txt", cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)))
            }
            assertEquals("hello", context.contentResolver.openInputStream(uri)!!.reader().readText())

            val vault = File(context.filesDir, "vault/${UUID.randomUUID()}.bin")
            val refused =
                runCatching { FileProvider.getUriForFile(context, ShareFileProvider.authority(context), vault) }
            assertTrue(refused.exceptionOrNull() is IllegalArgumentException)
        } finally {
            shared.delete()
        }
    }

    private suspend fun finished(flow: Flow<CopyProgress>, timeoutMs: Long = 30_000): PreparedShare =
        withTimeout(timeoutMs) { flow.filterIsInstance<CopyProgress.Done>().first().share }

    private fun store(type: DetectedType, name: String, write: (File) -> Unit): VaultFile {
        val id = UUID.randomUUID().toString()
        storage.vaultDir.mkdirs()
        val file = storage.fileFor(id)
        write(file)
        return VaultFile(
            id = id,
            displayName = name,
            sizeBytes = file.length(),
            detectedType = type,
            claimedType = null,
            category = TypePolicy.categoryOf(type),
            sha256 = "00",
            sourceUrl = "https://example.org/$name",
            sourceHost = "example.org",
            createdAt = Instant.now(),
            scanStatus = ScanStatus.NotChecked,
            isMismatch = false,
            mismatchAcknowledged = false,
            openFailed = false,
        )
    }

    /** One second of a quiet 16-bit mono tone at 8 kHz. */
    private fun wav(): ByteArray {
        val rate = 8_000
        val data = rate * 2
        return ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + data)
            put("WAVEfmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(rate)
            putInt(rate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(data)
            repeat(rate) { putShort(((it % 16) * 200).toShort()) }
        }.array()
    }
}
