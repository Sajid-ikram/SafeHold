package dev.ikram.sandbox.feature.browser

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BrowserTab(
    val id: String,
    /** Null for a new tab that has not been sent anywhere yet. */
    val url: String? = null,
    val title: String? = null,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    /** The page's renderer died. Its WebView is gone until the tab is reloaded. */
    val crashed: Boolean = false,
)

data class Tabs(val list: List<BrowserTab>, val activeId: String) {
    val active: BrowserTab get() = list.first { it.id == activeId }
}

/** In-memory tabs (v1: a simple list). There is always at least one tab. */
class TabManager(private val newId: () -> String = { UUID.randomUUID().toString() }) {
    private val state = MutableStateFlow(BrowserTab(newId()).let { Tabs(listOf(it), it.id) })
    val tabs: StateFlow<Tabs> = state.asStateFlow()

    val active: BrowserTab get() = state.value.active

    fun open(): BrowserTab {
        val tab = BrowserTab(newId())
        state.update { Tabs(it.list + tab, tab.id) }
        return tab
    }

    fun select(id: String) {
        state.update { tabs -> if (tabs.list.any { it.id == id }) tabs.copy(activeId = id) else tabs }
    }

    /** Closes [id]. Closing the active tab activates its neighbour; closing the last opens a fresh one. */
    fun close(id: String) {
        state.update { tabs ->
            val index = tabs.list.indexOfFirst { it.id == id }
            if (index < 0) return@update tabs
            val remaining = tabs.list - tabs.list[index]
            when {
                remaining.isEmpty() -> BrowserTab(newId()).let { Tabs(listOf(it), it.id) }
                tabs.activeId != id -> tabs.copy(list = remaining)
                else -> Tabs(remaining, remaining[minOf(index, remaining.lastIndex)].id)
            }
        }
    }

    fun update(id: String, transform: (BrowserTab) -> BrowserTab) {
        state.update { tabs -> tabs.copy(list = tabs.list.map { if (it.id == id) transform(it) else it }) }
    }

    fun contains(id: String): Boolean = state.value.list.any { it.id == id }
}
