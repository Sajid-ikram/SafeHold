package dev.ikram.sandbox.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.ScanStatus
import dev.ikram.sandbox.core.model.VaultFile
import java.time.Instant

/**
 * One vault file. Types are stored by name; the category is NOT stored but derived from
 * the detected type on every read, so a change to the policy applies to old files too.
 */
@Entity(tableName = "vault_files")
data class VaultFileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val detectedType: String,
    val claimedType: String?,
    val sha256: String,
    val sourceUrl: String,
    val sourceHost: String,
    val createdAtMillis: Long,
    val scanStatus: String,
    val scanFlaggedCount: Int?,
    val isMismatch: Boolean,
    val mismatchAcknowledged: Boolean,
    val openFailed: Boolean,
)

fun VaultFileEntity.toModel(): VaultFile {
    val detected = detectedTypeOf(detectedType) ?: DetectedType.UNKNOWN
    return VaultFile(
        id = id,
        displayName = displayName,
        sizeBytes = sizeBytes,
        detectedType = detected,
        claimedType = claimedType?.let(::detectedTypeOf),
        category = TypePolicy.categoryOf(detected),
        sha256 = sha256,
        sourceUrl = sourceUrl,
        sourceHost = sourceHost,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        scanStatus = scanStatusOf(scanStatus, scanFlaggedCount),
        isMismatch = isMismatch,
        mismatchAcknowledged = mismatchAcknowledged,
        openFailed = openFailed,
    )
}

fun VaultFile.toEntity(): VaultFileEntity = VaultFileEntity(
    id = id,
    displayName = displayName,
    sizeBytes = sizeBytes,
    detectedType = detectedType.name,
    claimedType = claimedType?.name,
    sha256 = sha256,
    sourceUrl = sourceUrl,
    sourceHost = sourceHost,
    createdAtMillis = createdAt.toEpochMilli(),
    scanStatus = scanStatus.storedName(),
    scanFlaggedCount = (scanStatus as? ScanStatus.Flagged)?.count,
    isMismatch = isMismatch,
    mismatchAcknowledged = mismatchAcknowledged,
    openFailed = openFailed,
)

/** Unknown names (a type renamed in a later version) read as null rather than crashing. */
private fun detectedTypeOf(name: String): DetectedType? = DetectedType.entries.firstOrNull { it.name == name }

internal fun ScanStatus.storedName(): String = when (this) {
    ScanStatus.NotChecked -> "NOT_CHECKED"
    ScanStatus.Checking -> "CHECKING"
    ScanStatus.Clean -> "CLEAN"
    is ScanStatus.Flagged -> "FLAGGED"
    ScanStatus.NotFound -> "NOT_FOUND"
    ScanStatus.Error -> "ERROR"
}

private fun scanStatusOf(name: String, flaggedCount: Int?): ScanStatus = when (name) {
    "CHECKING" -> ScanStatus.Checking
    "CLEAN" -> ScanStatus.Clean
    "FLAGGED" -> ScanStatus.Flagged(flaggedCount ?: 0)
    "NOT_FOUND" -> ScanStatus.NotFound
    "ERROR" -> ScanStatus.Error
    else -> ScanStatus.NotChecked
}
