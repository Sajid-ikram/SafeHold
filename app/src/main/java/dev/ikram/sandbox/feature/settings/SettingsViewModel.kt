package dev.ikram.sandbox.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.data.work.VaultCleaner
import dev.ikram.sandbox.core.model.AutoDelete
import dev.ikram.sandbox.core.model.Settings
import dev.ikram.sandbox.feature.lock.VaultLock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsMessage { BROWSING_DATA_CLEARED, KEY_SAVED, KEY_REMOVED, NO_SCREEN_LOCK }

data class SettingsUiState(
    val settings: Settings = Settings(),
    val loaded: Boolean = false,
    /** The VirusTotal key dialog is open. */
    val editingKey: Boolean = false,
    val keyError: Boolean = false,
    /** The dialog came from the automatic-checks switch, so saving a key also turns them on. */
    val keyTurnsOnScan: Boolean = false,
    val confirmClearBrowsing: Boolean = false,
    val message: SettingsMessage? = null,
)

sealed interface SettingsEvent {
    data class AskBeforeDownloadChanged(val ask: Boolean) : SettingsEvent

    data class AutoDeleteChanged(val autoDelete: AutoDelete) : SettingsEvent

    /** Switching on without a key asks for one first. */
    data class AutoScanChanged(val scan: Boolean) : SettingsEvent

    data object EditKeyTapped : SettingsEvent

    data class KeyEntered(val key: String) : SettingsEvent

    data object KeyRemoved : SettingsEvent

    data object KeyDialogDismissed : SettingsEvent

    /** Only sent after the fingerprint or screen lock check succeeded. */
    data class VaultLockConfirmed(val locked: Boolean) : SettingsEvent

    data object NoScreenLock : SettingsEvent

    data object ClearBrowsingTapped : SettingsEvent

    data object ClearBrowsingDismissed : SettingsEvent

    /** The screen cleared the WebViews; this only reports it. */
    data object BrowsingDataCleared : SettingsEvent

    data object MessageShown : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val vaultLock: VaultLock,
    private val cleaner: VaultCleaner,
) : ViewModel() {
    private val ui = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(repository.settings, ui) { settings, ui ->
        ui.copy(settings = settings, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.AskBeforeDownloadChanged -> save { setAskBeforeDownload(event.ask) }

            is SettingsEvent.AutoDeleteChanged -> save {
                setAutoDelete(event.autoDelete)
                // A shorter age applies straight away, not at the next scheduled run.
                cleaner.clean()
            }

            is SettingsEvent.AutoScanChanged -> when {
                !event.scan -> save { setAutoScan(false) }

                uiState.value.settings.virusTotalKey == null -> ui.update {
                    it.copy(editingKey = true, keyError = false, keyTurnsOnScan = true)
                }

                else -> save { setAutoScan(true) }
            }

            SettingsEvent.EditKeyTapped -> ui.update {
                it.copy(editingKey = true, keyError = false, keyTurnsOnScan = false)
            }

            is SettingsEvent.KeyEntered -> saveKey(event.key)

            SettingsEvent.KeyRemoved -> {
                ui.update { it.copy(editingKey = false, message = SettingsMessage.KEY_REMOVED) }
                save { setVirusTotalKey(null) }
            }

            SettingsEvent.KeyDialogDismissed -> ui.update { it.copy(editingKey = false, keyError = false) }

            is SettingsEvent.VaultLockConfirmed -> {
                // They have just proved who they are: the vault stays open for now.
                if (event.locked) vaultLock.unlock()
                save { setVaultLock(event.locked) }
            }

            SettingsEvent.NoScreenLock -> ui.update { it.copy(message = SettingsMessage.NO_SCREEN_LOCK) }

            SettingsEvent.ClearBrowsingTapped -> ui.update { it.copy(confirmClearBrowsing = true) }

            SettingsEvent.ClearBrowsingDismissed -> ui.update { it.copy(confirmClearBrowsing = false) }

            SettingsEvent.BrowsingDataCleared -> ui.update {
                it.copy(confirmClearBrowsing = false, message = SettingsMessage.BROWSING_DATA_CLEARED)
            }

            SettingsEvent.MessageShown -> ui.update { it.copy(message = null) }
        }
    }

    private fun saveKey(raw: String) {
        if (SettingsRepository.normalizeKey(raw) == null) {
            ui.update { it.copy(keyError = true) }
            return
        }
        val turnOnScan = ui.value.keyTurnsOnScan
        ui.update { it.copy(editingKey = false, keyError = false, message = SettingsMessage.KEY_SAVED) }
        save {
            setVirusTotalKey(raw)
            if (turnOnScan) setAutoScan(true)
        }
    }

    private fun save(change: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { repository.change() }
    }
}
