package dev.ikram.sandbox.core.model

sealed interface DownloadState {
    val downloadId: String

    data class Running(
        override val downloadId: String,
        val displayName: String,
        val bytesDownloaded: Long,
        /** Null when the server did not send a length. */
        val totalBytes: Long?,
    ) : DownloadState

    data class Completed(override val downloadId: String, val fileId: String) : DownloadState

    data class Failed(override val downloadId: String, val reason: DownloadFailure) : DownloadState

    data class Cancelled(override val downloadId: String) : DownloadState
}

enum class DownloadFailure {
    NETWORK,
    HTTP_ERROR,
    TOO_LARGE,
    TIMEOUT,
    STORAGE_FULL,
    UNSUPPORTED_SCHEME,
    UNKNOWN,
}
