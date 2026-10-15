package dev.ikram.sandbox.feature.share.copy

import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.ScanStatus
import dev.ikram.sandbox.core.model.ShareMode
import dev.ikram.sandbox.core.model.VaultFile
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The copier checks the rules again, whatever the screen offered. */
class VaultShareCopierTest {
    private val images = mockk<ImageCopy>()
    private val pdfs = mockk<PdfCopy>()
    private val texts = mockk<TextCopy>()
    private val media = mockk<MediaCopy>()
    private val originals = mockk<OriginalCopy>()
    private val copier = VaultShareCopier(images, pdfs, texts, media, originals)

    private val imageFlow = flowOf<CopyProgress>(CopyProgress.Working(null))
    private val originalFlow = flowOf<CopyProgress>(CopyProgress.Working(0.5f))

    init {
        every { images.copy(any()) } returns imageFlow
        every { pdfs.copy(any()) } returns flowOf()
        every { texts.copy(any()) } returns flowOf()
        every { media.copy(any()) } returns flowOf()
        every { originals.copy(any()) } returns originalFlow
    }

    private fun file(type: DetectedType, openFailed: Boolean = false) = VaultFile(
        id = "0b0b9a5c-0c3a-4a8e-9a39-7a0b2f6a1c11",
        displayName = "file",
        sizeBytes = 1,
        detectedType = type,
        claimedType = null,
        category = TypePolicy.categoryOf(type),
        sha256 = "00",
        sourceUrl = "https://example.org/file",
        sourceHost = "example.org",
        createdAt = Instant.EPOCH,
        scanStatus = ScanStatus.NotChecked,
        isMismatch = false,
        mismatchAcknowledged = false,
        openFailed = openFailed,
    )

    private fun refusal(flow: Flow<CopyProgress>): CopyFailure = runBlocking {
        try {
            flow.first()
            error("Expected a refusal")
        } catch (refused: CopyFailedException) {
            refused.reason
        }
    }

    @Test
    fun safeCopiesGoToTheCopyForTheirKind() {
        assertSame(imageFlow, copier.copy(file(DetectedType.PNG), ShareMode.SAFE_COPY))
    }

    @Test
    fun originalsOfAllowedFilesAreCopied() {
        assertSame(originalFlow, copier.copy(file(DetectedType.ZIP), ShareMode.ORIGINAL))
    }

    @Test
    fun blockedAndUnknownFilesAreRefusedEveryWay() {
        listOf(DetectedType.APK, DetectedType.PE_EXE, DetectedType.SCRIPT, DetectedType.UNKNOWN).forEach { type ->
            assertEquals(CopyFailure.NOT_ALLOWED, refusal(copier.copy(file(type), ShareMode.ORIGINAL)))
            assertEquals(CopyFailure.NOT_ALLOWED, refusal(copier.copy(file(type), ShareMode.SAFE_COPY)))
        }
    }

    @Test
    fun archivesHaveNoSafeCopy() {
        assertEquals(CopyFailure.NOT_ALLOWED, refusal(copier.copy(file(DetectedType.ZIP), ShareMode.SAFE_COPY)))
    }

    @Test
    fun aFileThatCrashedTheRendererGoesNowhere() {
        val crashed = file(DetectedType.PNG, openFailed = true)
        assertEquals(CopyFailure.NOT_ALLOWED, refusal(copier.copy(crashed, ShareMode.SAFE_COPY)))
        assertEquals(CopyFailure.NOT_ALLOWED, refusal(copier.copy(crashed, ShareMode.ORIGINAL)))
    }

    @Test
    fun linksAreNotFiles() {
        assertEquals(CopyFailure.NOT_ALLOWED, refusal(copier.copy(file(DetectedType.PNG), ShareMode.LINK)))
    }
}
