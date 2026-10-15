package dev.ikram.sandbox.core.data.storage

import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * Shared copies live in `share_tmp/` for an hour, long enough for the receiving app to
 * read them, then go. Runs at start up, before every share and on the cleanup schedule.
 */
class TempShareCleaner @Inject constructor(private val storage: VaultStorage, private val clock: Clock) {
    /** Deletes copies, and their labels, older than [keepFor]. Returns how many files went. */
    fun sweep(keepFor: Duration = KEEP_FOR): Int {
        val cutoff = clock.millis() - keepFor.toMillis()
        return storage.shareTmpDir.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < cutoff }
            .count { it.delete() }
    }

    companion object {
        val KEEP_FOR: Duration = Duration.ofHours(1)
    }
}
