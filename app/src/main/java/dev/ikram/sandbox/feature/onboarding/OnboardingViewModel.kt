package dev.ikram.sandbox.feature.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ViewModel() {
    /** Shown once. Saved in the app scope: this screen is gone a moment later. */
    fun finish() {
        scope.launch { settings.setOnboardingDone() }
    }
}
