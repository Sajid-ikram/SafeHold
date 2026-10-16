package dev.ikram.sandbox.feature.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import kotlinx.coroutines.launch

private class Page(val title: Int, val message: Int, val art: @Composable () -> Unit)

private val Pages = listOf(
    Page(R.string.onboarding_title, R.string.onboarding_message) { BrowseArt() },
    Page(R.string.onboarding_vault_title, R.string.onboarding_vault_message) { VaultArt() },
    Page(R.string.onboarding_share_title, R.string.onboarding_share_message) { ShareArt() },
)

@Composable
fun OnboardingRoute(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingScreen(
        onDone = {
            viewModel.finish()
            onDone()
        },
        modifier = modifier,
    )
}

/** Three pages: browse as usual, downloads stay in the vault, share a safe copy. */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { Pages.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == Pages.lastIndex
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        TopRow(
            page = page,
            onBack = { scope.launch { pager.animateScrollToPage(page - 1) } },
            onSkip = onDone,
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            PageContent(Pages[index])
        }
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Dots(page)
            Button(
                onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(page + 1) } },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next),
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun TopRow(page: Int, onBack: () -> Unit, onSkip: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 4.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (page == 0) {
            Row(
                modifier = Modifier.padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(SandboxIcons.Vault, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.app_name), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        } else {
            IconButton(onClick = onBack) { Icon(SandboxIcons.Back, stringResource(R.string.action_back)) }
        }
        Spacer(Modifier.weight(1f))
        if (page < Pages.lastIndex) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
        }
    }
}

@Composable
private fun PageContent(page: Page) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 20.dp)
                .fillMaxWidth()
                .height(340.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center,
        ) { page.art() }
        Column(
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(page.title), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(page.message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Dots(page: Int) {
    val description = stringResource(R.string.onboarding_step, page + 1, Pages.size)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Pages.indices.forEach { index ->
            val width by animateDpAsState(if (index == page) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .background(
                        if (index ==
                            page
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    SandboxTheme { OnboardingScreen(onDone = {}) }
}
