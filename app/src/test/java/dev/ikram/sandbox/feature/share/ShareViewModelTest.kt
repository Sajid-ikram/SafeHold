package dev.ikram.sandbox.feature.share

import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.ShareMode
import dev.ikram.sandbox.fakes.FakeVaultFileDao
import dev.ikram.sandbox.fakes.addFile
import dev.ikram.sandbox.feature.share.copy.CopyFailedException
import dev.ikram.sandbox.feature.share.copy.CopyFailure
import dev.ikram.sandbox.feature.share.copy.CopyProgress
import dev.ikram.sandbox.feature.share.copy.PreparedShare
import dev.ikram.sandbox.feature.share.copy.ShareCopier
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(DelicateCoroutinesApi::class) // isClosedForSend shows the copy was cancelled.
class ShareViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val dao = FakeVaultFileDao()
    private val repository by lazy {
        VaultRepository(dao, VaultStorage(folder.newFolder("files")), Dispatchers.Unconfined)
    }

    private var now = 0L
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    private val progress = Channel<CopyProgress>(Channel.UNLIMITED)
    private val requests = mutableListOf<ShareMode>()
    private val copier = ShareCopier { _, mode ->
        requests += mode
        progress.consumeAsFlow()
    }
    private val viewModel by lazy { ShareViewModel(repository, copier, clock) }
    private val state get() = viewModel.uiState.value

    private fun open(name: String, type: DetectedType, openFailed: Boolean = false) {
        viewModel.onEvent(ShareEvent.Opened(dao.addFile(name, type, openFailed = openFailed)))
    }

    private fun done() = CopyProgress.Done(PreparedShare(File("copy.jpg"), "harbour.jpg", "image/jpeg"))

    @Test
    fun aSafeCopyIsTheDefaultForFilesThatOpen() {
        open("harbour.jpg", DetectedType.JPEG)
        assertEquals(ShareStage.Choosing, state.stage)
        assertEquals(setOf(ShareMode.SAFE_COPY, ShareMode.ORIGINAL, ShareMode.LINK), state.available)
        assertEquals(ShareMode.SAFE_COPY, state.selected)
    }

    @Test
    fun archivesCannotBeRebuiltSoTheLinkComesFirst() {
        open("trail-photos.zip", DetectedType.ZIP)
        assertEquals(setOf(ShareMode.ORIGINAL, ShareMode.LINK), state.available)
        assertEquals(ShareMode.LINK, state.selected)
        viewModel.onEvent(ShareEvent.Selected(ShareMode.SAFE_COPY))
        assertEquals(ShareMode.LINK, state.selected)
    }

    @Test
    fun blockedFilesNeverLeave() {
        open("free-movies-hd.apk", DetectedType.APK)
        assertEquals(ShareStage.Blocked(ShareBlockReason.APP_INSTALLER), state.stage)
        assertTrue(state.available.isEmpty())
        viewModel.onEvent(ShareEvent.ShareTapped)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun everyKindOfLockedFileSaysWhy() {
        open("setup.exe", DetectedType.PE_EXE)
        assertEquals(ShareStage.Blocked(ShareBlockReason.PROGRAM), state.stage)
        open("mystery.bin", DetectedType.UNKNOWN)
        assertEquals(ShareStage.Blocked(ShareBlockReason.UNKNOWN), state.stage)
        open("crash.png", DetectedType.PNG, openFailed = true)
        assertEquals(ShareStage.Blocked(ShareBlockReason.OPEN_FAILED), state.stage)
    }

    @Test
    fun aSafeCopyShowsProgressThenIsReady() {
        open("harbour.jpg", DetectedType.JPEG)
        viewModel.onEvent(ShareEvent.ShareTapped)
        assertEquals(listOf(ShareMode.SAFE_COPY), requests)
        assertEquals(ShareStage.Preparing(ShareMode.SAFE_COPY, null, null), state.stage)
        progress.trySend(CopyProgress.Working(0.5f))
        assertEquals(0.5f, (state.stage as ShareStage.Preparing).fraction)
        progress.trySend(done())
        assertEquals(ShareStage.Ready(ShareTarget.File(done().share)), state.stage)
        viewModel.onEvent(ShareEvent.Launched)
        assertEquals(ShareStage.Done, state.stage)
    }

    @Test
    fun timeLeftIsEstimatedOnceThereIsEnoughToGoOn() {
        open("lecture.mp4", DetectedType.MP4)
        viewModel.onEvent(ShareEvent.ShareTapped)
        now = 500
        progress.trySend(CopyProgress.Working(0.1f))
        assertEquals(null, (state.stage as ShareStage.Preparing).secondsLeft)
        now = 2_000
        progress.trySend(CopyProgress.Working(0.25f))
        assertEquals(6, (state.stage as ShareStage.Preparing).secondsLeft)
    }

    @Test
    fun theOriginalOnlyGoesAfterTheWarning() {
        open("harbour.jpg", DetectedType.JPEG)
        viewModel.onEvent(ShareEvent.Selected(ShareMode.ORIGINAL))
        viewModel.onEvent(ShareEvent.ShareTapped)
        assertTrue(state.confirmOriginal)
        assertTrue(requests.isEmpty())

        viewModel.onEvent(ShareEvent.OriginalDismissed)
        assertFalse(state.confirmOriginal)
        assertTrue(requests.isEmpty())

        viewModel.onEvent(ShareEvent.ShareTapped)
        viewModel.onEvent(ShareEvent.OriginalConfirmed)
        assertEquals(listOf(ShareMode.ORIGINAL), requests)
        assertEquals(ShareStage.Preparing(ShareMode.ORIGINAL, null, null), state.stage)
    }

    @Test
    fun theLinkNeedsNoCopy() {
        open("harbour.jpg", DetectedType.JPEG)
        viewModel.onEvent(ShareEvent.Selected(ShareMode.LINK))
        viewModel.onEvent(ShareEvent.ShareTapped)
        assertEquals(ShareStage.Ready(ShareTarget.Link("https://podcasts.example/harbour.jpg")), state.stage)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun aFailedCopySaysWhyAndOffersAnotherWay() {
        open("broken.pdf", DetectedType.PDF)
        viewModel.onEvent(ShareEvent.ShareTapped)
        progress.close(CopyFailedException(CopyFailure.UNREADABLE))
        assertEquals(ShareStage.Failed(CopyFailure.UNREADABLE), state.stage)
        viewModel.onEvent(ShareEvent.TryAnotherWay)
        assertEquals(ShareStage.Choosing, state.stage)
    }

    @Test
    fun anUnexpectedErrorIsAPlainFailure() {
        open("broken.pdf", DetectedType.PDF)
        viewModel.onEvent(ShareEvent.ShareTapped)
        progress.close(IllegalStateException("boom"))
        assertEquals(ShareStage.Failed(CopyFailure.FAILED), state.stage)
    }

    @Test
    fun cancellingStopsTheCopy() {
        open("lecture.mp4", DetectedType.MP4)
        viewModel.onEvent(ShareEvent.ShareTapped)
        viewModel.onEvent(ShareEvent.CancelTapped)
        assertEquals(ShareStage.Choosing, state.stage)
        // Collection was cancelled, which cancels the copy's flow.
        assertTrue(progress.isClosedForSend)
    }

    @Test
    fun dismissingForgetsEverything() {
        open("harbour.jpg", DetectedType.JPEG)
        viewModel.onEvent(ShareEvent.ShareTapped)
        viewModel.onEvent(ShareEvent.Dismissed)
        assertEquals(ShareUiState(), state)
        assertTrue(progress.isClosedForSend)
    }

    @Test
    fun aDeletedFileClosesTheSheet() {
        viewModel.onEvent(ShareEvent.Opened("00000000-0000-0000-0000-000000000000"))
        assertEquals(ShareStage.Done, state.stage)
    }

    @Test
    fun reopeningStartsAfresh() {
        open("harbour.jpg", DetectedType.JPEG)
        viewModel.onEvent(ShareEvent.Selected(ShareMode.LINK))
        runBlocking { open("other.png", DetectedType.PNG) }
        assertEquals(ShareMode.SAFE_COPY, state.selected)
        assertEquals("other.png", state.file?.displayName)
    }
}
