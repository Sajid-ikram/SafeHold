package dev.ikram.sandbox.feature.vault

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.FileTypeIcon
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.MonoFamily
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.filetype.NameSanitizer
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.components.BadgeTone
import dev.ikram.sandbox.feature.vault.components.FileStatusBadge
import dev.ikram.sandbox.feature.vault.components.VirusTotalRow
import dev.ikram.sandbox.feature.vault.components.noun
import dev.ikram.sandbox.util.Formatters
import kotlinx.coroutines.launch

/** Real type, size, source, date and SHA-256, with the actions the policy allows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsSheet(
    file: VaultFile,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onHashCopied: () -> Unit,
    onDismiss: () -> Unit,
    // Off inside a viewer, where the file is already open.
    showOpen: Boolean = true,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(file)
            Notice(file)
            FactsCard(file, onHashCopied)
            VirusTotalRow(file)
            Actions(file, showOpen, onOpen, onShare, onDelete)
        }
    }
}

@Composable
private fun Header(file: VaultFile) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        FileTypeIcon(category = file.category, isMismatch = file.showsMismatch)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            FileStatusBadge(file)
        }
    }
}

/** One sentence on why this file is limited, when it is. */
@Composable
private fun Notice(file: VaultFile) {
    val claimed = file.claimedType?.let(TypePolicy::categoryOf)
    val (text, tone) = when {
        file.openFailed -> stringResource(R.string.details_open_failed) to BadgeTone.BLOCKED

        file.category == FileCategory.BLOCKED -> stringResource(
            if (TypePolicy.isAppInstaller(
                    file.detectedType,
                )
            ) {
                R.string.details_blocked_app
            } else {
                R.string.details_blocked_program
            },
        ) to BadgeTone.BLOCKED

        file.category == FileCategory.UNKNOWN -> stringResource(R.string.details_unknown) to BadgeTone.BLOCKED

        file.isMismatch && claimed != null -> stringResource(
            R.string.details_mismatch,
            stringResource(claimed.noun()),
            stringResource(file.category.noun()),
        ) to BadgeTone.CAUTION

        file.category == FileCategory.ARCHIVE -> stringResource(R.string.details_archive) to BadgeTone.NEUTRAL

        else -> return
    }
    val semantic = SandboxTheme.semanticColors
    val (container, content) = when (tone) {
        BadgeTone.BLOCKED -> semantic.blockedContainer to semantic.onBlockedContainer
        BadgeTone.CAUTION -> semantic.cautionContainer to semantic.onCautionContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = content,
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
private fun FactsCard(file: VaultFile, onHashCopied: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        RealType(file)
        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Fact(stringResource(R.string.details_size), Modifier.weight(1f)) { Value(Formatters.size(file.sizeBytes)) }
            Fact(stringResource(R.string.details_downloaded), Modifier.weight(1f)) {
                Value(Formatters.dateTime(file.createdAt))
            }
        }
        Divider()
        Fact(stringResource(R.string.details_source)) {
            SelectionContainer { Mono(file.sourceUrl, 12.5f) }
        }
        Divider()
        HashRow(file.sha256, onHashCopied)
    }
}

@Composable
private fun RealType(file: VaultFile) {
    Fact(stringResource(R.string.details_real_type)) {
        val extension = NameSanitizer.extensionOf(file.displayName)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Value(file.detectedType.label)
            if (extension != null) {
                Text(
                    text = " " + stringResource(
                        if (file.isMismatch) R.string.details_named else R.string.details_matches,
                        extension,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (file.isMismatch) {
                        SandboxTheme.semanticColors.caution
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (file.isMismatch) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Text(
            text = stringResource(R.string.details_real_type_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HashRow(sha256: String, onHashCopied: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Fact(stringResource(R.string.details_sha256), Modifier.weight(1f)) {
            SelectionContainer { Mono(sha256, 12f) }
        }
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("SHA-256", sha256)))
                    onHashCopied()
                }
            },
        ) {
            Icon(SandboxIcons.Copy, stringResource(R.string.details_copy_hash), Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Actions(
    file: VaultFile,
    showOpen: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val rules = TypePolicy.rulesFor(file.category)
    val canOpen = rules.canOpen && !file.openFailed
    val canShare = (rules.canShareOriginal || rules.canSafeCopy) && !file.openFailed
    val locked = file.category == FileCategory.BLOCKED || file.category == FileCategory.UNKNOWN
    Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val buttonModifier = Modifier
            .weight(1f)
            .height(48.dp)
        if (showOpen) {
            Button(onClick = onOpen, enabled = canOpen, modifier = buttonModifier) {
                ButtonContent(if (canOpen) SandboxIcons.Open else SandboxIcons.Lock, R.string.details_open)
            }
        }
        // Still tappable when sharing is off, to say why; it only looks disabled.
        val shareColors = if (canShare) {
            ButtonDefaults.filledTonalButtonColors()
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_CONTAINER_ALPHA),
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = LOCKED_CONTENT_ALPHA),
            )
        }
        FilledTonalButton(onClick = onShare, colors = shareColors, modifier = buttonModifier) {
            ButtonContent(if (canShare) SandboxIcons.Share else SandboxIcons.Lock, R.string.details_share)
        }
        if (locked) {
            Button(onClick = onDelete, modifier = buttonModifier) {
                ButtonContent(SandboxIcons.Delete, R.string.details_delete)
            }
        } else {
            OutlinedButton(onClick = onDelete, modifier = buttonModifier) {
                ButtonContent(SandboxIcons.Delete, R.string.details_delete)
            }
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, text: Int) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(stringResource(text), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
}

@Composable
private fun Fact(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Column(modifier = modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        value()
    }
}

@Composable
private fun Value(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Mono(text: String, size: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = MonoFamily,
            fontSize = size.sp,
            lineHeight = 18.sp,
        ),
    )
}

@Composable
private fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/** Material's disabled look, for a button that stays tappable. */
private const val LOCKED_CONTAINER_ALPHA = 0.12f
private const val LOCKED_CONTENT_ALPHA = 0.38f
