package dev.ikram.sandbox.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ikram.sandbox.core.data.download.DownloadLimits
import dev.ikram.sandbox.core.data.download.WebViewCookieJar
import dev.ikram.sandbox.core.data.scan.VirusTotalApi
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /**
     * Downloads share the browser's cookies. Plain http is refused twice over: the
     * platform's default network security config forbids cleartext, and the downloader
     * checks the final URL after redirects.
     */
    @Provides
    @Singleton
    fun okHttpClient(cookieJar: WebViewCookieJar): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(DownloadLimits.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(DownloadLimits.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Hash lookups get a client of their own: the browser's cookies never go to
     * VirusTotal, and with redirects off the key can never be carried to another host.
     */
    @Provides
    @Singleton
    fun virusTotalApi(downloads: OkHttpClient): VirusTotalApi {
        val client = downloads.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        val json = Json { ignoreUnknownKeys = true }
        return Retrofit.Builder()
            .baseUrl(VirusTotalApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VirusTotalApi::class.java)
    }

    private const val LOOKUP_TIMEOUT_SECONDS = 20L
}
