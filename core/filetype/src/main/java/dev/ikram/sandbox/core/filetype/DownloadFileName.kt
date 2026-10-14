package dev.ikram.sandbox.core.filetype

import dev.ikram.sandbox.core.model.DetectedType
import java.net.URI
import java.net.URLDecoder

/** The display name for a download: header first, then the URL, sanitized either way. */
object DownloadFileName {
    fun resolve(url: String, contentDisposition: String?, mimeType: String?): String {
        val raw = ContentDisposition.fileName(contentDisposition) ?: fromUrl(url)
        val name = NameSanitizer.sanitize(raw)
        if (NameSanitizer.extensionOf(name) != null) return name

        // "download" served as application/pdf reads better as "download.pdf".
        val claimed = ClaimedType.fromMimeType(mimeType) ?: return name
        if (claimed in NO_EXTENSION_HINT || claimed.extension == "bin") return name
        return "$name.${claimed.extension}"
    }

    /** Scripts share text's ".txt", which would hide what the server said the file is. */
    private val NO_EXTENSION_HINT = setOf(DetectedType.SCRIPT)

    private fun fromUrl(url: String): String? {
        val path = runCatching { URI(url).rawPath }.getOrNull()
            ?: url.substringBefore('?').substringBefore('#').substringAfter("://", "")
        val segment = path.trimEnd('/').substringAfterLast('/')
        if (segment.isEmpty()) return null
        return runCatching { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }.getOrDefault(segment)
    }
}
