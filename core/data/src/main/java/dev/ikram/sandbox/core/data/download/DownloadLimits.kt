package dev.ikram.sandbox.core.data.download

import javax.inject.Inject

class DownloadLimits(
    val maxBytes: Long,
    /** Free space to leave on the device after a download of known size. */
    val reserveBytes: Long,
    /** Off only in tests, which serve plain http. */
    val requireHttps: Boolean,
) {
    @Inject
    constructor() : this(maxBytes = MAX_BYTES, reserveBytes = RESERVE_BYTES, requireHttps = true)

    companion object {
        const val MAX_BYTES = 2L * 1024 * 1024 * 1024
        const val RESERVE_BYTES = 50L * 1024 * 1024
        const val CONNECT_TIMEOUT_SECONDS = 15L
        const val READ_TIMEOUT_SECONDS = 30L
    }
}
