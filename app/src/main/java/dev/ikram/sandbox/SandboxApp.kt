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
import dev.ikram.sandbox.core.data.scan.AutoScan
import dev.ikram.sandbox.core.data.work.AutoDeleteWorker
import dev.ikram.sandbox.core.data.work.VaultCleaner
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
        val scope = startup.applicationScope()
        // Before any download can start: clear what a killed process left behind, then
        // expired files and shared copies past their hour.
        scope.launch {
            startup.vaultRepository().reconcile()
            startup.vaultCleaner().clean()
        }
        scope.launch { startup.autoScan().run() }
        AutoDeleteWorker.schedule(this)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StartupEntryPoint {
        fun vaultRepository(): VaultRepository

        fun vaultCleaner(): VaultCleaner

        fun autoScan(): AutoScan

        @ApplicationScope
        fun applicationScope(): CoroutineScope
    }
}
