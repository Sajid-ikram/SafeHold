package dev.ikram.sandbox.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.ScanError
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
    // Added in schema 2, with the VirusTotal lookup.
    val scanEngineCount: Int? = null,
    val scanCheckedAtMillis: Long? = null,
    val scanError: String? = null,
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
        scanStatus = readScanStatus(),
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
    scanEngineCount = scanStatus.engines(),
    scanCheckedAtMillis = scanStatus.checkedAt()?.toEpochMilli(),
    scanError = (scanStatus as? ScanStatus.Error)?.reason?.name,
    isMismatch = isMismatch,
    mismatchAcknowledged = mismatchAcknowledged,
    openFailed = openFailed,
)

/** Unknown names (a type renamed in a later version) read as null rather than crashing. */
private fun detectedTypeOf(name: String): DetectedType? = DetectedType.entries.firstOrNull { it.name == name }

internal fun ScanStatus.storedName(): String = when (this) {
    ScanStatus.NotChecked -> NOT_CHECKED
    ScanStatus.Checking -> CHECKING
    is ScanStatus.Clean -> CLEAN
    is ScanStatus.Flagged -> FLAGGED
    is ScanStatus.NotFound -> NOT_FOUND
    is ScanStatus.Error -> ERROR
}

internal fun ScanStatus.engines(): Int? = when (this) {
    is ScanStatus.Clean -> engines
    is ScanStatus.Flagged -> engines
    else -> null
}

internal fun ScanStatus.checkedAt(): Instant? = when (this) {
    is ScanStatus.Clean -> checkedAt
    is ScanStatus.Flagged -> checkedAt
    is ScanStatus.NotFound -> checkedAt
    else -> null
}

/** Anything incomplete (a row from before a field existed) reads as not checked. */
private fun VaultFileEntity.readScanStatus(): ScanStatus {
    val checkedAt = scanCheckedAtMillis?.let(Instant::ofEpochMilli)
    return when (scanStatus) {
        CHECKING -> ScanStatus.Checking

        CLEAN -> if (checkedAt != null && scanEngineCount != null) {
            ScanStatus.Clean(scanEngineCount, checkedAt)
        } else {
            ScanStatus.NotChecked
        }

        FLAGGED -> if (checkedAt != null && scanEngineCount != null && scanFlaggedCount != null) {
            ScanStatus.Flagged(scanFlaggedCount, scanEngineCount, checkedAt)
        } else {
            ScanStatus.NotChecked
        }

        NOT_FOUND -> checkedAt?.let(ScanStatus::NotFound) ?: ScanStatus.NotChecked

        ERROR -> ScanStatus.Error(ScanError.entries.firstOrNull { it.name == scanError } ?: ScanError.NETWORK)

        else -> ScanStatus.NotChecked
    }
}

internal const val NOT_CHECKED = "NOT_CHECKED"
internal const val CHECKING = "CHECKING"
private const val CLEAN = "CLEAN"
private const val FLAGGED = "FLAGGED"
private const val NOT_FOUND = "NOT_FOUND"
private const val ERROR = "ERROR"

suspend fun VaultFileDao.setScanStatus(id: String, status: ScanStatus) = updateScan(
    id = id,
    status = status.storedName(),
    flagged = (status as? ScanStatus.Flagged)?.count,
    engines = status.engines(),
    checkedAt = status.checkedAt()?.toEpochMilli(),
    error = (status as? ScanStatus.Error)?.reason?.name,
)
