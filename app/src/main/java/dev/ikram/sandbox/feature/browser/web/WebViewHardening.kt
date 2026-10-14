package dev.ikram.sandbox.feature.browser.web

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Every security setting for a browsing WebView, in one place (golden rule 7: these
 * are never relaxed). Browsing itself stays a normal Chromium WebView; the protection
 * SafeHold offers is about downloaded files, not about pages.
 */
object WebViewHardening {
    // JavaScript is required to browse the modern web. What keeps it contained: no
    // addJavascriptInterface anywhere, no file or content access, and only http(s)
    // navigations (see NavigationPolicy).
    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true

            // Nothing on the device is reachable from a page.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false

            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true

            // No pop-up windows in v1: target=_blank links open in the same tab.
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false

            // The app holds no location permission; don't let pages ask.
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = true

            // Ordinary browser behaviour.
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
    }
}
