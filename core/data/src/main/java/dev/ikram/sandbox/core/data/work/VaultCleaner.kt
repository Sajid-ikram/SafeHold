package dev.ikram.sandbox.core.data.work

import dev.ikram.sandbox.core.data.db.VaultFileDao
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.data.storage.TempShareCleaner
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** Removes files older than the auto-delete age, if one is set, and shared copies past their hour. */
class VaultCleaner @Inject constructor(
    private val dao: VaultFileDao,
    private val repository: VaultRepository,
    private val settings: SettingsRepository,
    private val shareCleaner: TempShareCleaner,
    private val clock: Clock,
) {
    /** Returns how many vault files were deleted. */
    suspend fun clean(): Int {
        shareCleaner.sweep()
        val days = settings.current().autoDelete.days ?: return 0
        val cutoff = clock.instant().minus(Duration.ofDays(days.toLong())).toEpochMilli()
        val expired = dao.idsCreatedBefore(cutoff)
        expired.forEach { repository.delete(it) }
        return expired.size
    }
}
