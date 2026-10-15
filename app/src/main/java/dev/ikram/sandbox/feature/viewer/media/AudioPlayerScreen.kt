package dev.ikram.sandbox.feature.viewer.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.util.Formatters

private const val BAR_COUNT = 40
private const val MIN_BAR = 0.25f
private const val ARTWORK_SIZE = 300f
private const val HEX = 16
private const val BYTE_MAX = 0xFF
private const val SPREAD = 37
private val OUTER_SLOT = 72.dp

@Composable
fun AudioPlayerScreen(
    state: MediaUiState,
    onEvent: (MediaEvent) -> Unit,
    onBack: () -> Unit,
    onVolume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val file = state.file ?: return
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.Bottom),
        ) {
            Artwork(
                Modifier
                    .weight(1f, fill = false)
                    .align(Alignment.CenterHorizontally)
                    .sizeIn(maxWidth = ARTWORK_SIZE.dp, maxHeight = ARTWORK_SIZE.dp)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    file.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.media_audio_meta, file.sourceHost, Formatters.size(file.sizeBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Progress(file, state, onEvent)
            Controls(state, onEvent, onVolume)
        }
    }
}

/** The design's artwork: the audio note on two soft rings, scaled with the square. */
@Composable
private fun Artwork(modifier: Modifier) {
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.width / ARTWORK_SIZE
            drawCircle(
                color = ring.copy(alpha = 0.14f),
                radius = 160 * unit,
                center = Offset(30 * unit, 260 * unit),
                style = Stroke(40 * unit),
            )
            drawCircle(
                color = ring.copy(alpha = 0.10f),
                radius = 86 * unit,
                center = Offset(270 * unit, 40 * unit),
                style = Stroke(28 * unit),
            )
        }
        Icon(
            SandboxIcons.Audio,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp),
        )
    }
}

@Composable
private fun Progress(file: VaultFile, state: MediaUiState, onEvent: (MediaEvent) -> Unit) {
    val bars = remember(file.sha256) { waveform(file.sha256) }
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Scrubber(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = { onEvent(MediaEvent.SeekTo(it)) },
            contentDescription = stringResource(R.string.media_position),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) { fraction ->
            val gap = 2.dp.toPx()
            val width = (size.width - gap * (bars.size - 1)) / bars.size
            bars.forEachIndexed { index, height ->
                val barHeight = size.height * height
                drawRoundRect(
                    color = if ((index + 0.5f) / bars.size <= fraction) played else rest,
                    topLeft = Offset(index * (width + gap), (size.height - barHeight) / 2),
                    size = Size(width, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
        Row {
            Text(
                Formatters.duration(state.positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                Formatters.duration(state.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Controls(state: MediaUiState, onEvent: (MediaEvent) -> Unit, onVolume: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val speedLabel = stringResource(R.string.media_speed, Formatters.speed(state.speed))
        // The outer slots share one width, so the play button stays centred as the speed label changes.
        Box(modifier = Modifier.width(OUTER_SLOT), contentAlignment = Alignment.CenterStart) {
            SuggestionChip(
                onClick = { onEvent(MediaEvent.CycleSpeed) },
                label = { Text(Formatters.speed(state.speed)) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .height(36.dp)
                    .semantics { contentDescription = speedLabel },
            )
        }
        IconButton(onClick = { onEvent(MediaEvent.SeekBack) }) {
            Icon(SandboxIcons.SeekBack, stringResource(R.string.media_seek_back), modifier = Modifier.size(32.dp))
        }
        val playLabel = stringResource(if (state.isPlaying) R.string.media_pause else R.string.media_play)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.primary)
                .clickable(role = Role.Button, onClickLabel = playLabel) { onEvent(MediaEvent.PlayPause) }
                .semantics { contentDescription = playLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (state.isPlaying) SandboxIcons.Pause else SandboxIcons.Play,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { onEvent(MediaEvent.SeekForward) }) {
            Icon(SandboxIcons.SeekForward, stringResource(R.string.media_seek_forward), modifier = Modifier.size(32.dp))
        }
        Box(modifier = Modifier.width(OUTER_SLOT), contentAlignment = Alignment.CenterEnd) {
            IconButton(onClick = onVolume) {
                Icon(SandboxIcons.Volume, stringResource(R.string.media_volume))
            }
        }
    }
}

/**
 * Bar heights for the progress strip, from the file's hash: the same file always gets the
 * same shape. Decorative only; the audio itself is never analysed.
 */
internal fun waveform(sha256: String): List<Float> {
    val bytes = sha256.chunked(2).mapNotNull { it.toIntOrNull(HEX) }.ifEmpty { listOf(BYTE_MAX / 2) }
    return List(BAR_COUNT) { index ->
        // Mixing in the index keeps neighbouring bars from repeating the hash's pattern.
        val value = bytes[index % bytes.size] xor (index * SPREAD and BYTE_MAX)
        MIN_BAR + (1 - MIN_BAR) * value / BYTE_MAX.toFloat()
    }
}
