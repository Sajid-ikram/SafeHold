package dev.ikram.sandbox.feature.viewer.media

import android.content.Context
import android.media.AudioManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.DayLabel
import dev.ikram.sandbox.feature.viewer.components.ViewerMessage
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlayActions
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlayState
import dev.ikram.sandbox.feature.viewer.components.ViewerOverlays
import dev.ikram.sandbox.feature.viewer.components.ViewerTopBar
import dev.ikram.sandbox.util.Formatters
import java.time.LocalDate

/** Video files play in the video layout, audio (and video files holding only sound) in the audio one. */
@Composable
fun MediaViewerRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: MediaViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onEvent(MediaEvent.Backgrounded) }
    if (state.showsVideo) {
        VideoPlayerScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
    } else {
        AudioPlayerScreen(
            state = state,
            onEvent = viewModel::onEvent,
            onBack = onBack,
            onVolume = { showVolumePanel(context) },
            modifier = modifier,
        )
    }
    MediaOverlays(state, viewModel::onEvent)
}

@Composable
internal fun MediaTopBar(file: VaultFile, onBack: () -> Unit, onEvent: (MediaEvent) -> Unit) {
    ViewerTopBar(
        title = file.displayName,
        onBack = onBack,
        onShare = { onEvent(MediaEvent.ShareTapped) },
        onDetails = { onEvent(MediaEvent.DetailsTapped) },
    )
}

@Composable
internal fun UnplayableMessage() {
    ViewerMessage(
        icon = SandboxIcons.Video,
        title = stringResource(R.string.media_unplayable_title),
        message = stringResource(R.string.media_unplayable_message),
        container = MaterialTheme.colorScheme.surfaceContainerHigh,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun savedText(savedOn: DayLabel, today: LocalDate): String = when (savedOn) {
    DayLabel.Today -> stringResource(R.string.media_saved_today)
    DayLabel.Yesterday -> stringResource(R.string.media_saved_yesterday)
    is DayLabel.Day -> stringResource(R.string.media_saved_on, Formatters.day(savedOn.date, today))
}

@Composable
private fun MediaOverlays(state: MediaUiState, onEvent: (MediaEvent) -> Unit) {
    ViewerOverlays(
        state = ViewerOverlayState(
            file = state.file,
            showDetails = state.showDetails,
            confirmDelete = state.confirmDelete,
            message = state.message?.let { stringResource(R.string.vault_message_share_not_ready) },
        ),
        actions = ViewerOverlayActions(
            onShare = { onEvent(MediaEvent.ShareTapped) },
            onDelete = { onEvent(MediaEvent.DeleteTapped) },
            onDeleteConfirmed = { onEvent(MediaEvent.DeleteConfirmed) },
            onDeleteDismissed = { onEvent(MediaEvent.DeleteDismissed) },
            onDetailsDismissed = { onEvent(MediaEvent.DetailsDismissed) },
            onMessageShown = { onEvent(MediaEvent.MessageShown) },
        ),
    )
}

/** The system's own volume panel, as the hardware buttons would show it. */
private fun showVolumePanel(context: Context) {
    context.getSystemService(AudioManager::class.java)
        ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
}
