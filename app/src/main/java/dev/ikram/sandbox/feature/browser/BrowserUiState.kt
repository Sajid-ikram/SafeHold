package dev.ikram.sandbox.feature.browser

import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.feature.browser.web.WebEvent

data class BrowserUiState(
    val tabs: List<BrowserTab>,
    val activeTabId: String,
    val downloadPrompt: DownloadPrompt? = null,
    val message: BrowserMessage? = null,
    val showTabSwitcher: Boolean = false,
    /** The latest download, running or just finished, for the progress card. */
    val download: DownloadState? = null,
    /** Files saved since the vault was last opened. */
    val newFiles: Int = 0,
) {
    val activeTab: BrowserTab get() = tabs.first { it.id == activeTabId }
}

/**
 * The "Download this file?" sheet. Worded from what the server CLAIMS; the vault decides
 * from the bytes once the file arrives.
 */
data class DownloadPrompt(
    val request: DownloadRequest,
    val fileName: String,
    /** "PDF document", or null when neither the name nor the server said. */
    val typeLabel: String?,
    val category: FileCategory,
    /** Null when the server did not send a length. */
    val sizeBytes: Long?,
    val sourceHost: String?,
    val kind: Kind,
) {
    enum class Kind {
        SAVE,

        /** Claimed to be an app installer: saved locked if at all. */
        BLOCKED_APP,

        /** Claimed to be a program or script: saved locked if at all. */
        BLOCKED_PROGRAM,
    }
}

enum class BrowserMessage {
    NAVIGATION_BLOCKED,
    DOWNLOAD_UNSUPPORTED,
}

sealed interface BrowserEvent {
    data class SubmitAddress(val text: String) : BrowserEvent

    data object GoBack : BrowserEvent

    data object GoForward : BrowserEvent

    data object Reload : BrowserEvent

    data object StopLoading : BrowserEvent

    data object NewTab : BrowserEvent

    data class SelectTab(val tabId: String) : BrowserEvent

    data class CloseTab(val tabId: String) : BrowserEvent

    data object ShowTabs : BrowserEvent

    data object HideTabs : BrowserEvent

    /** "Save to vault", or "Save locked" for blocked types. */
    data object ConfirmDownload : BrowserEvent

    data object DismissDownload : BrowserEvent

    /** The progress card for a finished download has been shown long enough. */
    data class DownloadStatusDismissed(val downloadId: String) : BrowserEvent

    data object MessageShown : BrowserEvent

    /** The screen is showing WebViews from a pool; a new [generation] means they must be reloaded. */
    data class HostAttached(val generation: String) : BrowserEvent

    data class Web(val tabId: String, val event: WebEvent) : BrowserEvent
}
