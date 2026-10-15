package dev.ikram.sandbox.feature.share

import dev.ikram.sandbox.core.filetype.ClaimedType
import dev.ikram.sandbox.core.filetype.NameSanitizer
import dev.ikram.sandbox.core.model.DetectedType

/** Names for files handed to other apps. Only ever shown to them, never used as a path. */
object ShareNames {
    /**
     * The downloaded name when its extension already says what the shared file is, and
     * otherwise the same name with the real extension: a file is never passed on under a
     * disguise, and a safe copy that changed format says so.
     */
    fun forShare(displayName: String, sharedType: DetectedType): String {
        val name = NameSanitizer.sanitize(displayName)
        val extension = NameSanitizer.extensionOf(name)
        val keep = ClaimedType.fromFileName(name) == sharedType &&
            (sharedType != DetectedType.TEXT || extension in INERT_TEXT)
        if (keep) return name
        val base = if (extension == null) name else name.dropLast(extension.length + 1).trimEnd(' ', '.')
        return NameSanitizer.sanitize("${base.ifEmpty { NameSanitizer.FALLBACK_NAME }}.${sharedType.extension}")
    }

    /**
     * Text names that nothing acts on. Text called .html, .svg or .xml is renamed .txt:
     * the other phone might open those in a browser, where any script inside would run.
     */
    private val INERT_TEXT = setOf("txt", "text", "md", "markdown", "csv", "tsv", "json", "log", "srt", "vtt")
}
