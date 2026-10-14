package dev.ikram.sandbox.feature.browser.web

import android.net.Uri
import android.os.Message
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/** Progress and titles, and a firm no to everything a page might ask the device for. */
class SandboxChromeClient(private val onEvent: (WebEvent) -> Unit) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onEvent(WebEvent.ProgressChanged(newProgress))
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!title.isNullOrBlank()) onEvent(WebEvent.TitleChanged(title))
    }

    /** No uploads in v1. */
    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = false

    /** Camera, microphone and the rest: the app holds none of those permissions. */
    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
        false
}
