package dev.ikram.sandbox.feature.viewer.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.viewer.ViewerRouterViewModel
import dev.ikram.sandbox.feature.viewer.image.PageState
import dev.ikram.sandbox.feature.viewer.image.RenderResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PdfDocumentState {
    data object Loading : PdfDocumentState

    data class Ready(val pageCount: Int) : PdfDocumentState

    data object Unreadable : PdfDocumentState

    data object Unsafe : PdfDocumentState
}

data class PdfViewerUiState(
    val file: VaultFile? = null,
    val document: PdfDocumentState = PdfDocumentState.Loading,
    val pages: Map<Int, PageState> = emptyMap(),
    val thumbnails: Map<Int, ImageBitmap> = emptyMap(),
    val showDetails: Boolean = false,
    val confirmDelete: Boolean = false,
    val sharing: Boolean = false,
    val closed: Boolean = false,
)

sealed interface PdfViewerEvent {
    data class Viewport(val pageWidthPx: Int, val thumbnailWidthPx: Int) : PdfViewerEvent

    /** Pages on screen, first to last. Only these and their neighbours stay rendered. */
    data class PagesVisible(val pages: IntRange) : PdfViewerEvent

    data class ThumbnailsVisible(val pages: IntRange) : PdfViewerEvent

    data object ShareTapped : PdfViewerEvent

    data object DetailsTapped : PdfViewerEvent

    data object DetailsDismissed : PdfViewerEvent

    data object DeleteTapped : PdfViewerEvent

    data object DeleteConfirmed : PdfViewerEvent

    data object DeleteDismissed : PdfViewerEvent

    data object ShareDismissed : PdfViewerEvent
}

/**
 * Pages are requested from the renderer one at a time (PdfViewerViewModel asks, the
 * renderer runs one call at a time), nearest the screen first. Scrolling on cancels
 * whatever is no longer wanted.
 */
@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val pdf: RenderedPdf,
) : ViewModel() {
    private val fileId: String? = savedStateHandle.get<String>(ViewerRouterViewModel.FILE_ID)
    private val file = MutableStateFlow<VaultFile?>(null)
    private val document = MutableStateFlow<PdfDocumentState>(PdfDocumentState.Loading)
    private val pages = MutableStateFlow<Map<Int, PageState>>(emptyMap())
    private val thumbnails = MutableStateFlow<Map<Int, ImageBitmap>>(emptyMap())
    private val overlay = MutableStateFlow(Overlay())
    private val wanted = MutableStateFlow(Wanted())

    val uiState: StateFlow<PdfViewerUiState> = combine(file, document, pages, thumbnails, overlay) {
            file,
            document,
            pages,
            thumbnails,
            overlay,
        ->
        PdfViewerUiState(
            file = file,
            document = document,
            pages = pages,
            thumbnails = thumbnails,
            showDetails = overlay.showDetails,
            confirmDelete = overlay.confirmDelete,
            sharing = overlay.sharing,
            closed = overlay.closed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PdfViewerUiState())

    init {
        viewModelScope.launch {
            val found = fileId?.let { repository.file(it).first() }
            file.value = found
            if (found == null) {
                document.value = PdfDocumentState.Unreadable
                return@launch
            }
            document.value = when (val opened = pdf.open(found)) {
                is OpenedPdf.Ready -> PdfDocumentState.Ready(opened.pageCount)
                OpenedPdf.Unreadable -> PdfDocumentState.Unreadable
                OpenedPdf.Unsafe -> PdfDocumentState.Unsafe
            }
            if (document.value is PdfDocumentState.Ready) renderWanted(found)
        }
    }

    fun onEvent(event: PdfViewerEvent) {
        when (event) {
            is PdfViewerEvent.Viewport -> wanted.update {
                it.copy(pageWidth = event.pageWidthPx, thumbnailWidth = event.thumbnailWidthPx)
            }

            is PdfViewerEvent.PagesVisible -> onPagesVisible(event.pages)

            is PdfViewerEvent.ThumbnailsVisible -> wanted.update { it.copy(thumbnails = event.pages.toList()) }

            PdfViewerEvent.ShareTapped -> overlay.update { it.copy(sharing = true, showDetails = false) }

            PdfViewerEvent.DetailsTapped -> overlay.update { it.copy(showDetails = true) }

            PdfViewerEvent.DetailsDismissed -> overlay.update { it.copy(showDetails = false) }

            PdfViewerEvent.DeleteTapped -> overlay.update { it.copy(confirmDelete = true) }

            PdfViewerEvent.DeleteConfirmed -> delete()

            PdfViewerEvent.DeleteDismissed -> overlay.update { it.copy(confirmDelete = false) }

            PdfViewerEvent.ShareDismissed -> overlay.update { it.copy(sharing = false) }
        }
    }

    /** Visible pages first, then one either side; everything further away is let go. */
    private fun onPagesVisible(visible: IntRange) {
        val count = (document.value as? PdfDocumentState.Ready)?.pageCount ?: return
        val keep = (visible.first - MARGIN).coerceAtLeast(0)..(visible.last + MARGIN).coerceAtMost(count - 1)
        pages.update { current -> current.filterKeys { it in keep } }
        val order = visible.toList() + listOf(visible.last + 1, visible.first - 1).filter { it in keep }
        wanted.update { it.copy(pages = order) }
    }

    private suspend fun renderWanted(file: VaultFile) {
        wanted.collectLatest { want ->
            if (want.pageWidth == 0) return@collectLatest
            for (index in want.pages) {
                // Loading means an earlier pass was cancelled mid-page: render it again.
                val current = pages.value[index]
                if (current != null && current != PageState.Loading) continue
                pages.update { it + (index to PageState.Loading) }
                val state = pdf.page(file, index, want.pageWidth).toPageState()
                pages.update { it + (index to state) }
                if (state == PageState.Unsafe) {
                    document.value = PdfDocumentState.Unsafe
                    return@collectLatest
                }
            }
            want.thumbnails.filterNot { it in thumbnails.value }.forEach { index ->
                val result = pdf.page(file, index, want.thumbnailWidth)
                if (result is RenderResult.Ready) thumbnails.update { it + (index to result.image) }
            }
        }
    }

    private fun RenderResult.toPageState(): PageState = when (this) {
        is RenderResult.Ready -> PageState.Ready(image)
        RenderResult.NotDecodable -> PageState.NotDecodable
        RenderResult.Unsafe -> PageState.Unsafe
    }

    private fun delete() {
        val id = file.value?.id ?: return
        overlay.update { it.copy(confirmDelete = false, showDetails = false, closed = true) }
        viewModelScope.launch { repository.delete(id) }
    }

    private data class Wanted(
        val pageWidth: Int = 0,
        val thumbnailWidth: Int = 0,
        val pages: List<Int> = emptyList(),
        val thumbnails: List<Int> = emptyList(),
    )

    private data class Overlay(
        val showDetails: Boolean = false,
        val confirmDelete: Boolean = false,
        val sharing: Boolean = false,
        val closed: Boolean = false,
    )

    private companion object {
        const val MARGIN = 2
    }
}
