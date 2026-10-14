package dev.ikram.sandbox.fakes

import dev.ikram.sandbox.core.data.download.DownloadQueue
import dev.ikram.sandbox.core.model.DownloadRequest
import dev.ikram.sandbox.core.model.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FakeDownloadQueue : DownloadQueue {
    val enqueued = mutableListOf<DownloadRequest>()
    val cancelled = mutableListOf<String>()
    private val state = MutableStateFlow<List<DownloadState>>(emptyList())

    override val downloads: StateFlow<List<DownloadState>> = state

    override fun enqueue(request: DownloadRequest): String {
        enqueued += request
        val id = "download-${enqueued.size}"
        state.update { it + DownloadState.Running(id, request.url.substringAfterLast('/'), 0, null) }
        return id
    }

    override fun cancel(downloadId: String) {
        cancelled += downloadId
    }

    override fun dismiss(downloadId: String) = state.update { list -> list.filterNot { it.downloadId == downloadId } }

    fun emit(vararg states: DownloadState) {
        state.value = states.toList()
    }
}
