package dev.ikram.sandbox.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.EmptyState
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme

@Composable
fun BrowserRoute(modifier: Modifier = Modifier) {
    BrowserScreen(modifier = modifier)
}

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = SandboxIcons.Browser,
            title = stringResource(R.string.browser_placeholder_title),
            message = stringResource(R.string.browser_placeholder_message),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrowserScreenPreview() {
    SandboxTheme { BrowserScreen() }
}
