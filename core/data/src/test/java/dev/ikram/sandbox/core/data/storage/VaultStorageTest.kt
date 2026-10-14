package dev.ikram.sandbox.core.data.storage

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultStorageTest {
    @get:Rule val folder = TemporaryFolder()

    private val root by lazy { folder.newFolder("files") }
    private val storage by lazy { VaultStorage(root) }
    private fun id() = UUID.randomUUID().toString()

    @Test
    fun vaultFilesLiveUnderFilesDirVault() {
        val id = id()
        assertEquals(File(root, "vault/$id.bin"), storage.fileFor(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathsCannotBeBuiltFromAnythingButAUuid() {
        storage.fileFor("../../shared_prefs/settings")
    }

    @Test(expected = IllegalArgumentException::class)
    fun partialsToo() {
        storage.newPartial("evil.apk")
    }

    @Test
    fun commitMovesThePartialIntoTheVault() {
        val id = id()
        val partial = storage.newPartial(id).apply { writeText("hello") }
        val stored = storage.commit(partial, id)
        assertFalse(partial.exists())
        assertEquals("hello", stored.readText())
        assertEquals(setOf(id), storage.storedIds())
    }

    @Test
    fun deleteRemovesTheFile() {
        val id = id()
        storage.commit(storage.newPartial(id).apply { writeText("x") }, id)
        assertTrue(storage.delete(id))
        assertTrue(storage.storedIds().isEmpty())
        assertTrue(storage.delete(id)) // already gone is fine
    }

    @Test
    fun onlyEarlierSessionsLeftoversAreCleared() {
        val old = storage.newPartial(id()).apply {
            writeText("old")
            setLastModified(0)
        }
        val current = storage.newPartial(id()).apply { writeText("in progress") }
        storage.clearPartials()
        assertFalse(old.exists())
        assertTrue(current.exists())
    }

    @Test
    fun earlierSessionIdsSkipFilesWrittenNow() {
        val oldId = id()
        storage.commit(storage.newPartial(oldId).apply { writeText("a") }, oldId).setLastModified(0)
        val newId = id()
        storage.commit(storage.newPartial(newId).apply { writeText("b") }, newId)
        assertEquals(setOf(oldId), storage.earlierSessionIds())
        assertEquals(setOf(oldId, newId), storage.storedIds())
    }
}
