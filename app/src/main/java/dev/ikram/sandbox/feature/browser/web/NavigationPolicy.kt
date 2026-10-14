package dev.ikram.sandbox.feature.browser.web

import java.net.URLDecoder

sealed interface NavigationDecision {
    data object Allow : NavigationDecision

    /** Not loaded, and never handed to another app. */
    data object Block : NavigationDecision

    /** An app link whose page offered a web fallback; load that instead. */
    data class Redirect(val url: String) : NavigationDecision
}

/**
 * Which navigations a page may make (CLAUDE.md, WebView hardening). The top frame only
 * ever goes to http and https. Nothing launches another app in v1: an `intent:` link
 * loads its web fallback if it has one, and is dropped otherwise.
 */
object NavigationPolicy {
    private const val FALLBACK_KEY = "S.browser_fallback_url="

    /** Schemes a sub-frame can never load, even though WebView settings already refuse them. */
    private val LOCAL_SCHEMES = setOf("file", "content")

    fun decide(url: String, isMainFrame: Boolean): NavigationDecision {
        val scheme = schemeOf(url)
        return when {
            !isMainFrame -> if (scheme in LOCAL_SCHEMES) NavigationDecision.Block else NavigationDecision.Allow
            scheme == "http" || scheme == "https" -> NavigationDecision.Allow
            scheme == "intent" -> webFallback(url)?.let(NavigationDecision::Redirect) ?: NavigationDecision.Block
            else -> NavigationDecision.Block
        }
    }

    /** True for resource requests that must never be served, whatever the frame. */
    fun isLocalResource(url: String): Boolean = schemeOf(url) in LOCAL_SCHEMES

    private fun schemeOf(url: String): String = url.substringBefore(':', "").trim().lowercase()

    private fun webFallback(intentUrl: String): String? {
        val start = intentUrl.indexOf(FALLBACK_KEY).takeIf { it >= 0 } ?: return null
        val encoded = intentUrl.substring(start + FALLBACK_KEY.length).substringBefore(';')
        val fallback = runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull() ?: return null
        val scheme = schemeOf(fallback)
        return fallback.takeIf { scheme == "http" || scheme == "https" }
    }
}
