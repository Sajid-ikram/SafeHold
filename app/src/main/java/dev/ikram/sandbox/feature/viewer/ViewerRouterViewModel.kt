package dev.ikram.sandbox.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.model.VaultFile
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface ViewerTarget {
    data object Loading : ViewerTarget

    data object Missing : ViewerTarget

    data class Found(val file: VaultFile) : ViewerTarget
}

/** Looks the file up once; which viewer to show never changes while it is open. */
@HiltViewModel
class ViewerRouterViewModel @Inject constructor(savedStateHandle: SavedStateHandle, repository: VaultRepository) :
    ViewModel() {
    private val state = MutableStateFlow<ViewerTarget>(ViewerTarget.Loading)
    val target: StateFlow<ViewerTarget> = state.asStateFlow()

    init {
        val fileId = savedStateHandle.get<String>(FILE_ID)
        viewModelScope.launch {
            val file = fileId?.let { repository.file(it).first() }
            state.value = file?.let(ViewerTarget::Found) ?: ViewerTarget.Missing
        }
    }

    companion object {
        /** The argument name of Routes.Viewer. */
        const val FILE_ID = "fileId"
    }
}
