package dev.ikram.sandbox.feature.viewer.text

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.di.DefaultDispatcher
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.viewer.ViewerRouterViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TextContent {
    data object Loading : TextContent

    data class Ready(val document: TextDocument) : TextContent

    data object Unreadable : TextContent
}

data class FindState(
    val open: Boolean = false,
    val query: String = "",
    val matches: List<TextMatch> = emptyList(),
    /** Index into [matches], or -1 when there are none. */
    val current: Int = -1,
) {
    val capped: Boolean get() = matches.size >= TextSearch.MAX_MATCHES
}

enum class TextViewerMessage { SHARE_NOT_READY }

data class TextViewerUiState(
    val file: VaultFile? = null,
    val content: TextContent = TextContent.Loading,
    val wrap: Boolean = true,
    val lineNumbers: Boolean = true,
    val fontSizeSp: Int = TextViewerViewModel.DEFAULT_FONT_SP,
    val find: FindState = FindState(),
    val showDetails: Boolean = false,
    val confirmDelete: Boolean = false,
    val message: TextViewerMessage? = null,
    val closed: Boolean = false,
)

sealed interface TextViewerEvent {
    data object ToggleWrap : TextViewerEvent

    data object ToggleLineNumbers : TextViewerEvent

    data object SmallerText : TextViewerEvent

    data object LargerText : TextViewerEvent

    data object FindOpened : TextViewerEvent

    data object FindClosed : TextViewerEvent

    data class QueryChanged(val query: String) : TextViewerEvent

    data object NextMatch : TextViewerEvent

    data object PreviousMatch : TextViewerEvent

    data object ShareTapped : TextViewerEvent

    data object DetailsTapped : TextViewerEvent

    data object DetailsDismissed : TextViewerEvent

    data object DeleteTapped : TextViewerEvent

    data object DeleteConfirmed : TextViewerEvent

    data object DeleteDismissed : TextViewerEvent

    data object MessageShown : TextViewerEvent
}

@HiltViewModel
class TextViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VaultRepository,
    private val reader: TextFileReader,
    @param:DefaultDispatcher private val compute: CoroutineDispatcher,
) : ViewModel() {
    private val fileId: String? = savedStateHandle.get<String>(ViewerRouterViewModel.FILE_ID)
    private val state = MutableStateFlow(TextViewerUiState())
    val uiState: StateFlow<TextViewerUiState> = state.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            val file = fileId?.let { repository.file(it).first() }
            state.update { it.copy(file = file) }
            val document = file?.takeIf { it.category == FileCategory.TEXT }?.let { reader.read(it) }
            state.update { it.copy(content = document?.let(TextContent::Ready) ?: TextContent.Unreadable) }
            if (document != null) search(document)
        }
    }

    fun onEvent(event: TextViewerEvent) {
        when (event) {
            TextViewerEvent.ToggleWrap -> state.update { it.copy(wrap = !it.wrap) }

            TextViewerEvent.ToggleLineNumbers -> state.update { it.copy(lineNumbers = !it.lineNumbers) }

            TextViewerEvent.SmallerText -> state.update {
                it.copy(fontSizeSp = (it.fontSizeSp - 1).coerceAtLeast(MIN_FONT_SP))
            }

            TextViewerEvent.LargerText -> state.update {
                it.copy(fontSizeSp = (it.fontSizeSp + 1).coerceAtMost(MAX_FONT_SP))
            }

            TextViewerEvent.FindOpened -> state.update { it.copy(find = it.find.copy(open = true)) }

            TextViewerEvent.FindClosed -> {
                query.value = ""
                state.update { it.copy(find = FindState()) }
            }

            is TextViewerEvent.QueryChanged -> {
                query.value = event.query
                state.update { it.copy(find = it.find.copy(query = event.query)) }
            }

            TextViewerEvent.NextMatch -> step(1)

            TextViewerEvent.PreviousMatch -> step(-1)

            TextViewerEvent.ShareTapped -> state.update { it.copy(message = TextViewerMessage.SHARE_NOT_READY) }

            TextViewerEvent.DetailsTapped -> state.update { it.copy(showDetails = true) }

            TextViewerEvent.DetailsDismissed -> state.update { it.copy(showDetails = false) }

            TextViewerEvent.DeleteTapped -> state.update { it.copy(confirmDelete = true) }

            TextViewerEvent.DeleteConfirmed -> delete()

            TextViewerEvent.DeleteDismissed -> state.update { it.copy(confirmDelete = false) }

            TextViewerEvent.MessageShown -> state.update { it.copy(message = null) }
        }
    }

    /** Each new query cancels the search before it; a short pause lets typing settle first. */
    private suspend fun search(document: TextDocument) {
        query.collectLatest { text ->
            if (text.isEmpty()) {
                state.update { it.copy(find = it.find.copy(matches = emptyList(), current = -1)) }
                return@collectLatest
            }
            delay(SEARCH_DELAY_MS)
            val matches = withContext(compute) {
                TextSearch.find(document.text, text) { ensureActive() }
            }
            state.update {
                it.copy(find = it.find.copy(matches = matches, current = if (matches.isEmpty()) -1 else 0))
            }
        }
    }

    private fun step(by: Int) = state.update {
        val count = it.find.matches.size
        if (count == 0) it else it.copy(find = it.find.copy(current = (it.find.current + by).mod(count)))
    }

    private fun delete() {
        val id = state.value.file?.id ?: return
        state.update { it.copy(confirmDelete = false, showDetails = false, closed = true) }
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        const val DEFAULT_FONT_SP = 13
        const val MIN_FONT_SP = 9
        const val MAX_FONT_SP = 24
        const val SEARCH_DELAY_MS = 150L
    }
}
