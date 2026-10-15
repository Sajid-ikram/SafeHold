package dev.ikram.sandbox.core.data.settings

import dev.ikram.sandbox.core.data.FakePreferencesStore
import dev.ikram.sandbox.core.model.AutoDelete
import dev.ikram.sandbox.core.model.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    private val repository = SettingsRepository(FakePreferencesStore())
    private val key = "a".repeat(64)

    private fun current() = runBlocking { repository.current() }

    @Test
    fun defaultsAskFirstAndDeleteNothing() = assertEquals(Settings(), current())

    @Test
    fun choicesAreKept() = runBlocking {
        repository.setAskBeforeDownload(false)
        repository.setAutoDelete(AutoDelete.AFTER_30_DAYS)
        repository.setVaultLock(true)
        repository.setOnboardingDone()
        val settings = current()
        assertFalse(settings.askBeforeDownload)
        assertEquals(AutoDelete.AFTER_30_DAYS, settings.autoDelete)
        assertTrue(settings.vaultLock)
        assertTrue(settings.onboardingDone)
    }

    @Test
    fun automaticChecksNeedAKey() = runBlocking {
        repository.setAutoScan(true)
        assertFalse(current().autoScan)
        repository.setVirusTotalKey(key)
        repository.setAutoScan(true)
        assertTrue(current().autoScan)
    }

    @Test
    fun removingTheKeyStopsAutomaticChecks() = runBlocking {
        repository.setVirusTotalKey(key)
        repository.setAutoScan(true)
        repository.setVirusTotalKey(null)
        assertNull(current().virusTotalKey)
        assertFalse(current().autoScan)
    }

    @Test
    fun pastedKeysAreTidied() = runBlocking {
        repository.setVirusTotalKey("  ${"AB".repeat(32)}\n")
        assertEquals("ab".repeat(32), current().virusTotalKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun anythingButAKeyIsRefused() {
        runBlocking { repository.setVirusTotalKey("hunter2") }
    }

    @Test
    fun keyShapes() {
        assertNull(SettingsRepository.normalizeKey("a".repeat(63)))
        assertNull(SettingsRepository.normalizeKey("g".repeat(64)))
        assertEquals(key, SettingsRepository.normalizeKey(key))
    }
}
