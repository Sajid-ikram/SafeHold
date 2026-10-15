package dev.ikram.sandbox.core.data.storage

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TempShareCleanerTest {
    @get:Rule val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val storage by lazy { VaultStorage(folder.newFolder("files")) }
    private val cleaner by lazy { TempShareCleaner(storage, Clock.fixed(now, ZoneOffset.UTC)) }

    private fun shared(age: Duration) = storage.newShareFile("jpg").apply {
        writeText("copy")
        setLastModified(now.minus(age).toEpochMilli())
    }

    @Test
    fun copiesGoAfterAnHour() {
        val old = shared(Duration.ofMinutes(61))
        val fresh = shared(Duration.ofMinutes(59))
        assertEquals(1, cleaner.sweep())
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun nothingToSweepIsFine() = assertEquals(0, cleaner.sweep())

    @Test
    fun theVaultIsNeverTouched() {
        val id = "0b0b9a5c-0c3a-4a8e-9a39-7a0b2f6a1c11"
        val vaultFile = storage.newPartial(id).apply { writeText("x") }.let { storage.commit(it, id) }
        vaultFile.setLastModified(now.minus(Duration.ofDays(30)).toEpochMilli())
        cleaner.sweep()
        assertTrue(vaultFile.exists())
    }
}
