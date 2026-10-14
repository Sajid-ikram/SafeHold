package dev.ikram.sandbox.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.data.download.DownloadQueue
import dev.ikram.sandbox.core.data.download.VaultDownloader
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun downloadQueue(downloader: VaultDownloader): DownloadQueue

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

        /** "Today" and "Yesterday" in the vault come from this, so tests can pin the date. */
        @Provides
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}
