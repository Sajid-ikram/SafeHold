package dev.ikram.sandbox.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LockViewModel @Inject constructor(private val lock: VaultLock, private val settings: SettingsRepository) :
    ViewModel() {
    val state: StateFlow<LockState> = lock.state

    fun unlocked() = lock.unlock()

    /**
     * The phone no longer has a fingerprint or screen lock, so the vault lock cannot be
     * opened and is switched off. Only someone who knew the old screen lock could have
     * removed it, so this hands nothing to anyone else.
     */
    fun screenLockGone() {
        viewModelScope.launch { settings.setVaultLock(false) }
    }
}
