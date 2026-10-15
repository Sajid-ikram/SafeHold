package dev.ikram.sandbox.feature.browser.web

import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.UUID

/**
 * Owns one WebView per tab, for as long as the activity lives. WebViews hold an
 * activity context, so they must not live in a ViewModel; and keeping them here (not in
 * composition) means switching to the Vault and back doesn't reload the page.
 */
class WebViewPool(private val context: Context) {
    private val views = mutableMapOf<String, WebView>()

    /** Changes with every new pool, so the ViewModel knows when tabs must be reloaded. */
    val generation: String = UUID.randomUUID().toString()

    /** Receives every tab's events. Set by the browser screen. */
    var listener: ((tabId: String, WebEvent) -> Unit)? = null

    fun get(tabId: String): WebView = views.getOrPut(tabId) { create(tabId) }

    fun execute(command: WebCommand) {
        when (command) {
            is WebCommand.Load -> get(command.tabId).loadUrl(command.url)
            is WebCommand.GoBack -> views[command.tabId]?.goBack()
            is WebCommand.GoForward -> views[command.tabId]?.goForward()
            is WebCommand.Reload -> views[command.tabId]?.reload()
            is WebCommand.Stop -> views[command.tabId]?.stopLoading()
            is WebCommand.Destroy -> destroy(command.tabId)
        }
    }

    fun pause() {
        views.values.forEach { it.onPause() }
        // Timers are process wide; pausing through any one WebView pauses them all.
        views.values.firstOrNull()?.pauseTimers()
    }

    fun resume() {
        views.values.firstOrNull()?.resumeTimers()
        views.values.forEach { it.onResume() }
    }

    fun destroyAll() {
        views.keys.toList().forEach(::destroy)
    }

    /** History, cache, cookies, site storage and saved logins, for every tab. The vault is not touched. */
    fun clearBrowsingData(onDone: () -> Unit) {
        views.values.forEach { view ->
            view.clearHistory()
            view.clearFormData()
        }
        // The cache is shared by every WebView; with no tab open, a passing one clears it.
        val existing = views.values.firstOrNull()
        if (existing != null) {
            existing.clearCache(true)
        } else {
            WebView(context).apply {
                clearCache(true)
                destroy()
            }
        }
        WebStorage.getInstance().deleteAllData()
        WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies {
            cookies.flush()
            onDone()
        }
    }

    private fun destroy(tabId: String) {
        views.remove(tabId)?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
    }

    private fun create(tabId: String): WebView = WebView(context).apply {
        WebViewHardening.apply(this)
        val emit: (WebEvent) -> Unit = { event -> listener?.invoke(tabId, event) }
        webViewClient = SandboxWebViewClient(
            onEvent = emit,
            onRendererGone = { gone ->
                if (views[tabId] === gone) destroy(tabId)
                emit(WebEvent.RendererGone)
            },
        )
        webChromeClient = SandboxChromeClient(emit)
        setDownloadListener(
            DownloadInterceptor(currentPageUrl = { url }) { request ->
                emit(WebEvent.DownloadRequested(request))
                // The navigation that became a download never commits or finishes; once
                // WebView has dropped it, report the page that is really showing.
                post { emit(WebEvent.NavigationSettled(url, canGoBack(), canGoForward())) }
            },
        )
    }
}

val LocalWebViewPool = staticCompositionLocalOf<WebViewPool> { error("WebViewPool is provided by MainActivity") }
