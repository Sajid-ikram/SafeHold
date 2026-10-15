package dev.ikram.sandbox.feature.lock

import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.fakes.FakePreferencesStore
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LockViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val settings = SettingsRepository(FakePreferencesStore())
    private val lock = VaultLock(settings, Clock.systemUTC(), CoroutineScope(Dispatchers.Unconfined))
    private val viewModel = LockViewModel(lock, settings)

    @Test
    fun unlockingOpensTheVault() {
        runBlocking { settings.setVaultLock(true) }
        assertEquals(LockState.LOCKED, viewModel.state.value)
        viewModel.unlocked()
        assertEquals(LockState.OPEN, viewModel.state.value)
    }

    @Test
    fun withNoScreenLockLeftTheVaultLockSwitchesOff() {
        runBlocking { settings.setVaultLock(true) }
        viewModel.screenLockGone()
        assertFalse(runBlocking { settings.current().vaultLock })
        assertEquals(LockState.OPEN, viewModel.state.value)
    }
}
