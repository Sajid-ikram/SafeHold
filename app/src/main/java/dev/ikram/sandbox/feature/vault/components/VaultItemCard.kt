package dev.ikram.sandbox.feature.vault.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.icon
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.showsMismatch
import dev.ikram.sandbox.util.Formatters

private val CardShape = RoundedCornerShape(16.dp)

/** Grid view card. Real previews arrive with the isolated renderer; until then, the type icon. */
@Composable
fun VaultItemCard(file: VaultFile, dayText: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
    ) {
        CardPreview(file.category, file.showsMismatch)
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                file.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.vault_card_meta, Formatters.size(file.sizeBytes), dayText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FileStatusBadge(file, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun DownloadCard(download: DownloadState.Running, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        CardPreview(download.claimedCategory(), isMismatch = false)
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                download.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DownloadProgressBar(download)
            Text(
                text = download.progressText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CardPreview(category: FileCategory, isMismatch: Boolean) {
    val semantic = SandboxTheme.semanticColors
    val (container, content) = when {
        isMismatch -> semantic.cautionContainer to semantic.onCautionContainer
        category == FileCategory.BLOCKED -> semantic.blockedContainer to semantic.onBlockedContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isMismatch) SandboxIcons.Warning else category.icon(),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(32.dp),
        )
    }
}
