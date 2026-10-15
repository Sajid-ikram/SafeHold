package dev.ikram.sandbox.core.data.scan

import dev.ikram.sandbox.core.data.db.VaultFileDao
import dev.ikram.sandbox.core.data.db.setScanStatus
import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.model.ScanError
import dev.ikram.sandbox.core.model.ScanStatus
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException

/**
 * Looks vault files up on VirusTotal by hash and records the answer on the file. Lookups
 * run one at a time, spaced out to stay inside the free key's few-a-minute allowance.
 */
@Singleton
class ScanRepository @Inject constructor(
    private val api: VirusTotalApi,
    private val dao: VaultFileDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val turn = Mutex()
    private var lastLookupAt: Long? = null

    /** Checks in the background, so the answer arrives even if the screen that asked has gone. */
    fun requestCheck(fileId: String) {
        scope.launch { check(fileId) }
    }

    suspend fun check(fileId: String) {
        val file = dao.get(fileId) ?: return
        val key = settings.current().virusTotalKey
        if (key == null) {
            dao.setScanStatus(fileId, ScanStatus.Error(ScanError.NO_KEY))
            return
        }
        // Our own hash, but it goes into a URL: refuse anything that is not one.
        if (!SHA256.matches(file.sha256)) return
        dao.setScanStatus(fileId, ScanStatus.Checking)
        val result = turn.withLock {
            waitForTurn()
            lookUp(file.sha256, key)
        }
        dao.setScanStatus(fileId, result)
    }

    private suspend fun waitForTurn() {
        val last = lastLookupAt
        if (last != null) {
            val wait = last + SPACING_MILLIS - clock.millis()
            if (wait > 0) delay(wait)
        }
        lastLookupAt = clock.millis()
    }

    private suspend fun lookUp(sha256: String, key: String): ScanStatus = try {
        val response = api.file(sha256, key)
        val stats = response.body()?.data?.attributes?.stats
        when {
            response.code() == HTTP_NOT_FOUND -> ScanStatus.NotFound(clock.instant())

            response.code() == HTTP_UNAUTHORIZED || response.code() == HTTP_FORBIDDEN -> ScanStatus.Error(
                ScanError.BAD_KEY,
            )

            response.code() == HTTP_TOO_MANY -> ScanStatus.Error(ScanError.RATE_LIMITED)

            !response.isSuccessful || stats == null -> ScanStatus.Error(ScanError.NETWORK)

            stats.flagged > 0 -> ScanStatus.Flagged(stats.flagged, stats.engines, clock.instant())

            else -> ScanStatus.Clean(stats.engines, clock.instant())
        }
    } catch (@Suppress("SwallowedException") offline: IOException) {
        ScanStatus.Error(ScanError.NETWORK)
    } catch (@Suppress("SwallowedException") garbled: SerializationException) {
        ScanStatus.Error(ScanError.NETWORK)
    }

    companion object {
        /** The free key allows four lookups a minute. */
        const val SPACING_MILLIS = 15_000L
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY = 429
    }
}
