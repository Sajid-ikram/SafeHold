package dev.ikram.sandbox.core.model

/** Result of looking a file's SHA-256 up on VirusTotal. The file itself is never uploaded. */
sealed interface ScanStatus {
    data object NotChecked : ScanStatus

    data object Checking : ScanStatus

    data object Clean : ScanStatus

    /** [count] engines reported the file as malicious or suspicious. */
    data class Flagged(val count: Int) : ScanStatus

    /** VirusTotal has never seen this hash. Not the same as clean. */
    data object NotFound : ScanStatus

    data object Error : ScanStatus
}
