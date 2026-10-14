package dev.ikram.sandbox.core.model

/** Everything WebView's DownloadListener tells us about a download, untouched. */
data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    /** -1 when the server did not say. */
    val contentLength: Long,
    val referer: String?,
)
