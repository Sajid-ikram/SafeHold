package dev.ikram.sandbox.feature.viewer.media

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.components.BadgeTone
import dev.ikram.sandbox.feature.vault.components.FileStatusBadge
import dev.ikram.sandbox.feature.vault.components.StatusBadge
import dev.ikram.sandbox.util.Formatters
import kotlinx.coroutines.delay

/** On black, whatever the theme: the design's video controls. */
private val ControlColor = Color.White
private val ControlScrim = Color.Black.copy(alpha = 0.5f)
private val PictureDim = Color.Black.copy(alpha = 0.3f)
private val TrackColor = Color.White.copy(alpha = 0.3f)
private val PlayedColor = Color(0xFF83D5C7)
private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val VIDEO_RATIO = 16f / 9f

@Composable
fun VideoPlayerScreen(
    state: MediaUiState,
    onEvent: (MediaEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = state.file ?: return
    KeepScreenOn(state.isPlaying)
    FullscreenWindow(enabled = state.fullscreen, landscape = !state.isPortraitVideo)
    BackHandler(enabled = state.fullscreen) { onEvent(MediaEvent.ToggleFullscreen) }

    if (state.fullscreen) {
        VideoFrame(state, onEvent, Modifier.fillMaxSize())
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        MediaTopBar(file, onBack, onEvent)
        if (state.status == PlaybackStatus.FAILED) {
            UnplayableMessage()
            return@Column
        }
        VideoFrame(
            state,
            onEvent,
            Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_RATIO),
        )
        VideoDetails(file, state, onEvent)
    }
}

@Composable
private fun VideoDetails(file: VaultFile, state: MediaUiState, onEvent: (MediaEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(top = 20.dp, bottom = 24.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(file.displayName, style = MaterialTheme.typography.titleLarge, fontSize = 20.sp)
            Text(
                text = stringResource(
                    R.string.media_video_meta,
                    Formatters.size(file.sizeBytes),
                    file.sourceHost,
                    savedText(state.savedOn, state.today),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileStatusBadge(file)
                val quality = if (state.videoLines > 0) {
                    stringResource(R.string.media_video_quality, state.videoLines)
                } else {
                    stringResource(R.string.media_video)
                }
                StatusBadge(quality, SandboxIcons.Video, BadgeTone.NEUTRAL)
            }
        }
        PictureAndSoundNote(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp))
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { onEvent(MediaEvent.ShareTapped) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(SandboxIcons.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.media_share))
            }
            OutlinedButton(
                onClick = { onEvent(MediaEvent.DetailsTapped) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) {
                Icon(SandboxIcons.Info, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.media_details))
            }
        }
    }
}

@Composable
private fun PictureAndSoundNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            SandboxIcons.ShieldCheck,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.media_picture_and_sound_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The picture, with controls on top that hide themselves while it plays. Tap to bring them back. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoFrame(state: MediaUiState, onEvent: (MediaEvent) -> Unit, modifier: Modifier) {
    var controlsShown by remember { mutableStateOf(true) }
    // Every tap on a control restarts the countdown to hiding them.
    var interactions by remember { mutableIntStateOf(0) }
    val onControl: (MediaEvent) -> Unit = { event ->
        interactions++
        onEvent(event)
    }
    LaunchedEffect(controlsShown, state.isPlaying, interactions) {
        if (controlsShown && state.isPlaying) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsShown = false
        }
    }
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(interactionSource = null, indication = null) { controlsShown = !controlsShown },
    ) {
        ContentFrame(player = state.player)
        if (state.status == PlaybackStatus.PREPARING) {
            CircularProgressIndicator(color = ControlColor, modifier = Modifier.align(Alignment.Center))
        }
        AnimatedVisibility(
            visible = controlsShown || !state.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            // A light dimming keeps white controls readable over bright pictures.
            Box(Modifier.fillMaxSize().background(PictureDim)) { VideoControls(state, onControl) }
        }
    }
}

@Composable
private fun BoxScope.VideoControls(state: MediaUiState, onEvent: (MediaEvent) -> Unit) {
    if (state.status != PlaybackStatus.PREPARING) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(SandboxIcons.SeekBack, R.string.media_seek_back, size = 44) {
                onEvent(MediaEvent.SeekBack)
            }
            ControlButton(
                icon = if (state.isPlaying) SandboxIcons.Pause else SandboxIcons.Play,
                label = if (state.isPlaying) R.string.media_pause else R.string.media_play,
                size = 64,
                background = ControlScrim,
            ) { onEvent(MediaEvent.PlayPause) }
            ControlButton(SandboxIcons.SeekForward, R.string.media_seek_forward, size = 44) {
                onEvent(MediaEvent.SeekForward)
            }
        }
    }
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    R.string.media_time,
                    Formatters.duration(state.positionMs),
                    Formatters.duration(state.durationMs),
                ),
                color = ControlColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                icon = if (state.fullscreen) SandboxIcons.ExitFullscreen else SandboxIcons.Fullscreen,
                label = if (state.fullscreen) R.string.media_exit_fullscreen else R.string.media_fullscreen,
                size = 40,
            ) { onEvent(MediaEvent.ToggleFullscreen) }
        }
        VideoSeekBar(state, onEvent)
    }
}

@Composable
private fun VideoSeekBar(state: MediaUiState, onEvent: (MediaEvent) -> Unit) {
    Scrubber(
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        onSeek = { onEvent(MediaEvent.SeekTo(it)) },
        contentDescription = stringResource(R.string.media_position),
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
    ) { played ->
        val track = 4.dp.toPx()
        val thumb = 7.dp.toPx()
        val top = (size.height - track) / 2
        val radius = CornerRadius(track / 2)
        drawRoundRect(TrackColor, Offset(0f, top), Size(size.width, track), radius)
        drawRoundRect(PlayedColor, Offset(0f, top), Size(size.width * played, track), radius)
        drawCircle(PlayedColor, thumb, Offset(size.width * played, size.height / 2))
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: Int,
    size: Int,
    background: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size.dp)
            .background(background, CircleShape),
    ) {
        Icon(icon, stringResource(label), tint = ControlColor, modifier = Modifier.size((size / 2).dp))
    }
}

/** No dimming or sleeping while a video plays. A view flag, so no wake lock permission is needed. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Hides the system bars and, for a wide picture, turns the screen sideways. Undone on exit. */
@Composable
private fun FullscreenWindow(enabled: Boolean, landscape: Boolean) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, enabled, landscape) {
        if (!enabled) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val orientation = activity.requestedOrientation
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        if (landscape) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = orientation
        }
    }
}
