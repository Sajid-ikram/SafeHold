package dev.ikram.sandbox.feature.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.SandboxTopBar

/**
 * Picks the built in viewer for a vault file from its category, and refuses blocked and
 * unknown files outright. Placeholder until the viewers exist (build phases 5 to 7).
 */
@Composable
fun ViewerRouter(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        SandboxTopBar(
            title = stringResource(R.string.viewer_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back),
        )
        Text(
            text = stringResource(R.string.viewer_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}
