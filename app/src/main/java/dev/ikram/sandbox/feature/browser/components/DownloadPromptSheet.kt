package dev.ikram.sandbox.feature.browser.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.FileTypeIcon
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.feature.browser.BrowserViewModel
import dev.ikram.sandbox.feature.browser.DownloadPrompt
import dev.ikram.sandbox.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPromptSheet(prompt: DownloadPrompt, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        DownloadPromptContent(prompt, onConfirm, onDismiss, Modifier.navigationBarsPadding())
    }
}

@Composable
fun DownloadPromptContent(
    prompt: DownloadPrompt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocked = prompt.kind != DownloadPrompt.Kind.SAVE
    Column(
        modifier = modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(if (blocked) R.string.download_title_blocked else R.string.download_title),
            style = MaterialTheme.typography.titleLarge,
        )
        FileCard(prompt)
        if (blocked) BlockedNotice(prompt.kind) else VaultNotice(typeKnown = prompt.typeLabel != null)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val buttonModifier = Modifier
                .weight(1f)
                .height(48.dp)
            if (blocked) {
                OutlinedButton(onClick = onConfirm, modifier = buttonModifier) {
                    Text(stringResource(R.string.download_save_locked))
                }
                Button(onClick = onDismiss, modifier = buttonModifier) {
                    Text(stringResource(R.string.download_cancel_download))
                }
            } else {
                OutlinedButton(onClick = onDismiss, modifier = buttonModifier) {
                    Text(stringResource(R.string.download_cancel))
                }
                Button(onClick = onConfirm, modifier = buttonModifier) {
                    Text(stringResource(R.string.download_save))
                }
            }
        }
    }
}

@Composable
private fun FileCard(prompt: DownloadPrompt) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeIcon(category = prompt.category, neutralBackground = MaterialTheme.colorScheme.background)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = prompt.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.download_type_and_size,
                    prompt.typeLabel ?: stringResource(R.string.download_type_unknown),
                    prompt.sizeBytes?.let(Formatters::size) ?: stringResource(R.string.download_size_unknown),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            prompt.sourceHost?.let { host ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = SandboxIcons.Browser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.download_from, host),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultNotice(typeKnown: Boolean) {
    val semantic = SandboxTheme.semanticColors
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(semantic.safeContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SandboxIcons.ShieldCheck, null, tint = semantic.onSafeContainer, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.download_vault_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(
                    if (typeKnown) R.string.download_vault_body else R.string.download_vault_body_unknown,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BlockedNotice(kind: DownloadPrompt.Kind) {
    val semantic = SandboxTheme.semanticColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(semantic.blockedContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = SandboxIcons.Lock,
            contentDescription = null,
            tint = semantic.onBlockedContainer,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    if (kind == DownloadPrompt.Kind.BLOCKED_APP) {
                        R.string.download_blocked_app_title
                    } else {
                        R.string.download_blocked_program_title
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
                color = semantic.onBlockedContainer,
            )
            Text(
                text = stringResource(R.string.download_blocked_body),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.onBlockedContainer,
            )
        }
    }
}

private fun previewPrompt(url: String, mime: String, length: Long) = BrowserViewModel.promptFor(
    DownloadRequest(url, null, null, mime, length, "https://harbourlight.org/guides"),
)

@Preview(showBackground = true)
@Composable
private fun DownloadPromptPreview() {
    SandboxTheme {
        DownloadPromptContent(
            prompt = previewPrompt("https://harbourlight.org/files/north-coast-map.pdf", "application/pdf", 2_400_000),
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadPromptBlockedPreview() {
    SandboxTheme {
        DownloadPromptContent(
            prompt = previewPrompt(
                "https://dl.fastfiles.example/free-movies-hd.apk",
                "application/octet-stream",
                18_600_000,
            ),
            onConfirm = {},
            onDismiss = {},
        )
    }
}
