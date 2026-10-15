package dev.ikram.sandbox.feature.viewer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons

/** Every viewer's top bar: back, the file name over "Opened in sandbox", then find or share, and details. */
@Composable
fun ViewerTopBar(
    title: String,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onFind: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(SandboxIcons.Back, stringResource(R.string.action_back), tint = contentColor)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SandboxIndicator()
        }
        if (onFind != null) {
            IconButton(onClick = onFind) {
                Icon(SandboxIcons.Search, stringResource(R.string.viewer_find), tint = contentColor)
            }
        }
        if (onShare != null) {
            IconButton(onClick = onShare) {
                Icon(SandboxIcons.Share, stringResource(R.string.viewer_share), tint = contentColor)
            }
        }
        IconButton(onClick = onDetails) {
            Icon(SandboxIcons.Info, stringResource(R.string.viewer_details), tint = contentColor)
        }
    }
}
