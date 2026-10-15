package dev.ikram.sandbox.core.data.repository

import dev.ikram.sandbox.core.data.FakeVaultFileDao
import dev.ikram.sandbox.core.data.db.VaultFileEntity
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.ScanStatus
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private val dao = FakeVaultFileDao()
    private val storage by lazy { VaultStorage(folder.newFolder("files")) }
    private val repository by lazy { VaultRepository(dao, storage, Dispatchers.Unconfined) }

    private fun entity(id: String, type: DetectedType = DetectedType.PDF, createdAt: Long = 0) = VaultFileEntity(
        id = id,
        displayName = "$id.pdf",
        sizeBytes = 10,
        detectedType = type.name,
        claimedType = null,
        sha256 = "00",
        sourceUrl = "https://x.example/a",
        sourceHost = "x.example",
        createdAtMillis = createdAt,
        scanStatus = "FLAGGED",
        scanFlaggedCount = 3,
        scanEngineCount = 70,
        scanCheckedAtMillis = 5_000,
        isMismatch = false,
        mismatchAcknowledged = false,
        openFailed = false,
    )

    private fun storeFile(id: String, old: Boolean = true) {
        val file = storage.commit(storage.newPartial(id).apply { writeText("bytes") }, id)
        if (old) file.setLastModified(0)
    }

    private fun id() = UUID.randomUUID().toString()

    @Test
    fun filesAreMappedNewestFirstWithCategoryFromThePolicy() = runBlocking {
        val older = id()
        val newer = id()
        dao.insert(entity(older, DetectedType.APK, createdAt = 1))
        dao.insert(entity(newer, createdAt = 2))
        val files = repository.files.first()
        assertEquals(listOf(newer, older), files.map { it.id })
        assertEquals(FileCategory.BLOCKED, files[1].category)
        assertEquals(ScanStatus.Flagged(3, 70, Instant.ofEpochMilli(5_000)), files[0].scanStatus)
    }

    @Test
    fun unknownStoredTypeReadsAsUnknown() = runBlocking {
        dao.insert(entity(id()).copy(detectedType = "SOMETHING_REMOVED"))
        assertEquals(DetectedType.UNKNOWN, repository.files.first().single().detectedType)
    }

    @Test
    fun deleteRemovesFileAndRow() = runBlocking {
        val id = id()
        storeFile(id)
        dao.insert(entity(id))
        repository.delete(id)
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(storage.storedIds().isEmpty())
    }

    @Test
    fun reconcileDropsOrphansBothWays() = runBlocking {
        val kept = id()
        storeFile(kept)
        dao.insert(entity(kept))
        val orphanFile = id()
        storeFile(orphanFile)
        val orphanRow = id()
        dao.insert(entity(orphanRow))
        val inFlight = id()
        storeFile(inFlight, old = false) // just committed by this process, row not written yet

        repository.reconcile()

        assertEquals(setOf(kept, inFlight), storage.storedIds())
        assertEquals(listOf(kept), dao.rows.value.map { it.id })
    }

    @Test
    fun acknowledgeAndOpenFailed() = runBlocking {
        val id = id()
        dao.insert(entity(id))
        repository.acknowledgeMismatch(id)
        repository.markOpenFailed(id)
        val file = repository.file(id).first()!!
        assertTrue(file.mismatchAcknowledged)
        assertTrue(file.openFailed)
    }
}
