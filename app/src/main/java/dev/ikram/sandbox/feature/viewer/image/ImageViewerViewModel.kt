package dev.ikram.sandbox.feature.viewer.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.viewer.ViewerRouterViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PageState {
    data object Loading : PageState

    data class Ready(val image: ImageBitmap) : PageState

    data object NotDecodable : PageState

    data object Unsafe : PageState
}

sealed interface ImageViewerMessage {
    data object ShareNotReady : ImageViewerMessage

    data class Deleted(val name: String) : ImageViewerMessage
}

data class ImageViewerUiState(
    /** Every image in the vault, newest first: swiping moves through them. */
    val images: List<VaultFile> = emptyList(),
    val startIndex: Int = 0,
    val pages: Map<String, PageState> = emptyMap(),
    val thumbnails: Map<String, ImageBitmap> = emptyMap(),
    val details: VaultFile? = null,
    val confirmDelete: VaultFile? = null,
    val message: ImageViewerMessage? = null,
    /** Nothing left to show (the last image was deleted). */
    val closed: Boolean = false,
)

sealed interface ImageViewerEvent {
    /** The viewer's longest side in pixels, so images decode sharp enough to zoom. */
    data class Viewport(val longestSidePx: Int) : ImageViewerEvent

    data class PageShown(val fileId: String) : ImageViewerEvent

    data object ShareTapped : ImageViewerEvent

    data class DetailsTapped(val fileId: String) : ImageViewerEvent

    data object DetailsDismissed : ImageViewerEvent

    data class DeleteTapped(val fileId: String) : ImageViewerEvent

    data object DeleteConfirmed : ImageViewerEvent

    data object DeleteDismissed : ImageViewerEvent

    data object MessageShown : ImageViewerEvent
}

@HiltViewModel
class ImageViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val renderer: RenderedImages,
) : ViewModel() {
    private val startId: String? = savedStateHandle.get<String>(ViewerRouterViewModel.FILE_ID)
    private var startIndex: Int? = null
    private var maxSide = DEFAULT_SIDE

    private val images: StateFlow<List<VaultFile>?> = repository.files
        .map { files -> files.filter { it.category == FileCategory.IMAGE } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val pages = MutableStateFlow<Map<String, PageState>>(emptyMap())
    private val thumbnails = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val overlay = MutableStateFlow(Overlay())

    val uiState: StateFlow<ImageViewerUiState> = combine(images, pages, thumbnails, overlay) {
            images,
            pages,
            thumbnails,
            overlay,
        ->
        if (images == null) return@combine ImageViewerUiState()
        val start = startIndex ?: images.indexOfFirst { it.id == startId }.coerceAtLeast(0).also { startIndex = it }
        fun byId(id: String?) = id?.let { wanted -> images.firstOrNull { it.id == wanted } }
        ImageViewerUiState(
            images = images,
            startIndex = start,
            pages = pages,
            thumbnails = thumbnails,
            details = byId(overlay.detailsId),
            confirmDelete = byId(overlay.deleteId),
            message = overlay.message,
            closed = images.isEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ImageViewerUiState())

    init {
        viewModelScope.launch {
            images.collect { list -> list?.let { loadThumbnails(it) } }
        }
    }

    fun onEvent(event: ImageViewerEvent) {
        when (event) {
            is ImageViewerEvent.Viewport ->
                maxSide =
                    (event.longestSidePx * ZOOM_HEADROOM).toInt().coerceIn(MIN_SIDE, MAX_SIDE)

            is ImageViewerEvent.PageShown -> onPageShown(event.fileId)

            ImageViewerEvent.ShareTapped -> overlay.update { it.copy(message = ImageViewerMessage.ShareNotReady) }

            is ImageViewerEvent.DetailsTapped -> overlay.update { it.copy(detailsId = event.fileId) }

            ImageViewerEvent.DetailsDismissed -> overlay.update { it.copy(detailsId = null) }

            is ImageViewerEvent.DeleteTapped -> overlay.update { it.copy(deleteId = event.fileId) }

            ImageViewerEvent.DeleteConfirmed -> delete()

            ImageViewerEvent.DeleteDismissed -> overlay.update { it.copy(deleteId = null) }

            ImageViewerEvent.MessageShown -> overlay.update { it.copy(message = null) }
        }
    }

    /** Keeps the shown image and its neighbours decoded; lets go of the rest to bound memory. */
    private fun onPageShown(fileId: String) {
        val list = images.value ?: return
        val index = list.indexOfFirst { it.id == fileId }.takeIf { it >= 0 } ?: return
        val window = list.subList(maxOf(0, index - 1), minOf(list.size, index + 2))
        val keep = window.map { it.id }.toSet()
        pages.update { current -> current.filter { (id, state) -> id in keep || state !is PageState.Ready } }
        window.forEach(::load)
    }

    private fun load(file: VaultFile) {
        val current = pages.value[file.id]
        if (current != null) return
        pages.update { it + (file.id to PageState.Loading) }
        viewModelScope.launch {
            val state = when (val result = renderer.full(file, maxSide)) {
                is RenderResult.Ready -> PageState.Ready(result.image)
                RenderResult.NotDecodable -> if (file.openFailed) PageState.Unsafe else PageState.NotDecodable
                RenderResult.Unsafe -> PageState.Unsafe
            }
            pages.update { it + (file.id to state) }
        }
    }

    private suspend fun loadThumbnails(list: List<VaultFile>) {
        list.filterNot { it.id in thumbnails.value }.forEach { file ->
            renderer.thumbnail(file)?.let { image -> thumbnails.update { it + (file.id to image) } }
        }
    }

    private fun delete() {
        val file = overlay.value.deleteId?.let { id -> images.value?.firstOrNull { it.id == id } } ?: return
        overlay.update {
            it.copy(deleteId = null, detailsId = null, message = ImageViewerMessage.Deleted(file.displayName))
        }
        pages.update { it - file.id }
        viewModelScope.launch { repository.delete(file.id) }
    }

    private data class Overlay(
        val detailsId: String? = null,
        val deleteId: String? = null,
        val message: ImageViewerMessage? = null,
    )

    private companion object {
        const val DEFAULT_SIDE = 2048
        const val MIN_SIDE = 1024
        const val MAX_SIDE = 3072

        /** Decode a little larger than the screen so a zoomed image stays sharp. */
        const val ZOOM_HEADROOM = 1.5f
    }
}
