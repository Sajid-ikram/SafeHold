package dev.ikram.sandbox.feature.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.FileTypeIcon
import dev.ikram.sandbox.core.designsystem.component.FileTypeIconSize
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.ShareMode
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.copy.CopyFailure
import dev.ikram.sandbox.feature.vault.components.BadgeTone
import dev.ikram.sandbox.feature.vault.components.StatusBadge
import dev.ikram.sandbox.util.Formatters

/** The share flow for [fileId], as a bottom sheet over whatever screen asked for it. */
@Composable
fun ShareSheet(
    fileId: String,
    onDismiss: () -> Unit,
    viewModel: ShareViewModel = hiltViewModel(key = "share:$fileId"),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(fileId) { viewModel.onEvent(ShareEvent.Opened(fileId)) }
    LaunchedEffect(state.stage) {
        when (val stage = state.stage) {
            is ShareStage.Ready -> {
                ShareLauncher.launch(context, stage.target)
                viewModel.onEvent(ShareEvent.Launched)
            }

            ShareStage.Done -> onDismiss()

            else -> Unit
        }
    }
    ShareSheetContent(
        state = state,
        onEvent = viewModel::onEvent,
        onDismiss = {
            viewModel.onEvent(ShareEvent.Dismissed)
            onDismiss()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheetContent(state: ShareUiState, onEvent: (ShareEvent) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val file = state.file
            when (val stage = state.stage) {
                ShareStage.Loading, ShareStage.Done, is ShareStage.Ready -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is ShareStage.Blocked -> if (file != null) Blocked(file, stage.reason, onDismiss)

                ShareStage.Choosing -> if (file != null) Choosing(file, state, onEvent)

                is ShareStage.Preparing -> if (file != null) Preparing(file, stage, onEvent)

                is ShareStage.Failed -> Failed(stage.reason, onEvent, onDismiss)
            }
        }
    }
    if (state.confirmOriginal) {
        state.file?.let { ConfirmOriginal(it, onEvent) }
    }
}

@Composable
private fun ColumnScope.Choosing(file: VaultFile, state: ShareUiState, onEvent: (ShareEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FileTypeIcon(category = file.category, size = FileTypeIconSize.Small)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge, fontSize = 20.sp)
            Text(
                text = stringResource(R.string.share_subtitle, file.displayName, Formatters.size(file.sizeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    listOf(ShareMode.SAFE_COPY, ShareMode.ORIGINAL, ShareMode.LINK).forEach { mode ->
        ShareOption(
            mode = mode,
            category = file.category,
            selected = state.selected == mode,
            available = mode in state.available,
            onSelect = { onEvent(ShareEvent.Selected(mode)) },
        )
    }
    Button(
        onClick = { onEvent(ShareEvent.ShareTapped) },
        enabled = state.selected in state.available,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(52.dp),
    ) {
        Text(stringResource(buttonFor(state.selected)))
    }
}

@Composable
private fun ShareOption(
    mode: ShareMode,
    category: FileCategory,
    selected: Boolean,
    available: Boolean,
    onSelect: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (available) 1f else UNAVAILABLE_ALPHA)
            .background(if (selected && available) colors.secondaryContainer else colors.surfaceContainerLow, shape)
            .border(1.dp, if (selected && available) colors.primary else colors.outlineVariant, shape)
            .selectable(selected = selected, enabled = available, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadioButton(
            selected = selected && available,
            onClick = null,
            enabled = available,
            modifier = Modifier.size(48.dp),
        )
        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(titleFor(mode)), style = MaterialTheme.typography.titleSmall)
                if (mode == ShareMode.SAFE_COPY && available) {
                    StatusBadge(stringResource(R.string.share_recommended), SandboxIcons.ShieldCheck, BadgeTone.SAFE)
                }
            }
            when {
                !available -> Text(
                    text = stringResource(R.string.share_not_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                mode == ShareMode.ORIGINAL -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val caution = SandboxTheme.semanticColors.caution
                    Icon(
                        SandboxIcons.Warning,
                        contentDescription = null,
                        tint = caution,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.share_original_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = caution,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                else -> Text(
                    text = stringResource(
                        if (mode ==
                            ShareMode.LINK
                        ) {
                            R.string.share_link_body
                        } else {
                            safeCopyBody(category)
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Preparing(file: VaultFile, stage: ShareStage.Preparing, onEvent: (ShareEvent) -> Unit) {
    val safeCopy = stage.mode == ShareMode.SAFE_COPY
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(stage.fraction)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    if (safeCopy) R.string.share_preparing_safe_copy else R.string.share_preparing_original,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontSize = 20.sp,
            )
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            stage.secondsLeft?.let { seconds ->
                Text(
                    text = pluralStringResource(R.plurals.share_time_left, seconds, seconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (safeCopy) Steps(file.category, stage.fraction)
    Text(
        text = stringResource(if (safeCopy) R.string.share_original_stays else R.string.share_original_copying),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { onEvent(ShareEvent.CancelTapped) },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) { Text(stringResource(R.string.share_cancel)) }
}

/** The design's ring: progress in primary on a quiet track, the percentage inside. */
@Composable
private fun ProgressRing(fraction: Float?) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        if (fraction == null) {
            CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 6.dp)
        } else {
            Canvas(Modifier.size(58.dp)) {
                val stroke = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                // Inset by half the stroke so the ring is not clipped at the edge.
                val inset = stroke.width / 2
                val topLeft = Offset(inset, inset)
                val ring = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(colors.surfaceContainerHighest, 0f, FULL_TURN, false, topLeft, ring, style = stroke)
                drawArc(colors.primary, START_ANGLE, FULL_TURN * fraction, false, topLeft, ring, style = stroke)
            }
            Text(
                text = stringResource(R.string.share_percent, (fraction * PERCENT).toInt()),
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun Steps(category: FileCategory, fraction: Float?) {
    val current = when {
        fraction == null || fraction < READ_DONE -> 0
        fraction < REBUILD_DONE -> 1
        else -> 2
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        stepsFor(category).forEachIndexed { index, label ->
            Step(stringResource(label), done = index < current, active = index == current)
        }
    }
}

@Composable
private fun Step(label: String, done: Boolean, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .then(
                    when {
                        done -> Modifier.background(colors.primary, CircleShape)
                        active -> Modifier.border(2.dp, colors.primary, CircleShape)
                        else -> Modifier.border(2.dp, colors.outlineVariant, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(
                    SandboxIcons.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(14.dp),
                )

                active -> Box(Modifier.size(10.dp).background(colors.primary, CircleShape))
            }
        }
        Text(
            text = label,
            style = if (active) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (done || active) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.Blocked(file: VaultFile, reason: ShareBlockReason, onDismiss: () -> Unit) {
    val semantic = SandboxTheme.semanticColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(semantic.blockedContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SandboxIcons.Lock, contentDescription = null, tint = semantic.onBlockedContainer)
        }
        Text(
            stringResource(R.string.share_blocked_title),
            style = MaterialTheme.typography.titleLarge,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(blockedBody(reason), file.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    listOf(ShareMode.SAFE_COPY, ShareMode.ORIGINAL, ShareMode.LINK).forEach { mode ->
        ShareOption(mode, file.category, selected = false, available = false, onSelect = {})
    }
    Button(
        onClick = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(52.dp),
    ) { Text(stringResource(R.string.share_got_it)) }
}

@Composable
private fun ColumnScope.Failed(reason: CopyFailure, onEvent: (ShareEvent) -> Unit, onDismiss: () -> Unit) {
    val semantic = SandboxTheme.semanticColors
    val unsafe = reason == CopyFailure.UNSAFE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(if (unsafe) semantic.blockedContainer else semantic.cautionContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SandboxIcons.Warning,
                contentDescription = null,
                tint = if (unsafe) semantic.onBlockedContainer else semantic.onCautionContainer,
            )
        }
        Text(
            stringResource(failureTitle(reason)),
            style = MaterialTheme.typography.titleLarge,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(failureBody(reason)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!unsafe) {
            OutlinedButton(
                onClick = { onEvent(ShareEvent.TryAnotherWay) },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) { Text(stringResource(R.string.share_try_another_way)) }
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) { Text(stringResource(R.string.share_close)) }
    }
}

/** The amber warning in front of every original share. */
@Composable
private fun ConfirmOriginal(file: VaultFile, onEvent: (ShareEvent) -> Unit) {
    val semantic = SandboxTheme.semanticColors
    AlertDialog(
        onDismissRequest = { onEvent(ShareEvent.OriginalDismissed) },
        icon = { Icon(SandboxIcons.Warning, contentDescription = null, tint = semantic.caution) },
        title = { Text(stringResource(R.string.share_original_confirm_title), textAlign = TextAlign.Center) },
        text = { Text(stringResource(R.string.share_original_confirm_body, file.displayName)) },
        confirmButton = {
            TextButton(onClick = { onEvent(ShareEvent.OriginalConfirmed) }) {
                Text(stringResource(R.string.share_original_confirm), color = semantic.caution)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ShareEvent.OriginalDismissed) }) {
                Text(stringResource(R.string.share_cancel))
            }
        },
    )
}

private fun titleFor(mode: ShareMode): Int = when (mode) {
    ShareMode.SAFE_COPY -> R.string.share_safe_copy
    ShareMode.ORIGINAL -> R.string.share_original
    ShareMode.LINK -> R.string.share_link
}

private fun buttonFor(mode: ShareMode): Int = when (mode) {
    ShareMode.SAFE_COPY -> R.string.share_safe_copy
    ShareMode.ORIGINAL -> R.string.share_original
    ShareMode.LINK -> R.string.share_link_button
}

private fun safeCopyBody(category: FileCategory): Int = when (category) {
    FileCategory.IMAGE -> R.string.share_safe_copy_image
    FileCategory.PDF -> R.string.share_safe_copy_pdf
    FileCategory.VIDEO -> R.string.share_safe_copy_video
    FileCategory.AUDIO -> R.string.share_safe_copy_audio
    FileCategory.TEXT -> R.string.share_safe_copy_text
    else -> R.string.share_safe_copy_unavailable
}

private fun stepsFor(category: FileCategory): List<Int> = when (category) {
    FileCategory.IMAGE -> listOf(R.string.share_step_read_image, R.string.share_step_rebuild_image)
    FileCategory.PDF -> listOf(R.string.share_step_read_pdf, R.string.share_step_rebuild_pdf)
    FileCategory.VIDEO -> listOf(R.string.share_step_read_video, R.string.share_step_rebuild_video)
    FileCategory.AUDIO -> listOf(R.string.share_step_read_audio, R.string.share_step_rebuild_audio)
    else -> listOf(R.string.share_step_read_text, R.string.share_step_rebuild_text)
} + R.string.share_step_hidden_details

private fun blockedBody(reason: ShareBlockReason): Int = when (reason) {
    ShareBlockReason.APP_INSTALLER -> R.string.share_blocked_app
    ShareBlockReason.PROGRAM -> R.string.share_blocked_program
    ShareBlockReason.UNKNOWN -> R.string.share_blocked_unknown
    ShareBlockReason.OPEN_FAILED -> R.string.share_blocked_open_failed
}

private fun failureTitle(reason: CopyFailure): Int = when (reason) {
    CopyFailure.UNSAFE -> R.string.viewer_unsafe_title
    CopyFailure.UNREADABLE -> R.string.share_failed_unreadable_title
    CopyFailure.TOO_LARGE -> R.string.share_failed_too_large_title
    CopyFailure.NOT_ALLOWED, CopyFailure.FAILED -> R.string.share_failed_title
}

private fun failureBody(reason: CopyFailure): Int = when (reason) {
    CopyFailure.UNSAFE -> R.string.viewer_unsafe_message
    CopyFailure.UNREADABLE -> R.string.share_failed_unreadable_body
    CopyFailure.TOO_LARGE -> R.string.share_failed_too_large_body
    CopyFailure.NOT_ALLOWED, CopyFailure.FAILED -> R.string.share_failed_body
}

private const val UNAVAILABLE_ALPHA = 0.5f
private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val PERCENT = 100
private const val READ_DONE = 0.05f
private const val REBUILD_DONE = 0.98f
