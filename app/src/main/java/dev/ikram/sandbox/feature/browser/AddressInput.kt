package dev.ikram.sandbox.feature.browser

import java.net.URI
import java.net.URLEncoder

/** Turns what someone typed in the address bar into a URL, and a URL back into short display text. */
object AddressInput {
    const val SEARCH_URL = "https://duckduckgo.com/?q="

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$")
    private val PORT_AND_REST = Regex("^\\d{1,5}(/.*)?$")
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    private val DOMAIN_LABELS = Regex("^[\\p{L}\\p{N}-]+(\\.[\\p{L}\\p{N}-]+)*\\.[\\p{L}]{2,}$")

    /**
     * A web address to load, or a search for the text. Only http and https are ever
     * returned: "javascript:", "file:" and the rest are searched for, never loaded.
     */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        val scheme = SCHEME.find(text)
        if (scheme != null) {
            val name = scheme.groupValues[1].lowercase()
            val rest = scheme.groupValues[2]
            if (name in WEB_SCHEMES && isLoadable(text)) return text
            // "localhost:8080/x" and "example.com:8443" look like a scheme but are a host and port.
            if (!PORT_AND_REST.matches(rest) || !looksLikeHost(name)) return search(text)
        }
        return if (looksLikeHost(hostPart(text)) && isLoadable("https://$text")) "https://$text" else search(text)
    }

    private val WEB_SCHEMES = setOf("http", "https")

    fun search(query: String): String = SEARCH_URL + URLEncoder.encode(query, "UTF-8")

    /** "https://www.example.org/guides/" becomes "example.org/guides". */
    fun display(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host ?: return url
        val path = uri.rawPath.orEmpty().trimEnd('/')
        val shortHost = host.removePrefix("www.")
        return if (uri.scheme.equals("http", ignoreCase = true)) "http://$shortHost$path" else "$shortHost$path"
    }

    fun isSecure(url: String?): Boolean = url?.startsWith("https://", ignoreCase = true) == true

    fun hostOf(url: String?): String? = url?.let { runCatching { URI(it).host }.getOrNull() }?.removePrefix("www.")

    private fun hostPart(text: String): String =
        text.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')

    private fun looksLikeHost(host: String): Boolean =
        host.equals("localhost", ignoreCase = true) || IPV4.matches(host) || DOMAIN_LABELS.matches(host)

    /** A parseable URL with a host. Text with spaces is a search, not an address. */
    private fun isLoadable(url: String): Boolean =
        !url.contains(' ') && runCatching { URI(url).host != null }.getOrDefault(false)
}
