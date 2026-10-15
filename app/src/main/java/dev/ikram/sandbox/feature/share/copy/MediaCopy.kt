package dev.ikram.sandbox.feature.share.copy

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.Codec
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.ShareNames
import dev.ikram.sandbox.feature.viewer.media.vaultMediaSources
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Video and audio decoded and encoded again with Media3 Transformer: H.264 and AAC in a
 * new MP4. Reading goes through the same locked-down sources as the player, every track
 * is re-encoded even when it could be copied as it is, and the new file gets none of the
 * old one's metadata (location, dates, tags). Only the picture and sound come across.
 */
@OptIn(UnstableApi::class)
class MediaCopy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val files: ShareFiles,
) {
    fun copy(file: VaultFile): Flow<CopyProgress> = callbackFlow {
        val type = if (file.category == FileCategory.VIDEO) DetectedType.MP4 else DetectedType.M4A
        val target = files.create(type.extension)
        var finished = false
        val transformer = transformer(
            object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finished = true
                    val share = PreparedShare(target, ShareNames.forShare(file.displayName, type), type.mimeType)
                    trySend(CopyProgress.Done(share))
                    close()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    close(CopyFailedException(reasonFor(exportException), exportException))
                }
            },
        )
        transformer.start(
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(files.source(file)))).build(),
            target.path,
        )
        val progress = ProgressHolder()
        val poller = launch {
            while (isActive) {
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(CopyProgress.Working(progress.progress / PERCENT))
                }
                delay(POLL_MS)
            }
        }
        awaitClose {
            poller.cancel()
            if (!finished) {
                transformer.cancel()
                target.delete()
            }
        }
    }.flowOn(Dispatchers.Main) // Transformer is driven from the thread it was built on.

    private fun transformer(listener: Transformer.Listener): Transformer = Transformer.Builder(context)
        .setAssetLoaderFactory(
            ExoPlayerAssetLoader.Factory(
                context,
                DefaultDecoderFactory.Builder(context).build(),
                Clock.DEFAULT,
                vaultMediaSources(),
            ),
        )
        .setEncoderFactory(AlwaysEncode(DefaultEncoderFactory.Builder(context).build()))
        .setMuxerFactory(WithoutMetadata(DefaultMuxer.Factory()))
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(listener)
        .build()

    /** Transformer copies tracks untouched when it can; a safe copy never does. */
    private class AlwaysEncode(base: Codec.EncoderFactory) : Codec.EncoderFactory by base {
        override fun audioNeedsEncoding(): Boolean = true

        override fun videoNeedsEncoding(): Boolean = true
    }

    /**
     * Drops the metadata Transformer would carry over from the original. The muxer still
     * records the picture's rotation itself, so portrait videos stay upright.
     */
    private class WithoutMetadata(private val base: Muxer.Factory) : Muxer.Factory by base {
        override fun create(path: String): Muxer = object : Muxer by base.create(path) {
            override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit
        }
    }

    private companion object {
        const val POLL_MS = 250L
        const val PERCENT = 100f

        fun reasonFor(exception: ExportException): CopyFailure = when (exception.errorCode) {
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FAILED,
            ExportException.ERROR_CODE_IO_UNSPECIFIED,
            -> CopyFailure.UNREADABLE

            else -> CopyFailure.FAILED
        }
    }
}
