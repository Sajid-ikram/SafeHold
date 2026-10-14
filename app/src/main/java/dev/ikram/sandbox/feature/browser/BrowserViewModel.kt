package dev.ikram.sandbox.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ikram.sandbox.core.filetype.ClaimedType
import dev.ikram.sandbox.core.filetype.DownloadFileName
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.feature.browser.web.WebCommand
import dev.ikram.sandbox.feature.browser.web.WebEvent
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class BrowserViewModel @Inject constructor() : ViewModel() {
    private val tabManager = TabManager()
    private val overlay = MutableStateFlow(Overlay())
    private val commandChannel = Channel<WebCommand>(Channel.UNLIMITED)
    private var hostGeneration: String? = null

    val uiState: StateFlow<BrowserUiState> = combine(tabManager.tabs, overlay) { tabs, overlay ->
        BrowserUiState(
            tabs = tabs.list,
            activeTabId = tabs.activeId,
            downloadPrompt = overlay.downloadPrompt,
            message = overlay.message,
            showTabSwitcher = overlay.showTabSwitcher,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = tabManager.tabs.value.let { BrowserUiState(it.list, it.activeId) },
    )

    /** One-shot instructions for the WebViews, applied by the screen. */
    val commands: Flow<WebCommand> = commandChannel.receiveAsFlow()

    fun onEvent(event: BrowserEvent) {
        when (event) {
            is BrowserEvent.SubmitAddress -> AddressInput.resolve(event.text)?.let { load(tabManager.active.id, it) }

            BrowserEvent.GoBack -> send(WebCommand.GoBack(tabManager.active.id))

            BrowserEvent.GoForward -> send(WebCommand.GoForward(tabManager.active.id))

            BrowserEvent.Reload -> reload(tabManager.active)

            BrowserEvent.StopLoading -> send(WebCommand.Stop(tabManager.active.id))

            BrowserEvent.NewTab -> {
                tabManager.open()
                overlay.update { it.copy(showTabSwitcher = false) }
            }

            is BrowserEvent.SelectTab -> {
                tabManager.select(event.tabId)
                overlay.update { it.copy(showTabSwitcher = false) }
            }

            is BrowserEvent.CloseTab -> {
                tabManager.close(event.tabId)
                send(WebCommand.Destroy(event.tabId))
            }

            BrowserEvent.ShowTabs -> overlay.update { it.copy(showTabSwitcher = true) }

            BrowserEvent.HideTabs -> overlay.update { it.copy(showTabSwitcher = false) }

            BrowserEvent.ConfirmDownload -> overlay.update {
                it.copy(downloadPrompt = null, message = BrowserMessage.SAVING_NOT_READY)
            }

            BrowserEvent.DismissDownload -> overlay.update { it.copy(downloadPrompt = null) }

            BrowserEvent.MessageShown -> overlay.update { it.copy(message = null) }

            is BrowserEvent.HostAttached -> onHostAttached(event.generation)

            is BrowserEvent.Web -> onWebEvent(event.tabId, event.event)
        }
    }

    private fun onWebEvent(tabId: String, event: WebEvent) {
        if (!tabManager.contains(tabId)) return
        when (event) {
            is WebEvent.PageStarted -> tabManager.update(tabId) {
                it.copy(url = event.url, isLoading = true, progress = 0, crashed = false)
            }

            is WebEvent.PageFinished -> tabManager.update(tabId) { it.copy(url = event.url, isLoading = false) }

            is WebEvent.ProgressChanged -> tabManager.update(tabId) {
                it.copy(progress = event.percent, isLoading = event.percent < COMPLETE)
            }

            is WebEvent.TitleChanged -> tabManager.update(tabId) { it.copy(title = event.title) }

            is WebEvent.HistoryChanged -> tabManager.update(tabId) {
                it.copy(url = event.url, canGoBack = event.canGoBack, canGoForward = event.canGoForward)
            }

            is WebEvent.NavigationBlocked -> overlay.update { it.copy(message = BrowserMessage.NAVIGATION_BLOCKED) }

            is WebEvent.DownloadRequested -> onDownloadRequested(event.request)

            is WebEvent.NavigationSettled -> tabManager.update(tabId) {
                it.copy(
                    url = event.url,
                    isLoading = false,
                    progress = 0,
                    canGoBack = event.canGoBack,
                    canGoForward = event.canGoForward,
                )
            }

            WebEvent.RendererGone -> tabManager.update(tabId) {
                it.copy(crashed = true, isLoading = false, canGoBack = false, canGoForward = false)
            }
        }
    }

    private fun onDownloadRequested(request: DownloadRequest) {
        val scheme = request.url.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") {
            // blob: and data: downloads are skipped in v1.
            overlay.update { it.copy(message = BrowserMessage.DOWNLOAD_UNSUPPORTED) }
            return
        }
        overlay.update { it.copy(downloadPrompt = promptFor(request)) }
    }

    /** A new pool (the activity was recreated) has blank WebViews: reload every tab that had a page. */
    private fun onHostAttached(generation: String) {
        val previous = hostGeneration
        hostGeneration = generation
        if (previous == null || previous == generation) return
        tabManager.tabs.value.list.forEach { tab ->
            if (tab.url != null && !tab.crashed) send(WebCommand.Load(tab.id, tab.url))
        }
    }

    private fun load(tabId: String, url: String) {
        tabManager.update(tabId) { it.copy(url = url, isLoading = true, progress = 0, crashed = false) }
        send(WebCommand.Load(tabId, url))
    }

    /** A crashed tab has no WebView left, so it is loaded again from scratch. */
    private fun reload(tab: BrowserTab) {
        when {
            tab.crashed && tab.url != null -> load(tab.id, tab.url)
            tab.url != null -> send(WebCommand.Reload(tab.id))
        }
    }

    private fun send(command: WebCommand) {
        commandChannel.trySend(command)
    }

    private data class Overlay(
        val downloadPrompt: DownloadPrompt? = null,
        val message: BrowserMessage? = null,
        val showTabSwitcher: Boolean = false,
    )

    companion object {
        private const val COMPLETE = 100

        private val APP_TYPES = setOf(
            DetectedType.APK, DetectedType.APKS, DetectedType.XAPK, DetectedType.AAB, DetectedType.APPX,
            DetectedType.IPA, DetectedType.MSI, DetectedType.LINUX_PACKAGE, DetectedType.CRX,
        )

        fun promptFor(request: DownloadRequest): DownloadPrompt {
            val fileName = DownloadFileName.resolve(request.url, request.contentDisposition, request.mimeType)
            val claimed = ClaimedType.of(fileName, request.mimeType)
            val category = claimed?.let(TypePolicy::categoryOf) ?: FileCategory.UNKNOWN
            val kind = when {
                category != FileCategory.BLOCKED -> DownloadPrompt.Kind.SAVE
                claimed in APP_TYPES -> DownloadPrompt.Kind.BLOCKED_APP
                else -> DownloadPrompt.Kind.BLOCKED_PROGRAM
            }
            return DownloadPrompt(
                request = request,
                fileName = fileName,
                typeLabel = claimed?.label,
                category = category,
                sizeBytes = request.contentLength.takeIf { it > 0 },
                // Where the bytes come from, which is not always the page that linked them.
                sourceHost = AddressInput.hostOf(request.url) ?: AddressInput.hostOf(request.referer),
                kind = kind,
            )
        }
    }
}
