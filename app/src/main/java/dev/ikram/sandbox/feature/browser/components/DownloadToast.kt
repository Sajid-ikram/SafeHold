package dev.ikram.sandbox.feature.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.model.DownloadFailure
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.util.Formatters

/** The dark card above the page while a download goes into the vault (design: Download progress). */
@Composable
fun DownloadToast(download: DownloadState, onView: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape)
            .clip(shape)
            .background(colors.inverseSurface)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val (icon, title, body) = content(download)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = colors.inverseOnSurface, modifier = Modifier.size(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.inverseOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inverseOnSurface,
                    modifier = Modifier.alpha(BODY_ALPHA),
                )
            }
            if (download !is DownloadState.Failed) {
                TextButton(onClick = onView) {
                    Text(stringResource(R.string.download_toast_view), color = colors.inversePrimary)
                }
            }
        }
        if (download is DownloadState.Running) {
            val total = download.totalBytes
            val barModifier = Modifier
                .padding(end = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
            val track = colors.inverseOnSurface.copy(alpha = TRACK_ALPHA)
            if (total != null && total > 0) {
                LinearProgressIndicator(
                    progress = { (download.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = barModifier,
                    color = colors.inversePrimary,
                    trackColor = track,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            } else {
                LinearProgressIndicator(
                    modifier = barModifier,
                    color = colors.inversePrimary,
                    trackColor = track,
                    gapSize = 0.dp,
                )
            }
        }
    }
}

@Composable
private fun content(download: DownloadState): Triple<ImageVector, String, String> = when (download) {
    is DownloadState.Running -> Triple(
        SandboxIcons.Vault,
        stringResource(R.string.download_toast_saving, download.displayName),
        download.totalBytes?.takeIf { it > 0 }?.let {
            stringResource(
                R.string.download_toast_progress,
                Formatters.size(download.bytesDownloaded),
                Formatters.size(it),
            )
        } ?: stringResource(R.string.download_toast_progress_unknown, Formatters.size(download.bytesDownloaded)),
    )

    is DownloadState.Completed -> Triple(
        SandboxIcons.ShieldCheck,
        stringResource(R.string.download_toast_saved, download.displayName),
        stringResource(
            if (download.category == FileCategory.BLOCKED || download.category == FileCategory.UNKNOWN) {
                R.string.download_toast_saved_locked_body
            } else {
                R.string.download_toast_saved_body
            },
        ),
    )

    is DownloadState.Failed -> Triple(
        SandboxIcons.Warning,
        stringResource(R.string.download_toast_failed, download.displayName),
        stringResource(download.reason.text()),
    )

    is DownloadState.Cancelled -> Triple(SandboxIcons.Close, download.displayName, "")
}

private fun DownloadFailure.text(): Int = when (this) {
    DownloadFailure.NETWORK -> R.string.download_failure_network
    DownloadFailure.HTTP_ERROR -> R.string.download_failure_http
    DownloadFailure.TOO_LARGE -> R.string.download_failure_too_large
    DownloadFailure.TIMEOUT -> R.string.download_failure_timeout
    DownloadFailure.STORAGE_FULL -> R.string.download_failure_storage
    DownloadFailure.INSECURE_CONNECTION -> R.string.download_failure_insecure
    DownloadFailure.UNKNOWN -> R.string.download_failure_unknown
}

private const val BODY_ALPHA = 0.75f
private const val TRACK_ALPHA = 0.25f
