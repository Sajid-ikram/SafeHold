package dev.ikram.sandbox.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ikram.sandbox.core.data.di.ApplicationScope
import dev.ikram.sandbox.core.data.di.DefaultDispatcher
import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.data.download.DownloadQueue
import dev.ikram.sandbox.core.data.download.VaultDownloader
import dev.ikram.sandbox.feature.share.copy.ShareCopier
import dev.ikram.sandbox.feature.share.copy.VaultShareCopier
import dev.ikram.sandbox.feature.viewer.image.RenderedImages
import dev.ikram.sandbox.feature.viewer.image.ThumbnailSource
import dev.ikram.sandbox.feature.viewer.media.PlayerFactory
import dev.ikram.sandbox.feature.viewer.media.VaultPlayerFactory
import dev.ikram.sandbox.renderer.SandboxRenderer
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

    @Binds
    abstract fun thumbnailSource(images: RenderedImages): ThumbnailSource

    @Binds
    abstract fun playerFactory(factory: VaultPlayerFactory): PlayerFactory

    @Binds
    abstract fun shareCopier(copier: VaultShareCopier): ShareCopier

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @IoDispatcher
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @DefaultDispatcher
        fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

        /** The one way into the isolated renderer. Singleton, so there is only ever one renderer process. */
        @Provides
        @Singleton
        fun sandboxRenderer(@ApplicationContext context: Context): SandboxRenderer = SandboxRenderer(context)

        /** "Today" and "Yesterday" in the vault come from this, so tests can pin the date. */
        @Provides
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}
