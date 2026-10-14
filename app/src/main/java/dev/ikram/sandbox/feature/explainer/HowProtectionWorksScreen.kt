package dev.ikram.sandbox.feature.explainer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.component.SandboxTopBar
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme

@Composable
fun HowProtectionWorksRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    HowProtectionWorksScreen(onBack = onBack, modifier = modifier)
}

@Composable
fun HowProtectionWorksScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        SandboxTopBar(
            title = stringResource(R.string.explainer_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.action_back),
        )
        Text(
            text = stringResource(R.string.explainer_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HowProtectionWorksScreenPreview() {
    SandboxTheme { HowProtectionWorksScreen(onBack = {}) }
}
