package dev.ikram.sandbox.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.model.FileCategory

/**
 * The square file thumbnail from the design. Blocked and unknown files get the red
 * container; a type mismatch gets the amber one, whatever the category.
 */
@Composable
fun FileTypeIcon(
    category: FileCategory,
    modifier: Modifier = Modifier,
    isMismatch: Boolean = false,
    size: FileTypeIconSize = FileTypeIconSize.Regular,
) {
    val semantic = SandboxTheme.semanticColors
    val (container, content) = when {
        isMismatch -> semantic.cautionContainer to semantic.onCautionContainer

        category == FileCategory.BLOCKED || category == FileCategory.UNKNOWN ->
            semantic.blockedContainer to semantic.onBlockedContainer

        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(size.box)
            .background(container, RoundedCornerShape(size.corner)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isMismatch) SandboxIcons.Warning else category.icon(),
            contentDescription = null,
            tint = content,
        )
    }
}

fun FileCategory.icon(): ImageVector = when (this) {
    FileCategory.IMAGE -> SandboxIcons.Image
    FileCategory.VIDEO -> SandboxIcons.Video
    FileCategory.AUDIO -> SandboxIcons.Audio
    FileCategory.PDF -> SandboxIcons.Pdf
    FileCategory.TEXT -> SandboxIcons.Text
    FileCategory.ARCHIVE -> SandboxIcons.Archive
    FileCategory.BLOCKED -> SandboxIcons.Blocked
    FileCategory.UNKNOWN -> SandboxIcons.Unknown
}

@Preview
@Composable
private fun FileTypeIconPreview() {
    SandboxTheme {
        Row {
            FileCategory.entries.forEach { FileTypeIcon(it) }
            FileTypeIcon(FileCategory.VIDEO, isMismatch = true)
        }
    }
}
