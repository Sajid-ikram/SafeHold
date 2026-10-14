package dev.ikram.sandbox.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TabManagerTest {
    private var next = 0
    private val tabs = TabManager { "t${next++}" }

    @Test
    fun startsWithOneEmptyTab() {
        assertEquals(1, tabs.tabs.value.list.size)
        assertEquals(null, tabs.active.url)
    }

    @Test
    fun openActivatesTheNewTab() {
        val tab = tabs.open()
        assertEquals(tab.id, tabs.active.id)
        assertEquals(2, tabs.tabs.value.list.size)
    }

    @Test
    fun closingTheActiveTabActivatesItsNeighbour() {
        tabs.open() // t1
        tabs.open() // t2
        tabs.select("t1")
        tabs.close("t1")
        assertEquals("t2", tabs.active.id)
        tabs.close("t2")
        assertEquals("t0", tabs.active.id)
    }

    @Test
    fun closingAnotherTabKeepsTheActiveOne() {
        tabs.open() // t1, active
        tabs.close("t0")
        assertEquals("t1", tabs.active.id)
    }

    @Test
    fun closingTheLastTabOpensAFreshOne() {
        tabs.close("t0")
        assertEquals(1, tabs.tabs.value.list.size)
        assertNotEquals("t0", tabs.active.id)
    }

    @Test
    fun unknownIdsAreIgnored() {
        tabs.select("nope")
        tabs.close("nope")
        assertEquals("t0", tabs.active.id)
    }

    @Test
    fun update() {
        tabs.update("t0") { it.copy(url = "https://example.org", title = "Example") }
        assertEquals("Example", tabs.active.title)
    }
}
