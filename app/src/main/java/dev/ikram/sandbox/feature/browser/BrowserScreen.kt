package dev.ikram.sandbox.feature.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ikram.sandbox.R
import dev.ikram.sandbox.core.designsystem.icon.SandboxIcons
import dev.ikram.sandbox.core.designsystem.theme.SandboxTheme
import dev.ikram.sandbox.feature.browser.components.AddressBar
import dev.ikram.sandbox.feature.browser.components.CrashedPage
import dev.ikram.sandbox.feature.browser.components.DownloadPromptSheet
import dev.ikram.sandbox.feature.browser.components.NewTabPage
import dev.ikram.sandbox.feature.browser.components.TabSwitcherSheet
import dev.ikram.sandbox.feature.browser.web.LocalWebViewPool
import dev.ikram.sandbox.feature.browser.web.SandboxWebView

@Composable
fun BrowserRoute(
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pool = LocalWebViewPool.current

    // The listener stays set when the screen leaves composition, so a page that finishes
    // loading while the vault is open still updates its tab.
    DisposableEffect(pool, viewModel) {
        pool.listener = { tabId, event -> viewModel.onEvent(BrowserEvent.Web(tabId, event)) }
        viewModel.onEvent(BrowserEvent.HostAttached(pool.generation))
        onDispose { }
    }
    LaunchedEffect(pool, viewModel) {
        viewModel.commands.collect(pool::execute)
    }
    BackHandler(enabled = state.activeTab.canGoBack) { viewModel.onEvent(BrowserEvent.GoBack) }

    BrowserScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenVault = onOpenVault,
        modifier = modifier,
        webContent = { tab -> SandboxWebView(pool.get(tab.id), Modifier.fillMaxSize()) },
    )
}

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    onEvent: (BrowserEvent) -> Unit,
    onOpenVault: () -> Unit,
    modifier: Modifier = Modifier,
    webContent: @Composable (BrowserTab) -> Unit,
) {
    val tab = state.activeTab
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.text()) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onEvent(BrowserEvent.MessageShown)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
    ) {
        BrowserToolbar(state = state, onEvent = onEvent, onOpenVault = onOpenVault)
        if (tab.isLoading) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                tab.crashed -> CrashedPage(onReload = { onEvent(BrowserEvent.Reload) })
                tab.url == null -> NewTabPage()
                else -> key(tab.id) { webContent(tab) }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }

    state.downloadPrompt?.let { prompt ->
        DownloadPromptSheet(
            prompt = prompt,
            onConfirm = { onEvent(BrowserEvent.ConfirmDownload) },
            onDismiss = { onEvent(BrowserEvent.DismissDownload) },
        )
    }
    if (state.showTabSwitcher) {
        TabSwitcherSheet(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            onSelect = { onEvent(BrowserEvent.SelectTab(it)) },
            onClose = { onEvent(BrowserEvent.CloseTab(it)) },
            onNewTab = { onEvent(BrowserEvent.NewTab) },
            onDismiss = { onEvent(BrowserEvent.HideTabs) },
        )
    }
}

@Composable
private fun BrowserToolbar(state: BrowserUiState, onEvent: (BrowserEvent) -> Unit, onOpenVault: () -> Unit) {
    val tab = state.activeTab
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(BrowserEvent.GoBack) }, enabled = tab.canGoBack) {
            Icon(SandboxIcons.Back, stringResource(R.string.browser_back))
        }
        IconButton(onClick = { onEvent(BrowserEvent.GoForward) }, enabled = tab.canGoForward) {
            Icon(SandboxIcons.Forward, stringResource(R.string.browser_forward))
        }
        AddressBar(
            url = tab.url,
            isLoading = tab.isLoading,
            onSubmit = { onEvent(BrowserEvent.SubmitAddress(it)) },
            onReload = { onEvent(BrowserEvent.Reload) },
            onStop = { onEvent(BrowserEvent.StopLoading) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
        )
        TabCountButton(count = state.tabs.size, onClick = { onEvent(BrowserEvent.ShowTabs) })
        IconButton(onClick = onOpenVault) {
            Icon(
                SandboxIcons.Vault,
                stringResource(R.string.browser_open_vault),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    val description = pluralStringResource(R.plurals.browser_tab_count, count, count)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(5.dp))
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.coerceAtMost(MAX_SHOWN_TABS).toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_SHOWN_TABS = 99

private fun BrowserMessage.text(): Int = when (this) {
    BrowserMessage.NAVIGATION_BLOCKED -> R.string.browser_message_navigation_blocked
    BrowserMessage.DOWNLOAD_UNSUPPORTED -> R.string.browser_message_download_unsupported
    BrowserMessage.SAVING_NOT_READY -> R.string.browser_message_saving_not_ready
}

@Preview(showBackground = true)
@Composable
private fun BrowserScreenNewTabPreview() {
    SandboxTheme {
        BrowserScreen(
            state = BrowserUiState(tabs = listOf(BrowserTab("a")), activeTabId = "a"),
            onEvent = {},
            onOpenVault = {},
            webContent = {},
        )
    }
}
