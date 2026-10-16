package dev.ikram.sandbox.feature.onboarding

import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.download.NewFilesCounter
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.fakes.FakePreferencesStore
import dev.ikram.sandbox.navigation.ShellViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val settings = SettingsRepository(FakePreferencesStore())
    private val shell by lazy { ShellViewModel(NewFilesCounter(), settings) }

    @Test
    fun aFirstStartShowsOnboarding() = assertEquals(false, shell.onboardingDone.value)

    @Test
    fun finishingItMeansItIsNotShownAgain() {
        OnboardingViewModel(settings, CoroutineScope(Dispatchers.Unconfined)).finish()
        assertEquals(true, shell.onboardingDone.value)
    }
}
