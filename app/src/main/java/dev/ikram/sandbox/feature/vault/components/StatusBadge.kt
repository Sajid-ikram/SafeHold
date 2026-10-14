package dev.ikram.sandbox.feature.vault.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile

/** The design's pill badge: Safe to view / View only as text / Blocked, and friends. */
@Composable
fun StatusBadge(text: String, icon: ImageVector, tone: BadgeTone, modifier: Modifier = Modifier) {
    val semantic = SandboxTheme.semanticColors
    val (container, content) = when (tone) {
        BadgeTone.SAFE -> semantic.safeContainer to semantic.onSafeContainer
        BadgeTone.NEUTRAL -> semantic.neutralContainer to MaterialTheme.colorScheme.onSurfaceVariant
        BadgeTone.CAUTION -> semantic.cautionContainer to semantic.onCautionContainer
        BadgeTone.BLOCKED -> semantic.blockedContainer to semantic.onBlockedContainer
    }
    Row(
        modifier = modifier
            .height(22.dp)
            .background(container, RoundedCornerShape(11.dp))
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** What a vault file may do, at a glance. */
@Composable
fun FileStatusBadge(file: VaultFile, modifier: Modifier = Modifier) {
    val (text, icon, tone) = when {
        file.openFailed -> Triple(R.string.badge_open_failed, SandboxIcons.Warning, BadgeTone.BLOCKED)

        else -> when (file.category) {
            FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO, FileCategory.PDF ->
                Triple(R.string.badge_safe, SandboxIcons.ShieldCheck, BadgeTone.SAFE)

            FileCategory.TEXT -> Triple(R.string.badge_text_only, SandboxIcons.Text, BadgeTone.NEUTRAL)

            FileCategory.ARCHIVE -> Triple(R.string.badge_details_only, SandboxIcons.Archive, BadgeTone.NEUTRAL)

            FileCategory.BLOCKED, FileCategory.UNKNOWN ->
                Triple(R.string.badge_blocked, SandboxIcons.Lock, BadgeTone.BLOCKED)
        }
    }
    StatusBadge(stringResource(text), icon, tone, modifier)
}

@Composable
fun BadgeTone.contentColor(): Color = when (this) {
    BadgeTone.SAFE -> SandboxTheme.semanticColors.safe
    BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    BadgeTone.CAUTION -> SandboxTheme.semanticColors.caution
    BadgeTone.BLOCKED -> SandboxTheme.semanticColors.blocked
}
