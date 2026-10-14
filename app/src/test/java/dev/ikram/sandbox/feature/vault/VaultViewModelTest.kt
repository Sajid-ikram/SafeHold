package dev.ikram.sandbox.feature.vault

import app.cash.turbine.test
import dev.ikram.sandbox.MainDispatcherRule
import dev.ikram.sandbox.core.data.db.VaultFileEntity
import dev.ikram.sandbox.core.data.download.NewFilesCounter
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.DownloadState
import dev.ikram.sandbox.fakes.FakeDownloadQueue
import dev.ikram.sandbox.fakes.FakeVaultFileDao
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    @get:Rule val folder = TemporaryFolder()

    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 10, 14, 15, 0).toInstant(zone)
    private val clock = Clock.fixed(now, zone)

    private val dao = FakeVaultFileDao()
    private val queue = FakeDownloadQueue()
    private val newFiles = NewFilesCounter()
    private val storage by lazy { VaultStorage(folder.newFolder("files")) }
    private val viewModel by lazy {
        VaultViewModel(VaultRepository(dao, storage, Dispatchers.Unconfined), queue, newFiles, clock) { null }
    }
    private val state get() = viewModel.uiState.value

    private fun add(
        name: String,
        type: DetectedType = DetectedType.PDF,
        claimed: DetectedType? = null,
        at: Instant = now,
        mismatch: Boolean = false,
        host: String = "harbourlight.org",
    ): String {
        val id = UUID.randomUUID().toString()
        runBlocking {
            dao.insert(
                VaultFileEntity(
                    id = id,
                    displayName = name,
                    sizeBytes = 2_400_000,
                    detectedType = type.name,
                    claimedType = claimed?.name,
                    sha256 = "ab",
                    sourceUrl = "https://$host/$name",
                    sourceHost = host,
                    createdAtMillis = at.toEpochMilli(),
                    scanStatus = "NOT_CHECKED",
                    scanFlaggedCount = null,
                    isMismatch = mismatch,
                    mismatchAcknowledged = false,
                    openFailed = false,
                ),
            )
        }
        return id
    }

    @Test
    fun emptyVaultShowsTheEmptyState() {
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
    }

    @Test
    fun downloadingIntoAnEmptyVaultIsNotEmpty() {
        queue.emit(DownloadState.Running("d", "a.pdf", 10, 100))
        assertFalse(state.isEmpty)
        assertEquals(1, state.downloads.size)
    }

    @Test
    fun filesAreGroupedTodayYesterdayAndOlder() {
        add("today.pdf")
        add("yesterday.mp3", DetectedType.MP3, at = now.minusSeconds(DAY))
        add("old.jpg", DetectedType.JPEG, at = now.minusSeconds(5 * DAY))
        val labels = state.sections.map { it.label }
        assertEquals(DayLabel.Today, labels[0])
        assertEquals(DayLabel.Yesterday, labels[1])
        assertTrue(labels[2] is DayLabel.Day)
    }

    @Test
    fun searchMatchesNameHostAndType() {
        add("north-coast-map.pdf")
        add("interview.mp3", DetectedType.MP3, host = "podcasts.example")
        viewModel.onEvent(VaultEvent.QueryChanged("podcasts"))
        assertEquals(listOf("interview.mp3"), state.sections.flatMap { it.files }.map { it.displayName })
        viewModel.onEvent(VaultEvent.QueryChanged("PDF document"))
        assertEquals(listOf("north-coast-map.pdf"), state.sections.flatMap { it.files }.map { it.displayName })
    }

    @Test
    fun blockedFilterIncludesUnknownFiles() {
        add("a.pdf")
        add("app.apk", DetectedType.APK)
        add("mystery.bin", DetectedType.UNKNOWN)
        viewModel.onEvent(VaultEvent.FilterSelected(VaultFilter.BLOCKED))
        assertEquals(
            setOf("app.apk", "mystery.bin"),
            state.sections.flatMap {
                it.files
            }.map { it.displayName }.toSet(),
        )
    }

    @Test
    fun tappingAFileOpensItsDetails() {
        val id = add("a.pdf")
        viewModel.onEvent(VaultEvent.FileTapped(id))
        assertEquals(id, state.details?.id)
        viewModel.onEvent(VaultEvent.DetailsDismissed)
        assertNull(state.details)
    }

    @Test
    fun aDisguisedFileWarnsFirstAndOnlyOnce() {
        val id = add("movie.mp4", DetectedType.APK, claimed = DetectedType.MP4, mismatch = true)
        viewModel.onEvent(VaultEvent.FileTapped(id))
        assertEquals(id, state.mismatch?.id)
        assertNull(state.details)

        viewModel.onEvent(VaultEvent.MismatchAccepted) // "Keep it locked"
        assertNull(state.mismatch)
        assertEquals(id, state.details?.id)

        viewModel.onEvent(VaultEvent.DetailsDismissed)
        viewModel.onEvent(VaultEvent.FileTapped(id))
        assertNull(state.mismatch)
        assertEquals(id, state.details?.id)
    }

    @Test
    fun anOpenableDisguisedFileOpensAfterTheWarning() = runTest {
        val id = add("report.pdf", DetectedType.TEXT, claimed = DetectedType.PDF, mismatch = true)
        viewModel.effects.test {
            viewModel.onEvent(VaultEvent.FileTapped(id))
            viewModel.onEvent(VaultEvent.MismatchAccepted)
            assertEquals(VaultEffect.OpenViewer(id), awaitItem())
        }
    }

    @Test
    fun blockedFilesNeverOpen() = runTest {
        val id = add("app.apk", DetectedType.APK)
        viewModel.effects.test {
            viewModel.onEvent(VaultEvent.OpenTapped(id))
            expectNoEvents()
        }
    }

    @Test
    fun openingAViewableFile() = runTest {
        val id = add("a.pdf")
        viewModel.onEvent(VaultEvent.FileTapped(id))
        viewModel.effects.test {
            viewModel.onEvent(VaultEvent.OpenTapped(id))
            assertEquals(VaultEffect.OpenViewer(id), awaitItem())
        }
        assertNull(state.details)
    }

    @Test
    fun deleteAsksFirstThenRemovesTheFile() {
        val id = add("a.pdf")
        viewModel.onEvent(VaultEvent.DeleteTapped(id))
        assertEquals(id, state.confirmDelete?.id)
        viewModel.onEvent(VaultEvent.DeleteConfirmed)
        assertNull(state.confirmDelete)
        assertTrue(state.isEmpty)
        assertEquals(VaultMessage.Deleted("a.pdf"), state.message)
    }

    @Test
    fun mismatchDeleteRemovesTheFile() {
        val id = add("movie.mp4", DetectedType.APK, claimed = DetectedType.MP4, mismatch = true)
        viewModel.onEvent(VaultEvent.FileTapped(id))
        viewModel.onEvent(VaultEvent.MismatchDelete)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun cancellingADownloadGoesToTheQueue() {
        viewModel.onEvent(VaultEvent.CancelDownload("d1"))
        assertEquals(listOf("d1"), queue.cancelled)
    }

    @Test
    fun visibleVaultClearsTheBadge() {
        newFiles.increment()
        viewModel.onEvent(VaultEvent.VisibilityChanged(true))
        assertEquals(0, newFiles.count.value)
        newFiles.increment()
        assertEquals(0, newFiles.count.value)
        viewModel.onEvent(VaultEvent.VisibilityChanged(false))
        newFiles.increment()
        assertEquals(1, newFiles.count.value)
    }

    private companion object {
        const val DAY = 24L * 60 * 60
    }
}
