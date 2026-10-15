package dev.ikram.sandbox.core.data.scan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * The one VirusTotal call SafeHold makes: what is already known about a hash. Files are
 * never uploaded, and nothing but the hash and the person's own key is ever sent.
 */
interface VirusTotalApi {
    @GET("api/v3/files/{sha256}")
    suspend fun file(@Path("sha256") sha256: String, @Header("x-apikey") key: String): Response<FileReport>

    companion object {
        const val BASE_URL = "https://www.virustotal.com/"
    }
}

@Serializable
data class FileReport(val data: Data) {
    @Serializable
    data class Data(val attributes: Attributes)

    @Serializable
    data class Attributes(@SerialName("last_analysis_stats") val stats: AnalysisStats = AnalysisStats())
}

/** Engines by verdict. Those that timed out, failed or could not read the type are not counted. */
@Serializable
data class AnalysisStats(
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val undetected: Int = 0,
    val harmless: Int = 0,
) {
    val flagged: Int get() = malicious + suspicious
    val engines: Int get() = malicious + suspicious + undetected + harmless
}
