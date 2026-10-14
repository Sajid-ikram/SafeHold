package dev.ikram.sandbox.feature.vault

import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import java.time.LocalDate

enum class VaultFilter(private val categories: Set<FileCategory>) {
    ALL(FileCategory.entries.toSet()),
    IMAGES(setOf(FileCategory.IMAGE)),
    VIDEOS(setOf(FileCategory.VIDEO)),
    AUDIO(setOf(FileCategory.AUDIO)),
    PDFS(setOf(FileCategory.PDF)),
    TEXT(setOf(FileCategory.TEXT)),
    ARCHIVES(setOf(FileCategory.ARCHIVE)),

    /** Unknown files are locked just like executables, so they live here too. */
    BLOCKED(setOf(FileCategory.BLOCKED, FileCategory.UNKNOWN)),
    ;

    fun matches(category: FileCategory): Boolean = category in categories
}

enum class VaultLayout { LIST, GRID }

sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class Day(val date: LocalDate) : DayLabel
}

data class VaultSection(val label: DayLabel, val files: List<VaultFile>)

sealed interface VaultMessage {
    data object ShareNotReady : VaultMessage

    data object HashCopied : VaultMessage

    data class Deleted(val name: String) : VaultMessage
}

data class VaultUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    /** No files and nothing downloading: the empty state, not "nothing matches". */
    val isEmpty: Boolean = false,
    val query: String = "",
    val filter: VaultFilter = VaultFilter.ALL,
    val layout: VaultLayout = VaultLayout.LIST,
    val sections: List<VaultSection> = emptyList(),
    val downloads: List<DownloadState.Running> = emptyList(),
    /** The file whose details sheet is open. */
    val details: VaultFile? = null,
    /** The file whose mismatch warning is showing. */
    val mismatch: VaultFile? = null,
    /** The file waiting for delete confirmation. */
    val confirmDelete: VaultFile? = null,
    val message: VaultMessage? = null,
)

sealed interface VaultEvent {
    /** While the vault is on screen, new files are not counted as unseen. */
    data class VisibilityChanged(val visible: Boolean) : VaultEvent

    data class QueryChanged(val query: String) : VaultEvent

    data class FilterSelected(val filter: VaultFilter) : VaultEvent

    data class LayoutSelected(val layout: VaultLayout) : VaultEvent

    data class FileTapped(val fileId: String) : VaultEvent

    data object DetailsDismissed : VaultEvent

    data class OpenTapped(val fileId: String) : VaultEvent

    data class ShareTapped(val fileId: String) : VaultEvent

    data class DeleteTapped(val fileId: String) : VaultEvent

    data object DeleteConfirmed : VaultEvent

    data object DeleteDismissed : VaultEvent

    /** "Keep it locked" for blocked files, "Open anyway" for the rest. */
    data object MismatchAccepted : VaultEvent

    data object MismatchDelete : VaultEvent

    data object MismatchDismissed : VaultEvent

    data class CancelDownload(val downloadId: String) : VaultEvent

    data object HashCopied : VaultEvent

    data object MessageShown : VaultEvent
}

sealed interface VaultEffect {
    data class OpenViewer(val fileId: String) : VaultEffect
}
