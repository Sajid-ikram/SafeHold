package dev.ikram.sandbox.core.model

sealed interface DownloadState {
    /** The same UUID the file gets in the vault once it is saved. */
    val downloadId: String
    val displayName: String

    data class Running(
        override val downloadId: String,
        override val displayName: String,
        val bytesDownloaded: Long,
        /** Null when the server did not send a length. */
        val totalBytes: Long?,
    ) : DownloadState

    data class Completed(
        override val downloadId: String,
        override val displayName: String,
        val fileId: String,
        val category: FileCategory,
    ) : DownloadState

    data class Failed(override val downloadId: String, override val displayName: String, val reason: DownloadFailure) :
        DownloadState

    data class Cancelled(override val downloadId: String, override val displayName: String) : DownloadState
}

enum class DownloadFailure {
    NETWORK,
    HTTP_ERROR,
    TOO_LARGE,
    TIMEOUT,
    STORAGE_FULL,

    /** Plain http, or a secure connection that could not be made. SafeHold only downloads over https. */
    INSECURE_CONNECTION,
    UNKNOWN,
}
