package dev.ikram.sandbox.core.filetype

import java.net.URLDecoder
import java.nio.charset.Charset

/**
 * Reads the file name from a `Content-Disposition` header (RFC 6266). The result is raw
 * server text: pass it through [NameSanitizer] before showing it.
 */
object ContentDisposition {
    fun fileName(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val parameters = parameters(header)
        // filename* carries the charset and wins when both are present.
        return parameters["filename*"]?.let(::decodeExtended)?.takeIf { it.isNotBlank() }
            ?: parameters["filename"]?.takeIf { it.isNotBlank() }
    }

    /**
     * `type; a=b; c="d; e"` as lower-cased names and unquoted values. Parameters without
     * a value are skipped, and the first of a repeated name wins.
     */
    private fun parameters(header: String): Map<String, String> = splitOutsideQuotes(header)
        .drop(1) // the disposition type
        .mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) return@mapNotNull null
            part.substring(0, equals).trim().lowercase() to unquote(part.substring(equals + 1).trim())
        }
        .distinctBy { it.first }
        .toMap()

    /** Splits on semicolons, except inside quoted strings. */
    private fun splitOutsideQuotes(header: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var escaped = false
        for (char in header) {
            when {
                escaped -> escaped = false

                char == '\\' && inQuotes -> escaped = true

                char == '"' -> inQuotes = !inQuotes

                char == ';' && !inQuotes -> {
                    parts += current.toString()
                    current.clear()
                    continue
                }
            }
            current.append(char)
        }
        parts += current.toString()
        return parts
    }

    /** `"a \"b\""` becomes `a "b"`; unquoted values pass through. */
    private fun unquote(value: String): String {
        if (value.length < 2 || !value.startsWith('"') || !value.endsWith('"')) return value
        return value.substring(1, value.length - 1).replace(Regex("\\\\(.)"), "$1")
    }

    /** `UTF-8''na%C3%AFve.txt` (RFC 5987). Unknown charsets and bad escapes give null. */
    private fun decodeExtended(value: String): String? {
        val parts = value.split('\'', limit = 3)
        if (parts.size != 3) return null
        val charset = runCatching { Charset.forName(parts[0].trim()) }.getOrNull() ?: return null
        return runCatching { URLDecoder.decode(parts[2].replace("+", "%2B"), charset.name()) }.getOrNull()
    }
}
