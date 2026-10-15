package dev.ikram.sandbox.core.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Runs [VaultCleaner] twice a day, whether or not SafeHold is open. Plain background
 * work: no network, no foreground service, no wake lock of its own.
 */
class AutoDeleteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication<CleanupEntryPoint>(applicationContext).vaultCleaner().clean()
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CleanupEntryPoint {
        fun vaultCleaner(): VaultCleaner
    }

    companion object {
        private const val NAME = "vault-cleanup"
        private const val EVERY_HOURS = 12L

        /** Safe to call on every start: an existing schedule is kept. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoDeleteWorker>(EVERY_HOURS, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
