package dev.ikram.sandbox

import android.app.Application
import android.os.Process
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.repository.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class SandboxApp : Application() {
    /**
     * Android creates this Application in the isolated :renderer process too. Nothing
     * here may run there: that process gets no database, no files and no app graph, only
     * the renderer service. So no field injection, and an early return when isolated.
     */
    override fun onCreate() {
        super.onCreate()
        if (Process.isIsolated()) return
        val startup = EntryPointAccessors.fromApplication<StartupEntryPoint>(this)
        // Before any download can start: clear what a killed process left behind.
        startup.applicationScope().launch { startup.vaultRepository().reconcile() }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StartupEntryPoint {
        fun vaultRepository(): VaultRepository

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }
}
