package dev.ikram.sandbox.core.filetype

import java.text.Normalizer

/**
 * Cleans server supplied file names for DISPLAY. The result is never used to build a
 * path: vault files are always stored as `<uuid>.bin`.
 */
object NameSanitizer {
    const val FALLBACK_NAME = "download"
    const val MAX_LENGTH = 120
    private const val MAX_EXTENSION_LENGTH = 10
    private const val ELLIPSIS = "…"

    /**
     * Invisible characters that can disguise a name. The right-to-left override is the
     * classic one: "photo\u202Egpj.exe" displays as "photoexe.jpg".
     */
    private val INVISIBLE = Regex("[\\p{Cc}\\p{Cf}\\u2028\\u2029]")
    private val WHITESPACE = Regex("\\s+")
    private val EXTENSION = Regex("[a-z0-9_-]{1,$MAX_EXTENSION_LENGTH}")

    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return FALLBACK_NAME
        val lastSegment = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = Normalizer.normalize(lastSegment, Normalizer.Form.NFC)
            .replace(WHITESPACE, " ")
            .replace(INVISIBLE, "")
            .trim(' ', '.')
        if (cleaned.isEmpty()) return FALLBACK_NAME
        return truncate(cleaned)
    }

    /**
     * The lower-case text after the LAST dot, so "movie.mp4.exe" is "exe". Null when
     * there is none, or when it does not look like an extension.
     */
    fun extensionOf(name: String?): String? {
        if (name == null) return null
        val visible = name.replace(INVISIBLE, "").trimEnd(' ', '.')
        val dot = visible.lastIndexOf('.')
        if (dot <= 0 || dot == visible.lastIndex) return null
        val extension = visible.substring(dot + 1).lowercase()
        return extension.takeIf { EXTENSION.matches(it) }
    }

    /** Cuts the middle of long names, keeping the extension that tells people what a file is. */
    private fun truncate(name: String): String {
        if (name.codePointCount(0, name.length) <= MAX_LENGTH) return name
        val extension = extensionOf(name)?.let { ".$it" }.orEmpty()
        val keep = MAX_LENGTH - extension.length - 1
        val base = name.dropLast(extension.length)
        val cut = base.offsetByCodePoints(0, minOf(keep, base.codePointCount(0, base.length)))
        // takeLast keeps the extension's original case.
        return base.substring(0, cut).trimEnd() + ELLIPSIS + name.takeLast(extension.length)
    }
}
