package dev.ikram.sandbox.feature.lock

import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.fakes.FakePreferencesStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultLockTest {
    private var now = 0L
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
    private val settings = SettingsRepository(FakePreferencesStore())
    private val lock = VaultLock(settings, clock, CoroutineScope(Dispatchers.Unconfined))

    private fun enable() = runBlocking { settings.setVaultLock(true) }

    @Test
    fun withTheLockOffTheVaultIsOpen() = assertEquals(LockState.OPEN, lock.state.value)

    @Test
    fun withTheLockOnItStartsLocked() {
        enable()
        assertEquals(LockState.LOCKED, lock.state.value)
        lock.unlock()
        assertEquals(LockState.OPEN, lock.state.value)
    }

    @Test
    fun aQuickTripAwayKeepsItOpen() {
        enable()
        lock.unlock()
        lock.onBackground()
        now += VaultLock.GRACE_MILLIS - 1
        lock.onForeground()
        assertEquals(LockState.OPEN, lock.state.value)
    }

    @Test
    fun aLongerTripAwayLocksIt() {
        enable()
        lock.unlock()
        lock.onBackground()
        now += VaultLock.GRACE_MILLIS
        lock.onForeground()
        assertEquals(LockState.LOCKED, lock.state.value)
    }

    @Test
    fun comingBackWithoutLeavingChangesNothing() {
        enable()
        lock.unlock()
        now += VaultLock.GRACE_MILLIS * 10
        lock.onForeground()
        assertEquals(LockState.OPEN, lock.state.value)
    }

    @Test
    fun turningTheLockOffOpensTheVault() {
        enable()
        runBlocking { settings.setVaultLock(false) }
        assertEquals(LockState.OPEN, lock.state.value)
    }
}
