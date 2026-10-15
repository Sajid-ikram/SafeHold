package dev.ikram.sandbox.feature.viewer.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import dev.ikram.sandbox.util.Formatters

/**
 * A seek area drawn by [draw] with the fraction played so far. Tap or drag to seek;
 * while dragging, the drawing follows the finger and the player seeks on release.
 */
@Composable
fun Scrubber(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    draw: DrawScope.(played: Float) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration by rememberUpdatedState(durationMs)
    val seek by rememberUpdatedState(onSeek)
    val enabled = durationMs > 0
    val played = dragging ?: if (enabled) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    fun seekTo(fraction: Float) = seek((fraction.coerceIn(0f, 1f) * duration).toLong())

    Canvas(
        modifier = modifier
            .semantics {
                this.contentDescription = contentDescription
                stateDescription = "${Formatters.duration(positionMs)} / ${Formatters.duration(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        seekTo(target)
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { offset -> seekTo(offset.x / size.width) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragging = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragging?.let(::seekTo)
                        dragging = null
                    },
                    onDragCancel = { dragging = null },
                    onHorizontalDrag = { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
    ) { draw(played) }
}
