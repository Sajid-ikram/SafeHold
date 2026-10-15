package dev.ikram.sandbox.feature.lock

import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.model.Settings
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class LockState {
    /** Settings not read yet: show nothing rather than flash the vault or a lock. */
    UNKNOWN,
    LOCKED,
    OPEN,
}

/**
 * Whether the vault may be shown. With the lock on, it opens after fingerprint or screen
 * lock, and locks again once SafeHold has been in the background for [GRACE_MILLIS] (long
 * enough to pass a file to another app and come back, not long enough to walk away).
 */
@Singleton
class VaultLock @Inject constructor(
    settings: SettingsRepository,
    private val clock: Clock,
    @param:ApplicationScope scope: CoroutineScope,
) {
    private val unlocked = MutableStateFlow(false)
    private var backgroundedAt: Long? = null

    /** Null until settings have been read. */
    val enabled: StateFlow<Boolean?> = settings.settings.map<Settings, Boolean?> { it.vaultLock }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val state: StateFlow<LockState> = combine(enabled, unlocked) { enabled, unlocked ->
        when {
            enabled == null -> LockState.UNKNOWN
            !enabled || unlocked -> LockState.OPEN
            else -> LockState.LOCKED
        }
    }.stateIn(scope, SharingStarted.Eagerly, LockState.UNKNOWN)

    /** After a successful fingerprint or screen lock check. */
    fun unlock() {
        unlocked.value = true
    }

    fun onBackground() {
        backgroundedAt = clock.millis()
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (clock.millis() - since >= GRACE_MILLIS) unlocked.value = false
    }

    companion object {
        const val GRACE_MILLIS = 30_000L
    }
}
