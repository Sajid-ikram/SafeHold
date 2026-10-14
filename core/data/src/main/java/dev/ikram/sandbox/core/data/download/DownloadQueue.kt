package dev.ikram.sandbox.core.data.download

import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.core.model.DownloadState
import kotlinx.coroutines.flow.StateFlow

/** Downloads into the vault, as the screens see them. */
interface DownloadQueue {
    /** Running downloads, and finished ones until [dismiss]ed. Oldest first. */
    val downloads: StateFlow<List<DownloadState>>

    /** Starts a download and returns its id, which is also the vault file's id. */
    fun enqueue(request: DownloadRequest): String

    fun cancel(downloadId: String)

    /** Forgets a finished download once its outcome has been shown. */
    fun dismiss(downloadId: String)
}
