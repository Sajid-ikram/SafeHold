package dev.ikram.sandbox

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.repository.VaultRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class SandboxApp : Application() {
    @Inject lateinit var vaultRepository: VaultRepository

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Before any download can start: clear what a killed process left behind.
        applicationScope.launch { vaultRepository.reconcile() }
    }
}
