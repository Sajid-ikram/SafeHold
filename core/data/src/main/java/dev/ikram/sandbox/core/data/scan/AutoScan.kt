package dev.ikram.sandbox.core.data.scan

import dev.ikram.sandbox.core.data.download.VaultDownloader
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import javax.inject.Inject

/** Step 7 of every download: with automatic checks on, each new file is looked up by its hash. */
class AutoScan @Inject constructor(
    private val downloader: VaultDownloader,
    private val settings: SettingsRepository,
    private val scans: ScanRepository,
) {
    /** Runs for as long as the app does. */
    suspend fun run() {
        downloader.saved.collect { fileId ->
            val current = settings.current()
            if (current.autoScan && current.virusTotalKey != null) scans.requestCheck(fileId)
        }
    }
}
