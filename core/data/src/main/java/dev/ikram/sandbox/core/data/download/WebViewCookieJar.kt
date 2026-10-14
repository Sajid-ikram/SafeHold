package dev.ikram.sandbox.core.data.download

import android.webkit.CookieManager
import javax.inject.Inject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Shares the browser's cookies with downloads, so a file behind a login downloads as
 * the signed-in page saw it. Cookies the download sets go back to the browser.
 */
class WebViewCookieJar @Inject constructor() : CookieJar {
    private val manager: CookieManager get() = CookieManager.getInstance()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = manager.getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { manager.setCookie(url.toString(), it.toString()) }
    }
}
