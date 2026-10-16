package dev.ikram.sandbox.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.download.NewFilesCounter
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.model.Settings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** State for the app chrome around the screens: where to start, and the bottom bar's vault badge. */
@HiltViewModel
class ShellViewModel @Inject constructor(newFiles: NewFilesCounter, settings: SettingsRepository) : ViewModel() {
    val newFiles: StateFlow<Int> = newFiles.count

    /** Null until settings have been read. */
    val onboardingDone: StateFlow<Boolean?> = settings.settings.map<Settings, Boolean?> { it.onboardingDone }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
