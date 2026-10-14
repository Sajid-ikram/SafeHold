package dev.ikram.sandbox.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.icon
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.components.label
import dev.ikram.sandbox.feature.vault.components.noun

/**
 * Shown the first time a disguised file is tapped. A locked file can only be deleted or
 * kept locked; anything else opens as what it really is.
 */
@Composable
fun MismatchWarningDialog(file: VaultFile, onAccept: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val claimed = file.claimedType?.let(TypePolicy::categoryOf) ?: FileCategory.UNKNOWN
    val locked = !TypePolicy.rulesFor(file.category).canOpen
    val semantic = SandboxTheme.semanticColors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(semantic.cautionContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SandboxIcons.Warning, contentDescription = null, tint = semantic.onCautionContainer)
                }
                Text(
                    text = stringResource(R.string.mismatch_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(
                        if (locked) R.string.mismatch_body_blocked else R.string.mismatch_body_safe,
                        file.displayName,
                        stringResource(claimed.noun()),
                        stringResource(file.category.noun()),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Comparison(claimed, file, locked)
            if (locked) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = SandboxIcons.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.mismatch_locked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val primaryModifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                if (locked) {
                    Button(onClick = onDelete, modifier = primaryModifier) {
                        Text(stringResource(R.string.mismatch_delete))
                    }
                    TextButton(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mismatch_keep_locked))
                    }
                } else {
                    Button(onClick = onAccept, modifier = primaryModifier) {
                        Text(stringResource(R.string.mismatch_open_anyway))
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mismatch_delete))
                    }
                }
            }
        }
    }
}

/** "Name says: Video" against "It's actually: Android app (APK)". */
@Composable
private fun Comparison(claimed: FileCategory, file: VaultFile, locked: Boolean) {
    val actualColor = if (locked) SandboxTheme.semanticColors.blocked else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        ComparisonRow(
            claimed.icon(),
            MaterialTheme.colorScheme.onSurfaceVariant,
            R.string.mismatch_name_says,
            stringResource(claimed.label()),
            MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ComparisonRow(
            file.category.icon(),
            actualColor,
            R.string.mismatch_actually,
            file.detectedType.label,
            actualColor,
        )
    }
}

@Composable
private fun ComparisonRow(icon: ImageVector, iconTint: Color, label: Int, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}
