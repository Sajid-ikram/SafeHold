package dev.ikram.sandbox.feature.viewer.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
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

/**
 * How every media file in the vault is read, for playing and for safe copies alike:
 * from the vault file only (no network, no other files), by audio and video extractors
 * only. Media3 also ships image extractors, and images are only ever decoded by the
 * isolated renderer (golden rule 6).
 */
@OptIn(UnstableApi::class)
fun vaultMediaSources(): MediaSource.Factory =
    ProgressiveMediaSource.Factory(FileDataSource.Factory(), AudioVideoExtractors())

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
