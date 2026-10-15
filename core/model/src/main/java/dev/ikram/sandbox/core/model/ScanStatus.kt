package dev.ikram.sandbox.core.model

import java.time.Instant

/** Result of looking a file's SHA-256 up on VirusTotal. The file itself is never uploaded. */
sealed interface ScanStatus {
    data object NotChecked : ScanStatus

    data object Checking : ScanStatus

    /** None of the [engines] that looked at it reported anything. */
    data class Clean(val engines: Int, val checkedAt: Instant) : ScanStatus

    /** [count] of the [engines] reported the file as malicious or suspicious. */
    data class Flagged(val count: Int, val engines: Int, val checkedAt: Instant) : ScanStatus

    /** VirusTotal has never seen this hash. Not the same as clean. */
    data class NotFound(val checkedAt: Instant) : ScanStatus

    data class Error(val reason: ScanError) : ScanStatus
}

enum class ScanError {
    /** No VirusTotal key has been added in settings. */
    NO_KEY,

    /** VirusTotal turned the key down. */
    BAD_KEY,

    /** Too many lookups: the free key allows a few a minute. */
    RATE_LIMITED,

    /** No connection, or VirusTotal did not answer sensibly. */
    NETWORK,
}
