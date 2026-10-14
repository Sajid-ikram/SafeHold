package dev.ikram.sandbox.feature.browser.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.EmptyState
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons

/** What a fresh tab shows before it has been sent anywhere. No WebView exists yet. */
@Composable
fun NewTabPage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = SandboxIcons.Vault,
            title = stringResource(R.string.browser_new_tab_title),
            message = stringResource(R.string.browser_new_tab_message),
        )
    }
}

@Composable
fun CrashedPage(onReload: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = SandboxIcons.Warning,
            title = stringResource(R.string.browser_crashed_title),
            message = stringResource(R.string.browser_crashed_message),
            action = { Button(onClick = onReload) { Text(stringResource(R.string.browser_crashed_reload)) } },
        )
    }
}
