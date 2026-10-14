package dev.ikram.sandbox.feature.browser.web

import android.webkit.DownloadListener
import dev.ikram.sandbox.core.model.DownloadRequest

/**
 * Catches every download a page starts, before anything touches storage, and turns it
 * into a [DownloadRequest]. The only place downloads go from here is the vault.
 */
class DownloadInterceptor(
    private val currentPageUrl: () -> String?,
    private val onDownload: (DownloadRequest) -> Unit,
) : DownloadListener {

    override fun onDownloadStart(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long,
    ) {
        onDownload(
            DownloadRequest(
                url = url,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimetype,
                contentLength = contentLength,
                referer = currentPageUrl(),
            ),
        )
    }
}
