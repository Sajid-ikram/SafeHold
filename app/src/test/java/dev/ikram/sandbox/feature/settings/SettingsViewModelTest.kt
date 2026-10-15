package dev.ikram.sandbox.feature.settings

import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.data.storage.TempShareCleaner
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.data.work.VaultCleaner
import dev.ikram.sandbox.core.model.AutoDelete
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.fakes.FakePreferencesStore
import dev.ikram.sandbox.fakes.FakeVaultFileDao
import dev.ikram.sandbox.fakes.addFile
import dev.ikram.sandbox.feature.lock.LockState
import dev.ikram.sandbox.feature.lock.VaultLock
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val dao = FakeVaultFileDao()
    private val repository = SettingsRepository(FakePreferencesStore())
    private val lock = VaultLock(repository, clock, CoroutineScope(Dispatchers.Unconfined))
    private val storage by lazy { VaultStorage(folder.newFolder("files")) }
    private val viewModel by lazy {
        val vault = VaultRepository(dao, storage, Dispatchers.Unconfined)
        SettingsViewModel(
            repository,
            lock,
            VaultCleaner(dao, vault, repository, TempShareCleaner(storage, clock), clock),
        )
    }
    private val state get() = viewModel.uiState.value
    private val key = "d".repeat(64)

    @Test
    fun showsTheSavedSettings() {
        runBlocking { repository.setAskBeforeDownload(false) }
        assertTrue(state.loaded)
        assertFalse(state.settings.askBeforeDownload)
    }

    @Test
    fun turningChecksOnWithoutAKeyAsksForOne() {
        viewModel.onEvent(SettingsEvent.AutoScanChanged(true))
        assertTrue(state.editingKey)
        assertFalse(state.settings.autoScan)
    }

    @Test
    fun aWrongKeyIsRefusedAndNothingIsSaved() {
        viewModel.onEvent(SettingsEvent.AutoScanChanged(true))
        viewModel.onEvent(SettingsEvent.KeyEntered("not a key"))
        assertTrue(state.keyError)
        assertTrue(state.editingKey)
        assertNull(state.settings.virusTotalKey)
    }

    @Test
    fun aKeyFromTheSwitchTurnsChecksOn() {
        viewModel.onEvent(SettingsEvent.AutoScanChanged(true))
        viewModel.onEvent(SettingsEvent.KeyEntered(key))
        assertFalse(state.editingKey)
        assertEquals(key, state.settings.virusTotalKey)
        assertTrue(state.settings.autoScan)
        assertEquals(SettingsMessage.KEY_SAVED, state.message)
    }

    @Test
    fun aKeyAddedOnItsOwnLeavesChecksAsTheyWere() {
        viewModel.onEvent(SettingsEvent.EditKeyTapped)
        viewModel.onEvent(SettingsEvent.KeyEntered(key))
        assertEquals(key, state.settings.virusTotalKey)
        assertFalse(state.settings.autoScan)
    }

    @Test
    fun removingTheKeyTurnsChecksOff() {
        viewModel.onEvent(SettingsEvent.AutoScanChanged(true))
        viewModel.onEvent(SettingsEvent.KeyEntered(key))
        viewModel.onEvent(SettingsEvent.KeyRemoved)
        assertNull(state.settings.virusTotalKey)
        assertFalse(state.settings.autoScan)
    }

    @Test
    fun turningTheLockOnLeavesTheVaultOpenForNow() {
        viewModel.onEvent(SettingsEvent.VaultLockConfirmed(true))
        assertTrue(state.settings.vaultLock)
        assertEquals(LockState.OPEN, lock.state.value)
    }

    @Test
    fun aShorterAgeDeletesStraightAway() {
        dao.addFile("old.pdf", DetectedType.PDF, createdAt = now.minusSeconds(10L * 24 * 60 * 60))
        viewModel.onEvent(SettingsEvent.AutoDeleteChanged(AutoDelete.AFTER_7_DAYS))
        assertEquals(AutoDelete.AFTER_7_DAYS, state.settings.autoDelete)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun clearingBrowsingDataAsksFirst() {
        viewModel.onEvent(SettingsEvent.ClearBrowsingTapped)
        assertTrue(state.confirmClearBrowsing)
        viewModel.onEvent(SettingsEvent.BrowsingDataCleared)
        assertFalse(state.confirmClearBrowsing)
        assertEquals(SettingsMessage.BROWSING_DATA_CLEARED, state.message)
    }
}
