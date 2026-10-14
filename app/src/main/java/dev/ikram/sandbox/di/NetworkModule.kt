package dev.ikram.sandbox.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ikram.sandbox.core.data.download.DownloadLimits
import dev.ikram.sandbox.core.data.download.WebViewCookieJar
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

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
}
