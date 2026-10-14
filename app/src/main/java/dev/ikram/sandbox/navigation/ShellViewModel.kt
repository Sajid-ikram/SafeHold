package dev.ikram.sandbox.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.data.download.NewFilesCounter
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** State for the app chrome around the screens: the bottom bar's vault badge. */
@HiltViewModel
class ShellViewModel @Inject constructor(newFiles: NewFilesCounter) : ViewModel() {
    val newFiles: StateFlow<Int> = newFiles.count
}
