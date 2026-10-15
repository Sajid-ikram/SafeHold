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
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.amr.AmrExtractor
import androidx.media3.extractor.avi.AviExtractor
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.flv.FlvExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.ts.Ac3Extractor
import androidx.media3.extractor.ts.Ac4Extractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.PsExtractor
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.wav.WavExtractor
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
 * - The only data source is the vault file itself, so nothing inside a media file can
 *   make the player reach the network or any other file.
 * - Only audio and video extractors: Media3 also ships image extractors, and images are
 *   only ever decoded by the isolated renderer (golden rule 6).
 * - Only the MediaCodec video and audio renderers: no image, subtitle, metadata or
 *   camera motion renderers, so nothing but picture and sound is ever acted on.
 */
@OptIn(UnstableApi::class)
class VaultPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storage: VaultStorage,
) : PlayerFactory {
    override fun create(file: VaultFile): Player {
        val mediaSource = ProgressiveMediaSource.Factory(FileDataSource.Factory(), AudioVideoExtractors())
        val player = ExoPlayer.Builder(context, PictureAndSoundRenderers(context), mediaSource)
            .setAudioAttributes(audioAttributes(file), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(storage.fileFor(file.id))))
        player.prepare()
        return player
    }

    private fun audioAttributes(file: VaultFile): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(
            if (file.category ==
                FileCategory.VIDEO
            ) {
                C.AUDIO_CONTENT_TYPE_MOVIE
            } else {
                C.AUDIO_CONTENT_TYPE_MUSIC
            },
        )
        .build()

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

/** Media3's default extractors, minus everything that is not audio or video. An allowlist, so new ones stay out. */
@OptIn(UnstableApi::class)
private class AudioVideoExtractors : ExtractorsFactory {
    private val defaults = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> = defaults.createExtractors().audioVideoOnly()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        defaults.createExtractors(uri, responseHeaders).audioVideoOnly()

    private fun Array<Extractor>.audioVideoOnly(): Array<Extractor> = filter { it.javaClass in ALLOWED }.toTypedArray()

    private companion object {
        val ALLOWED: Set<Class<out Extractor>> = setOf(
            Mp4Extractor::class.java,
            FragmentedMp4Extractor::class.java,
            MatroskaExtractor::class.java,
            AviExtractor::class.java,
            FlvExtractor::class.java,
            TsExtractor::class.java,
            PsExtractor::class.java,
            Mp3Extractor::class.java,
            AdtsExtractor::class.java,
            Ac3Extractor::class.java,
            Ac4Extractor::class.java,
            OggExtractor::class.java,
            FlacExtractor::class.java,
            WavExtractor::class.java,
            AmrExtractor::class.java,
        )
    }
}
