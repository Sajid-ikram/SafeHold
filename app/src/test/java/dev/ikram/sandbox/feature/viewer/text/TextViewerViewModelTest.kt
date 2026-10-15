package dev.ikram.sandbox.feature.viewer.text

import androidx.lifecycle.SavedStateHandle
import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.fakes.FakeVaultFileDao
import dev.ikram.sandbox.fakes.addFile
import dev.ikram.sandbox.feature.viewer.ViewerRouterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class TextViewerViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val dao = FakeVaultFileDao()
    private val storage by lazy { VaultStorage(folder.newFolder("files")) }
    private val repository by lazy { VaultRepository(dao, storage, Dispatchers.Unconfined) }

    private fun store(text: String, type: DetectedType = DetectedType.TEXT): String {
        val id = dao.addFile("notes.txt", type)
        storage.vaultDir.mkdirs()
        storage.fileFor(id).writeText(text)
        return id
    }

    private fun viewModel(id: String) = TextViewerViewModel(
        SavedStateHandle(mapOf(ViewerRouterViewModel.FILE_ID to id)),
        repository,
        TextFileReader(storage, Dispatchers.Unconfined),
        mainRule.dispatcher,
    )

    private val TextViewerViewModel.document: TextDocument
        get() = (uiState.value.content as TextContent.Ready).document

    @Test
    fun showsTheFileAsPlainText() {
        val viewModel = viewModel(store("line one\nline two\n"))
        assertEquals(2, viewModel.document.lineCount)
        assertEquals("line two", viewModel.document.row(1))
        assertEquals("notes.txt", viewModel.uiState.value.file?.displayName)
    }

    @Test
    fun aFileThatIsNotTextIsNotRead() {
        val viewModel = viewModel(store("%PDF-1.7", DetectedType.PDF))
        assertEquals(TextContent.Unreadable, viewModel.uiState.value.content)
    }

    @Test
    fun aFileMissingFromDiskIsUnreadable() {
        val viewModel = viewModel(dao.addFile("gone.txt", DetectedType.TEXT))
        assertEquals(TextContent.Unreadable, viewModel.uiState.value.content)
    }

    @Test
    fun anUnknownIdIsUnreadable() {
        val viewModel = viewModel("00000000-0000-0000-0000-000000000000")
        assertEquals(TextContent.Unreadable, viewModel.uiState.value.content)
    }

    @Test
    fun findMovesThroughMatchesAndWrapsAround() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel(store("cat\ndog\nCat\ncatalog"))
        viewModel.onEvent(TextViewerEvent.FindOpened)
        viewModel.onEvent(TextViewerEvent.QueryChanged("cat"))
        advanceUntilIdle()
        val find = viewModel.uiState.value.find
        assertTrue(find.open)
        assertEquals(3, find.matches.size)
        assertEquals(0, find.current)

        viewModel.onEvent(TextViewerEvent.PreviousMatch)
        assertEquals(2, viewModel.uiState.value.find.current)
        viewModel.onEvent(TextViewerEvent.NextMatch)
        assertEquals(0, viewModel.uiState.value.find.current)
    }

    @Test
    fun aNewQueryReplacesTheOldMatches() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel(store("alpha beta"))
        viewModel.onEvent(TextViewerEvent.QueryChanged("alpha"))
        viewModel.onEvent(TextViewerEvent.QueryChanged("zzz"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.find.matches.isEmpty())
        assertEquals(-1, viewModel.uiState.value.find.current)
        // Stepping with nothing found does nothing.
        viewModel.onEvent(TextViewerEvent.NextMatch)
        assertEquals(-1, viewModel.uiState.value.find.current)
    }

    @Test
    fun closingFindClearsIt() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel(store("cat"))
        viewModel.onEvent(TextViewerEvent.FindOpened)
        viewModel.onEvent(TextViewerEvent.QueryChanged("cat"))
        advanceUntilIdle()
        viewModel.onEvent(TextViewerEvent.FindClosed)
        advanceUntilIdle()
        assertEquals(FindState(), viewModel.uiState.value.find)
    }

    @Test
    fun textSizeStaysWithinBounds() {
        val viewModel = viewModel(store("a"))
        repeat(50) { viewModel.onEvent(TextViewerEvent.LargerText) }
        assertEquals(TextViewerViewModel.MAX_FONT_SP, viewModel.uiState.value.fontSizeSp)
        repeat(50) { viewModel.onEvent(TextViewerEvent.SmallerText) }
        assertEquals(TextViewerViewModel.MIN_FONT_SP, viewModel.uiState.value.fontSizeSp)
    }

    @Test
    fun wrapAndLineNumbersToggle() {
        val viewModel = viewModel(store("a"))
        viewModel.onEvent(TextViewerEvent.ToggleWrap)
        viewModel.onEvent(TextViewerEvent.ToggleLineNumbers)
        assertFalse(viewModel.uiState.value.wrap)
        assertFalse(viewModel.uiState.value.lineNumbers)
    }

    @Test
    fun deletingRemovesTheFileAndCloses() {
        val id = store("bye")
        val viewModel = viewModel(id)
        viewModel.onEvent(TextViewerEvent.DeleteTapped)
        viewModel.onEvent(TextViewerEvent.DeleteConfirmed)
        assertTrue(viewModel.uiState.value.closed)
        assertFalse(storage.fileFor(id).exists())
        assertTrue(dao.rows.value.isEmpty())
    }
}
