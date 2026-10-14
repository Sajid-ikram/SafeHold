package dev.ikram.sandbox.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.download.DownloadQueue
import dev.ikram.sandbox.core.data.download.NewFilesCounter
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val downloadQueue: DownloadQueue,
    private val newFiles: NewFilesCounter,
    private val clock: Clock,
) : ViewModel() {
    private val controls = MutableStateFlow(Controls())
    private val effectChannel = Channel<VaultEffect>(Channel.BUFFERED)

    /** Null until the first read from the database. */
    private val files: StateFlow<List<VaultFile>?> = repository.files.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null,
    )

    val uiState: StateFlow<VaultUiState> = combine(files, downloadQueue.downloads, controls, ::build)
        .stateIn(viewModelScope, SharingStarted.Eagerly, VaultUiState(today = LocalDate.now(clock)))

    val effects: Flow<VaultEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: VaultEvent) {
        when (event) {
            is VaultEvent.VisibilityChanged -> newFiles.setVaultVisible(event.visible)
            is VaultEvent.QueryChanged -> controls.update { it.copy(query = event.query) }
            is VaultEvent.FilterSelected -> controls.update { it.copy(filter = event.filter) }
            is VaultEvent.LayoutSelected -> controls.update { it.copy(layout = event.layout) }
            is VaultEvent.FileTapped -> onFileTapped(event.fileId)
            VaultEvent.DetailsDismissed -> controls.update { it.copy(detailsId = null) }
            is VaultEvent.OpenTapped -> open(event.fileId)
            is VaultEvent.ShareTapped -> controls.update { it.copy(message = VaultMessage.ShareNotReady) }
            is VaultEvent.DeleteTapped -> controls.update { it.copy(deleteId = event.fileId) }
            VaultEvent.DeleteConfirmed -> controls.value.deleteId?.let(::delete)
            VaultEvent.DeleteDismissed -> controls.update { it.copy(deleteId = null) }
            VaultEvent.MismatchAccepted -> onMismatchAccepted()
            VaultEvent.MismatchDelete -> controls.value.mismatchId?.let(::delete)
            VaultEvent.MismatchDismissed -> controls.update { it.copy(mismatchId = null) }
            is VaultEvent.CancelDownload -> downloadQueue.cancel(event.downloadId)
            VaultEvent.HashCopied -> controls.update { it.copy(message = VaultMessage.HashCopied) }
            VaultEvent.MessageShown -> controls.update { it.copy(message = null) }
        }
    }

    /** A disguised file shows its warning before anything else, once. */
    private fun onFileTapped(fileId: String) {
        val file = find(fileId) ?: return
        controls.update {
            if (file.isMismatch &&
                !file.mismatchAcknowledged
            ) {
                it.copy(mismatchId = fileId)
            } else {
                it.copy(detailsId = fileId)
            }
        }
    }

    private fun onMismatchAccepted() {
        val file = controls.value.mismatchId?.let(::find) ?: return
        viewModelScope.launch { repository.acknowledgeMismatch(file.id) }
        controls.update { it.copy(mismatchId = null) }
        if (TypePolicy.rulesFor(file.category).canOpen) {
            effectChannel.trySend(VaultEffect.OpenViewer(file.id))
        } else {
            controls.update { it.copy(detailsId = file.id) }
        }
    }

    private fun open(fileId: String) {
        val file = find(fileId) ?: return
        if (!TypePolicy.rulesFor(file.category).canOpen || file.openFailed) return
        controls.update { it.copy(detailsId = null) }
        effectChannel.trySend(VaultEffect.OpenViewer(fileId))
    }

    private fun delete(fileId: String) {
        val name = find(fileId)?.displayName ?: return
        controls.update {
            it.copy(deleteId = null, mismatchId = null, detailsId = null, message = VaultMessage.Deleted(name))
        }
        viewModelScope.launch { repository.delete(fileId) }
    }

    private fun find(fileId: String): VaultFile? = files.value?.firstOrNull { it.id == fileId }

    private fun build(files: List<VaultFile>?, downloads: List<DownloadState>, controls: Controls): VaultUiState {
        val today = LocalDate.now(clock)
        if (files == null) return VaultUiState(today = today, query = controls.query, filter = controls.filter)
        val running = downloads.filterIsInstance<DownloadState.Running>()
        val visible = files.filter { controls.filter.matches(it.category) && it.matches(controls.query) }
        val sections = visible
            .groupBy { it.createdAt.atZone(clock.zone).toLocalDate() }
            .toSortedMap(reverseOrder())
            .map { (date, dayFiles) -> VaultSection(labelFor(date, today), dayFiles) }
        fun byId(id: String?) = id?.let { wanted -> files.firstOrNull { it.id == wanted } }
        return VaultUiState(
            today = today,
            isLoading = false,
            isEmpty = files.isEmpty() && running.isEmpty(),
            query = controls.query,
            filter = controls.filter,
            layout = controls.layout,
            sections = sections,
            downloads = running,
            details = byId(controls.detailsId),
            mismatch = byId(controls.mismatchId),
            confirmDelete = byId(controls.deleteId),
            message = controls.message,
        )
    }

    private fun labelFor(date: LocalDate, today: LocalDate): DayLabel = when (date) {
        today -> DayLabel.Today
        today.minusDays(1) -> DayLabel.Yesterday
        else -> DayLabel.Day(date)
    }

    private fun VaultFile.matches(query: String): Boolean {
        val text = query.trim()
        return text.isEmpty() ||
            displayName.contains(text, ignoreCase = true) ||
            sourceHost.contains(text, ignoreCase = true) ||
            detectedType.label.contains(text, ignoreCase = true)
    }

    private data class Controls(
        val query: String = "",
        val filter: VaultFilter = VaultFilter.ALL,
        val layout: VaultLayout = VaultLayout.LIST,
        val detailsId: String? = null,
        val mismatchId: String? = null,
        val deleteId: String? = null,
        val message: VaultMessage? = null,
    )
}

/** Blocked files stay red; amber marks a disguise on a file that is otherwise allowed. */
val VaultFile.showsMismatch: Boolean get() = isMismatch && category != FileCategory.BLOCKED
