package dev.ikram.sandbox.feature.browser.web

import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Keeps every tab on http and https, and reports page state. SSL errors are not
 * overridden, so WebView's default (cancel the load) always applies.
 */
class SandboxWebViewClient(private val onEvent: (WebEvent) -> Unit, private val onRendererGone: (WebView) -> Unit) :
    WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return when (val decision = NavigationPolicy.decide(url, request.isForMainFrame)) {
            NavigationDecision.Allow -> false

            NavigationDecision.Block -> {
                if (request.isForMainFrame) onEvent(WebEvent.NavigationBlocked(url))
                true
            }

            is NavigationDecision.Redirect -> {
                view.loadUrl(decision.url)
                true
            }
        }
    }

    /** Belt and braces behind allowFileAccess and allowContentAccess: local files are never served. */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        if (NavigationPolicy.isLocalResource(request.url.toString())) {
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        } else {
            null
        }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        onEvent(WebEvent.PageStarted(url))
    }

    override fun onPageFinished(view: WebView, url: String) {
        onEvent(WebEvent.PageFinished(url))
        onEvent(WebEvent.HistoryChanged(url, view.canGoBack(), view.canGoForward()))
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        onEvent(WebEvent.HistoryChanged(url, view.canGoBack(), view.canGoForward()))
    }

    /**
     * Without this, a crashed or killed renderer takes the whole app down. The pool
     * destroys the WebView and the tab offers a reload.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone(view)
        return true
    }
}
