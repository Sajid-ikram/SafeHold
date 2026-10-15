package dev.ikram.sandbox.feature.viewer.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import javax.inject.Inject

/** Builds the player for one vault file. An interface so view model tests can hand in a fake. */
fun interface PlayerFactory {
    fun create(file: VaultFile): Player
}

/**
 * ExoPlayer cut down to what the viewing rules allow. Containers are parsed by Media3's
 * own (Kotlin and Java, so memory safe) extractors in this process; decoding happens in
 * the system's media codec process. On top of the defaults:
 *
 * - Files are read through [vaultMediaSources]: the vault file only, audio and video
 *   extractors only.
 * - Only the MediaCodec video and audio renderers: no image, subtitle, metadata or
 *   camera motion renderers, so nothing but picture and sound is ever acted on.
 */
@OptIn(UnstableApi::class)
class VaultPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storage: VaultStorage,
) : PlayerFactory {
    override fun create(file: VaultFile): Player {
        val player = ExoPlayer.Builder(context, PictureAndSoundRenderers(context), vaultMediaSources())
            .setAudioAttributes(audioAttributes(file), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(storage.fileFor(file.id))))
        player.prepare()
        return player
    }

    private fun audioAttributes(file: VaultFile): AudioAttributes {
        val isVideo = file.category == FileCategory.VIDEO
        return AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(if (isVideo) C.AUDIO_CONTENT_TYPE_MOVIE else C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
    }
}

@OptIn(UnstableApi::class)
private class PictureAndSoundRenderers(context: Context) : DefaultRenderersFactory(context) {
    override fun createRenderers(
        eventHandler: Handler,
        videoRendererEventListener: VideoRendererEventListener,
        audioRendererEventListener: AudioRendererEventListener,
        textRendererOutput: TextOutput,
        metadataRendererOutput: MetadataOutput,
    ): Array<Renderer> = super.createRenderers(
        eventHandler,
        videoRendererEventListener,
        audioRendererEventListener,
        textRendererOutput,
        metadataRendererOutput,
    ).filter { it is MediaCodecVideoRenderer || it is MediaCodecAudioRenderer }.toTypedArray()
}
