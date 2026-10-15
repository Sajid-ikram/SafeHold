package dev.ikram.sandbox.core.data.db

import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.ScanError
import dev.ikram.sandbox.core.model.ScanStatus
import dev.ikram.sandbox.core.model.VaultFile
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanStatusStorageTest {
    private val at = Instant.parse("2026-10-15T12:00:00Z")

    private fun file(status: ScanStatus) = VaultFile(
        id = "0b0b9a5c-0c3a-4a8e-9a39-7a0b2f6a1c11",
        displayName = "f.pdf",
        sizeBytes = 1,
        detectedType = DetectedType.PDF,
        claimedType = null,
        category = FileCategory.PDF,
        sha256 = "00",
        sourceUrl = "https://example.org/f.pdf",
        sourceHost = "example.org",
        createdAt = at,
        scanStatus = status,
        isMismatch = false,
        mismatchAcknowledged = false,
        openFailed = false,
    )

    @Test
    fun everyStatusSurvivesTheDatabase() {
        listOf(
            ScanStatus.NotChecked,
            ScanStatus.Checking,
            ScanStatus.Clean(70, at),
            ScanStatus.Flagged(14, 70, at),
            ScanStatus.NotFound(at),
            ScanStatus.Error(ScanError.RATE_LIMITED),
        ).forEach { status -> assertEquals(status, file(status).toEntity().toModel().scanStatus) }
    }

    @Test
    fun rowsFromBeforeTheLookupReadAsNotChecked() {
        // Schema 1 rows have no engine count or time.
        val old = file(ScanStatus.NotChecked).toEntity().copy(scanStatus = "CLEAN")
        assertEquals(ScanStatus.NotChecked, old.toModel().scanStatus)
    }
}
