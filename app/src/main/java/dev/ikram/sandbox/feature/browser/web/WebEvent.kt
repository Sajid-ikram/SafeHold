package dev.ikram.sandbox.feature.browser.web

import dev.ikram.sandbox.core.model.DownloadRequest

/** What a tab's WebView reports. Plain data, so the ViewModel never touches a View. */
sealed interface WebEvent {
    data class PageStarted(val url: String) : WebEvent

    data class PageFinished(val url: String) : WebEvent

    data class ProgressChanged(val percent: Int) : WebEvent

    data class TitleChanged(val title: String) : WebEvent

    data class HistoryChanged(val url: String, val canGoBack: Boolean, val canGoForward: Boolean) : WebEvent

    /** A page tried to leave http and https, for example to open another app. */
    data class NavigationBlocked(val url: String) : WebEvent

    data class DownloadRequested(val request: DownloadRequest) : WebEvent

    /**
     * A navigation ended without a new page (it became a download). [url] is the page
     * still showing, or null if the tab never had one.
     */
    data class NavigationSettled(val url: String?, val canGoBack: Boolean, val canGoForward: Boolean) : WebEvent

    /** The renderer process died; the WebView has been destroyed. */
    data object RendererGone : WebEvent
}

/** What the ViewModel asks a tab's WebView to do. */
sealed interface WebCommand {
    val tabId: String

    data class Load(override val tabId: String, val url: String) : WebCommand

    data class GoBack(override val tabId: String) : WebCommand

    data class GoForward(override val tabId: String) : WebCommand

    data class Reload(override val tabId: String) : WebCommand

    data class Stop(override val tabId: String) : WebCommand

    data class Destroy(override val tabId: String) : WebCommand
}
