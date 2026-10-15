package dev.ikram.sandbox.feature.viewer.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.ShareSheet
import dev.ikram.sandbox.feature.vault.FileDetailsSheet

/** What a viewer can show on top of a file: details, sharing, the delete confirmation, a snackbar. */
data class ViewerOverlayState(
    val file: VaultFile?,
    val showDetails: Boolean,
    val confirmDelete: Boolean,
    /** The share sheet is open. */
    val sharing: Boolean,
    /** Already resolved to text; null when there is nothing to say. */
    val message: String? = null,
)

class ViewerOverlayActions(
    val onShare: () -> Unit,
    val onShareDismissed: () -> Unit,
    val onDelete: () -> Unit,
    val onDeleteConfirmed: () -> Unit,
    val onDeleteDismissed: () -> Unit,
    val onDetailsDismissed: () -> Unit,
    val onMessageShown: () -> Unit = {},
)

@Composable
fun ViewerOverlays(state: ViewerOverlayState, actions: ViewerOverlayActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            snackbarHostState.showSnackbar(state.message)
            actions.onMessageShown()
        }
    }
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(snackbarHostState)
    }
    val file = state.file ?: return
    if (state.sharing) ShareSheet(fileId = file.id, onDismiss = actions.onShareDismissed)
    if (state.showDetails) {
        FileDetailsSheet(
            file = file,
            onOpen = {},
            onShare = actions.onShare,
            onDelete = actions.onDelete,
            onHashCopied = {},
            onDismiss = actions.onDetailsDismissed,
            showOpen = false,
        )
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = actions.onDeleteDismissed,
            title = { Text(stringResource(R.string.delete_title, file.displayName)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = actions.onDeleteConfirmed) {
                    Text(stringResource(R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onDeleteDismissed) { Text(stringResource(R.string.delete_cancel)) }
            },
        )
    }
}
