package dev.ikram.sandbox.core.data.work

import dev.ikram.sandbox.core.data.FakePreferencesStore
import dev.ikram.sandbox.core.data.FakeVaultFileDao
import dev.ikram.sandbox.core.data.db.VaultFileEntity
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.data.storage.TempShareCleaner
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.AutoDelete
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultCleanerTest {
    @get:Rule val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val dao = FakeVaultFileDao()
    private val settings = SettingsRepository(FakePreferencesStore())
    private val storage by lazy { VaultStorage(folder.newFolder("files")) }
    private val cleaner by lazy {
        VaultCleaner(
            dao,
            VaultRepository(dao, storage, Dispatchers.Unconfined),
            settings,
            TempShareCleaner(storage, clock),
            clock,
        )
    }

    private fun file(age: Duration): String = runBlocking {
        val id = UUID.randomUUID().toString()
        storage.commit(storage.newPartial(id).apply { writeText("x") }, id)
        dao.insert(
            VaultFileEntity(
                id = id,
                displayName = "f",
                sizeBytes = 1,
                detectedType = "PDF",
                claimedType = null,
                sha256 = "00",
                sourceUrl = "https://example.org/f",
                sourceHost = "example.org",
                createdAtMillis = now.minus(age).toEpochMilli(),
                scanStatus = "NOT_CHECKED",
                scanFlaggedCount = null,
                isMismatch = false,
                mismatchAcknowledged = false,
                openFailed = false,
            ),
        )
        id
    }

    @Test
    fun nothingIsDeletedUnlessSomeoneChoseTo() = runBlocking {
        val old = file(Duration.ofDays(400))
        assertEquals(0, cleaner.clean())
        assertTrue(storage.fileFor(old).exists())
    }

    @Test
    fun filesPastTheChosenAgeGoWithTheirBytes() = runBlocking {
        settings.setAutoDelete(AutoDelete.AFTER_30_DAYS)
        val old = file(Duration.ofDays(31))
        val recent = file(Duration.ofDays(29))
        assertEquals(1, cleaner.clean())
        assertFalse(storage.fileFor(old).exists())
        assertEquals(listOf(recent), dao.ids())
    }

    @Test
    fun sharedCopiesAreSweptEitherWay() = runBlocking {
        val shared = storage.newShareFile("jpg").apply {
            writeText("copy")
            setLastModified(now.minus(Duration.ofHours(2)).toEpochMilli())
        }
        cleaner.clean()
        assertFalse(shared.exists())
    }
}
