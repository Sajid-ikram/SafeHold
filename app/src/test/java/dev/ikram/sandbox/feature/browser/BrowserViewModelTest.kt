package dev.ikram.sandbox.feature.browser

import app.cash.turbine.test
import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.download.NewFilesCounter
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.fakes.FakeDownloadQueue
import dev.ikram.sandbox.fakes.FakePreferencesStore
import dev.ikram.sandbox.feature.browser.web.WebCommand
import dev.ikram.sandbox.feature.browser.web.WebEvent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BrowserViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    // Lazy: the rule must install the test Main dispatcher before viewModelScope starts.
    private val queue = FakeDownloadQueue()
    private val newFiles = NewFilesCounter()
    private val settings = SettingsRepository(FakePreferencesStore())
    private val viewModel by lazy { BrowserViewModel(queue, newFiles, settings) }
    private val state get() = viewModel.uiState.value
    private val tabId get() = state.activeTabId

    private fun web(event: WebEvent, tab: String = tabId) = viewModel.onEvent(BrowserEvent.Web(tab, event))

    private fun download(url: String, mime: String? = null, disposition: String? = null) = DownloadRequest(
        url = url,
        userAgent = "ua",
        contentDisposition = disposition,
        mimeType = mime,
        contentLength = 2_400_000,
        referer = "https://harbourlight.org/guides",
    )

    @Test
    fun submittingAnAddressLoadsItInTheActiveTab() = runTest {
        viewModel.commands.test {
            viewModel.onEvent(BrowserEvent.SubmitAddress("harbourlight.org/guides"))
            assertEquals(WebCommand.Load(tabId, "https://harbourlight.org/guides"), awaitItem())
        }
        assertEquals("https://harbourlight.org/guides", state.activeTab.url)
        assertTrue(state.activeTab.isLoading)
    }

    @Test
    fun javascriptTypedInTheAddressBarIsSearchedNotRun() = runTest {
        viewModel.commands.test {
            viewModel.onEvent(BrowserEvent.SubmitAddress("javascript:alert(1)"))
            val load = awaitItem() as WebCommand.Load
            assertTrue(load.url.startsWith(AddressInput.SEARCH_URL))
        }
    }

    @Test
    fun pageEventsUpdateTheTab() {
        web(WebEvent.PageStarted("https://example.org/"))
        web(WebEvent.ProgressChanged(40))
        web(WebEvent.TitleChanged("Example"))
        web(WebEvent.HistoryChanged("https://example.org/", canGoBack = true, canGoForward = false))
        assertEquals(40, state.activeTab.progress)
        assertTrue(state.activeTab.canGoBack)
        web(WebEvent.PageFinished("https://example.org/"))
        web(WebEvent.ProgressChanged(100))
        assertFalse(state.activeTab.isLoading)
        assertEquals("Example", state.activeTab.title)
    }

    @Test
    fun blockedNavigationShowsAMessage() {
        web(WebEvent.NavigationBlocked("market://details?id=x"))
        assertEquals(BrowserMessage.NAVIGATION_BLOCKED, state.message)
        viewModel.onEvent(BrowserEvent.MessageShown)
        assertNull(state.message)
    }

    @Test
    fun ordinaryDownloadAsksToSaveToTheVault() {
        web(WebEvent.DownloadRequested(download("https://harbourlight.org/files/north-coast-map.pdf")))
        val prompt = state.downloadPrompt!!
        assertEquals("north-coast-map.pdf", prompt.fileName)
        assertEquals("PDF document", prompt.typeLabel)
        assertEquals(FileCategory.PDF, prompt.category)
        assertEquals(DownloadPrompt.Kind.SAVE, prompt.kind)
        assertEquals("harbourlight.org", prompt.sourceHost)
        assertEquals(2_400_000L, prompt.sizeBytes)
    }

    @Test
    fun withAskingOffDownloadsGoStraightToTheVault() {
        runBlocking { settings.setAskBeforeDownload(false) }
        val request = download("https://harbourlight.org/files/north-coast-map.pdf")
        web(WebEvent.DownloadRequested(request))
        assertNull(state.downloadPrompt)
        assertEquals(listOf(request), queue.enqueued)
    }

    @Test
    fun apkDownloadIsOfferedOnlyAsLocked() {
        web(WebEvent.DownloadRequested(download("https://dl.fastfiles.example/free-movies-hd.apk")))
        val prompt = state.downloadPrompt!!
        assertEquals(DownloadPrompt.Kind.BLOCKED_APP, prompt.kind)
        assertEquals(FileCategory.BLOCKED, prompt.category)
        assertEquals("dl.fastfiles.example", prompt.sourceHost)
    }

    @Test
    fun exeAndScriptDownloadsAreProgramsNotApps() {
        web(WebEvent.DownloadRequested(download("https://x.example/setup.exe")))
        assertEquals(DownloadPrompt.Kind.BLOCKED_PROGRAM, state.downloadPrompt!!.kind)
        web(WebEvent.DownloadRequested(download("https://x.example/get", disposition = "attachment; filename=run.sh")))
        assertEquals(DownloadPrompt.Kind.BLOCKED_PROGRAM, state.downloadPrompt!!.kind)
    }

    @Test
    fun unlabelledDownloadIsUnknownButSavable() {
        web(WebEvent.DownloadRequested(download("https://x.example/get?id=7", mime = "application/octet-stream")))
        val prompt = state.downloadPrompt!!
        assertNull(prompt.typeLabel)
        assertEquals(FileCategory.UNKNOWN, prompt.category)
        assertEquals(DownloadPrompt.Kind.SAVE, prompt.kind)
    }

    @Test
    fun blobAndDataDownloadsAreNotSupportedYet() {
        web(WebEvent.DownloadRequested(download("blob:https://x.example/1234")))
        assertNull(state.downloadPrompt)
        assertEquals(BrowserMessage.DOWNLOAD_UNSUPPORTED, state.message)
        web(WebEvent.DownloadRequested(download("data:text/plain;base64,aGk=")))
        assertNull(state.downloadPrompt)
    }

    @Test
    fun aNavigationThatBecomesADownloadStopsLoading() {
        web(WebEvent.PageFinished("https://example.org/"))
        viewModel.onEvent(BrowserEvent.SubmitAddress("https://x.example/a.pdf"))
        assertTrue(state.activeTab.isLoading)
        web(WebEvent.DownloadRequested(download("https://x.example/a.pdf")))
        web(WebEvent.NavigationSettled("https://example.org/", canGoBack = false, canGoForward = false))
        assertFalse(state.activeTab.isLoading)
        assertEquals("https://example.org/", state.activeTab.url)
    }

    @Test
    fun aNewTabWhoseFirstAddressWasADownloadShowsTheNewTabPageAgain() {
        viewModel.onEvent(BrowserEvent.SubmitAddress("https://x.example/a.pdf"))
        web(WebEvent.NavigationSettled(null, canGoBack = false, canGoForward = false))
        assertNull(state.activeTab.url)
    }

    @Test
    fun confirmingStartsTheDownloadIntoTheVault() {
        val request = download("https://harbourlight.org/files/map.pdf")
        web(WebEvent.DownloadRequested(request))
        viewModel.onEvent(BrowserEvent.ConfirmDownload)
        assertNull(state.downloadPrompt)
        assertEquals(listOf(request), queue.enqueued)
        assertTrue(state.download is DownloadState.Running)
    }

    @Test
    fun finishedDownloadCardIsDismissedThroughTheQueue() {
        queue.emit(DownloadState.Completed("d1", "map.pdf", "d1", FileCategory.PDF))
        assertTrue(state.download is DownloadState.Completed)
        viewModel.onEvent(BrowserEvent.DownloadStatusDismissed("d1"))
        assertNull(state.download)
    }

    @Test
    fun newFilesAreCountedForTheVaultBadge() {
        newFiles.increment()
        newFiles.increment()
        assertEquals(2, state.newFiles)
        newFiles.setVaultVisible(true)
        assertEquals(0, state.newFiles)
    }

    @Test
    fun dismissingTheDownloadClearsIt() {
        web(WebEvent.DownloadRequested(download("https://x.example/a.pdf")))
        viewModel.onEvent(BrowserEvent.DismissDownload)
        assertNull(state.downloadPrompt)
    }

    @Test
    fun rendererCrashMarksTheTabAndReloadStartsAgain() = runTest {
        viewModel.onEvent(BrowserEvent.SubmitAddress("https://example.org"))
        web(WebEvent.RendererGone)
        assertTrue(state.activeTab.crashed)
        viewModel.commands.test {
            awaitItem() // the original load
            viewModel.onEvent(BrowserEvent.Reload)
            assertEquals(WebCommand.Load(tabId, "https://example.org"), awaitItem())
        }
        assertFalse(state.activeTab.crashed)
    }

    @Test
    fun aNewWebViewHostReloadsOpenPages() = runTest {
        viewModel.onEvent(BrowserEvent.HostAttached("first"))
        viewModel.onEvent(BrowserEvent.SubmitAddress("https://example.org"))
        viewModel.commands.test {
            awaitItem() // the original load
            viewModel.onEvent(BrowserEvent.HostAttached("first"))
            expectNoEvents()
            viewModel.onEvent(BrowserEvent.HostAttached("second"))
            assertEquals(WebCommand.Load(tabId, "https://example.org"), awaitItem())
        }
    }

    @Test
    fun closingATabDestroysItsWebView() = runTest {
        val first = tabId
        viewModel.onEvent(BrowserEvent.NewTab)
        assertEquals(2, state.tabs.size)
        viewModel.commands.test {
            viewModel.onEvent(BrowserEvent.CloseTab(first))
            assertEquals(WebCommand.Destroy(first), awaitItem())
        }
        assertEquals(1, state.tabs.size)
    }

    @Test
    fun eventsFromClosedTabsAreIgnored() {
        web(WebEvent.TitleChanged("ghost"), tab = "closed-tab")
        assertNull(state.activeTab.title)
    }
}
