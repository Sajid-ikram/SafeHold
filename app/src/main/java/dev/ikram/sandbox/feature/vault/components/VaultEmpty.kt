package dev.ikram.sandbox.feature.vault.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons

/** The design's empty vault: a locked tray waiting for files. */
@Composable
fun VaultEmpty(onStartBrowsing: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        EmptyTray()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vault_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.vault_empty_message),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        FilledTonalButton(onClick = onStartBrowsing) {
            Icon(SandboxIcons.Browser, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.vault_empty_action), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun EmptyTray() {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(width = 220.dp, height = 200.dp)) {
        Box(
            Modifier
                .padding(start = 10.dp, end = 10.dp, top = 20.dp)
                .fillMaxSize()
                .background(colors.surfaceContainer, RoundedCornerShape(40.dp)),
        )
        Box(
            Modifier
                .offset(x = 50.dp, y = 52.dp)
                .size(width = 120.dp, height = 116.dp)
                .alpha(0.55f)
                .drawBehind {
                    drawRoundRect(
                        color = colors.outline,
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                        ),
                    )
                },
        )
        Box(
            Modifier
                .offset(x = 72.dp, y = 104.dp)
                .size(width = 34.dp, height = 42.dp)
                .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp)),
        )
        Box(
            Modifier
                .offset(x = 114.dp, y = 112.dp)
                .size(34.dp)
                .alpha(0.7f)
                .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
                .size(84.dp)
                .background(colors.background, CircleShape)
                .padding(8.dp)
                .background(colors.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SandboxIcons.Vault,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun VaultNoMatch(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            SandboxIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(R.string.vault_no_match_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.vault_no_match_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
