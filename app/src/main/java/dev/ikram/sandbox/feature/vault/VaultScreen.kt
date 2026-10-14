package dev.ikram.sandbox.feature.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.EmptyState
import dev.ikram.sandbox.core.designsystem.component.SandboxTopBar
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme

@Composable
fun VaultRoute(onOpenFile: (fileId: String) -> Unit, modifier: Modifier = Modifier) {
    VaultScreen(onOpenFile = onOpenFile, modifier = modifier)
}

@Composable
fun VaultScreen(@Suppress("UNUSED_PARAMETER") onOpenFile: (fileId: String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        SandboxTopBar(title = stringResource(R.string.vault_title))
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = SandboxIcons.Vault,
                title = stringResource(R.string.vault_empty_title),
                message = stringResource(R.string.vault_empty_message),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VaultScreenPreview() {
    SandboxTheme { VaultScreen(onOpenFile = {}) }
}
