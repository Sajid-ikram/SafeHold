package dev.ikram.sandbox.feature.vault.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import dev.ikram.sandbox.core.designsystem.component.FileTypeIcon
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.filetype.ClaimedType
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.vault.showsMismatch
import dev.ikram.sandbox.util.Formatters

@Composable
fun VaultItemRow(file: VaultFile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FileTypeIcon(category = file.category, isMismatch = file.showsMismatch)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.vault_item_meta,
                    Formatters.size(file.sizeBytes),
                    file.sourceHost,
                    Formatters.time(file.createdAt),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FileStatusBadge(file, Modifier.padding(top = 2.dp))
        }
    }
}

/** A download still on its way in, with progress and a cancel button. */
@Composable
fun DownloadRow(download: DownloadState.Running, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FileTypeIcon(category = download.claimedCategory())
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = download.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DownloadProgressBar(download)
            Text(
                text = download.progressText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(SandboxIcons.Close, stringResource(R.string.vault_cancel_download), Modifier.size(18.dp))
        }
    }
}

@Composable
fun DownloadProgressBar(download: DownloadState.Running, modifier: Modifier = Modifier) {
    val barModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(2.dp))
    val total = download.totalBytes
    if (total != null && total > 0) {
        LinearProgressIndicator(
            progress = { (download.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
            modifier = barModifier,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(modifier = barModifier, gapSize = 0.dp)
    }
}

@Composable
fun DownloadState.Running.progressText(): String {
    val total = totalBytes
    return if (total != null && total > 0) {
        stringResource(R.string.vault_saving, Formatters.size(bytesDownloaded), Formatters.size(total))
    } else {
        stringResource(R.string.vault_saving_unknown_total, Formatters.size(bytesDownloaded))
    }
}

/** Until the bytes arrive, only the name hints at what a download is. */
fun DownloadState.Running.claimedCategory(): FileCategory =
    ClaimedType.fromFileName(displayName)?.let(TypePolicy::categoryOf) ?: FileCategory.UNKNOWN
