package dev.ikram.sandbox.core.data.scan

import dev.ikram.sandbox.core.data.FakePreferencesStore
import dev.ikram.sandbox.core.data.FakeVaultFileDao
import dev.ikram.sandbox.core.data.db.VaultFileEntity
import dev.ikram.sandbox.core.data.db.toModel
import dev.ikram.sandbox.core.data.settings.SettingsRepository
import dev.ikram.sandbox.core.model.ScanError
import dev.ikram.sandbox.core.model.ScanStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class ScanRepositoryTest {
    private val server = MockWebServer()
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val dao = FakeVaultFileDao()
    private val settings = SettingsRepository(FakePreferencesStore())
    private val sha = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
    private val key = "c".repeat(64)
    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun TestScope.repository(): ScanRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(JSON.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VirusTotalApi::class.java)
        return ScanRepository(api, dao, settings, Clock.fixed(now, ZoneOffset.UTC), backgroundScope)
    }

    private suspend fun stored(id: String = "f1", sha256: String = sha): String {
        dao.insert(
            VaultFileEntity(
                id = id,
                displayName = "file.apk",
                sizeBytes = 1,
                detectedType = "APK",
                claimedType = null,
                sha256 = sha256,
                sourceUrl = "https://example.org/file.apk",
                sourceHost = "example.org",
                createdAtMillis = 0,
                scanStatus = "NOT_CHECKED",
                scanFlaggedCount = null,
                isMismatch = false,
                mismatchAcknowledged = false,
                openFailed = false,
            ),
        )
        return id
    }

    private suspend fun status(id: String = "f1"): ScanStatus = dao.get(id)!!.toModel().scanStatus

    private fun report(malicious: Int, suspicious: Int, undetected: Int, harmless: Int): MockResponse {
        val stats = "\"malicious\":$malicious,\"suspicious\":$suspicious,\"undetected\":$undetected," +
            "\"harmless\":$harmless,\"timeout\":1,\"type-unsupported\":3,\"failure\":0"
        val body = "{\"data\":{\"id\":\"$sha\",\"type\":\"file\",\"attributes\":" +
            "{\"last_analysis_stats\":{$stats},\"names\":[\"x\"]}}}"
        return MockResponse(code = 200, body = body)
    }

    @Test
    fun aCleanReportCountsTheEnginesThatAnswered() = runTest {
        settings.setVirusTotalKey(key)
        server.enqueue(report(0, 0, 60, 10))
        repository().check(stored())
        assertEquals(ScanStatus.Clean(engines = 70, checkedAt = now), status())
    }

    @Test
    fun maliciousAndSuspiciousBothCountAsFlags() = runTest {
        settings.setVirusTotalKey(key)
        server.enqueue(report(12, 2, 50, 6))
        repository().check(stored())
        assertEquals(ScanStatus.Flagged(count = 14, engines = 70, checkedAt = now), status())
    }

    @Test
    fun onlyTheHashAndKeyAreSent() = runTest {
        settings.setVirusTotalKey(key)
        server.enqueue(report(0, 0, 1, 0))
        repository().check(stored())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v3/files/$sha", request.target)
        assertEquals(key, request.headers["x-apikey"])
        assertNull(request.headers["Cookie"])
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun anUnknownHashIsNotClean() = runTest {
        settings.setVirusTotalKey(key)
        server.enqueue(MockResponse(code = 404, body = "{\"error\":{\"code\":\"NotFoundError\"}}"))
        repository().check(stored())
        assertEquals(ScanStatus.NotFound(now), status())
    }

    @Test
    fun failuresSayWhy() = runTest {
        settings.setVirusTotalKey(key)
        val repository = repository()
        mapOf(
            401 to ScanError.BAD_KEY,
            403 to ScanError.BAD_KEY,
            429 to ScanError.RATE_LIMITED,
            500 to ScanError.NETWORK,
        ).forEach { (code, reason) ->
            server.enqueue(MockResponse(code = code))
            val id = stored(id = "f$code")
            repository.check(id)
            assertEquals(ScanStatus.Error(reason), status(id))
        }
    }

    @Test
    fun aGarbledAnswerIsAFailureNotACrash() = runTest {
        settings.setVirusTotalKey(key)
        server.enqueue(MockResponse(code = 200, body = "{not json"))
        repository().check(stored())
        assertEquals(ScanStatus.Error(ScanError.NETWORK), status())
    }

    @Test
    fun withoutAKeyNothingIsSent() = runTest {
        repository().check(stored())
        assertEquals(ScanStatus.Error(ScanError.NO_KEY), status())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun somethingThatIsNotAHashNeverReachesAUrl() = runTest {
        settings.setVirusTotalKey(key)
        repository().check(stored(sha256 = "../../users/me"))
        assertEquals(0, server.requestCount)
        assertEquals(ScanStatus.NotChecked, status())
    }

    @Test
    fun lookupsAreSpacedForTheFreeKey() = runTest {
        settings.setVirusTotalKey(key)
        server.enqueue(report(0, 0, 1, 0))
        server.enqueue(report(0, 0, 1, 0))
        val repository = repository()
        repository.check(stored("a"))
        val first = currentTime
        repository.check(stored("b"))
        assertTrue(currentTime - first >= ScanRepository.SPACING_MILLIS)
    }
}
