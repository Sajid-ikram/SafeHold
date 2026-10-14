package dev.ikram.sandbox.core.model

import java.time.Instant

/**
 * A downloaded file held in the vault. The bytes live at `filesDir/vault/<id>.bin`;
 * [displayName] is sanitized server text for display only and is never used as a path.
 */
data class VaultFile(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val detectedType: DetectedType,
    /** What the file name or server claimed, or null when neither said anything we recognise. */
    val claimedType: DetectedType?,
    val category: FileCategory,
    /** Lowercase hex. */
    val sha256: String,
    val sourceUrl: String,
    val sourceHost: String,
    val createdAt: Instant,
    val scanStatus: ScanStatus,
    /** The bytes disagree with the claimed type, so the mismatch warning shows on first open. */
    val isMismatch: Boolean,
    val mismatchAcknowledged: Boolean,
    /** The isolated renderer crashed or timed out on this file. It is not offered for opening again. */
    val openFailed: Boolean,
)
