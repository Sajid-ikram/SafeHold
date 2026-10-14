package dev.ikram.sandbox.feature.browser.web

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Shows a pooled WebView. The pool owns its lifetime: leaving composition only detaches
 * it, so the page survives tab switches and trips to the vault.
 */
@Composable
fun SandboxWebView(webView: WebView, modifier: Modifier = Modifier) {
    AndroidView(
        factory = {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView
        },
        modifier = modifier,
        onRelease = { (it.parent as? ViewGroup)?.removeView(it) },
    )
}
