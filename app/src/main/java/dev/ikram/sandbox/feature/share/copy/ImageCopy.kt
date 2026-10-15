package dev.ikram.sandbox.feature.share.copy

import android.graphics.Bitmap
import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.ShareNames
import dev.ikram.sandbox.renderer.RendererCrashedException
import dev.ikram.sandbox.renderer.RendererLimits
import dev.ikram.sandbox.renderer.SandboxRenderer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * A picture rebuilt from its pixels alone. The isolated renderer decodes it (turned
 * upright) and hands back nothing but checked pixels; this process writes a brand new
 * JPEG or PNG from them. EXIF, location, camera details, comments and anything appended
 * to the file cannot survive, because none of it was ever read into the new file.
 */
class ImageCopy @Inject constructor(
    private val renderer: SandboxRenderer,
    private val files: ShareFiles,
    private val repository: VaultRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun copy(file: VaultFile): Flow<CopyProgress> = flow {
        emit(CopyProgress.Working(null))
        val bitmap = guardRenderer(repository, file) {
            renderer.decodeImage(files.source(file), RendererLimits.MAX_OUTPUT_SIDE, RendererLimits.MAX_OUTPUT_SIDE)
        } ?: throw CopyFailedException(CopyFailure.UNREADABLE)
        emit(CopyProgress.Working(DECODED))
        val type = outputType(file.detectedType)
        val share = try {
            files.writing(type.extension) { target ->
                target.outputStream().use { out ->
                    val format = if (type == DetectedType.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    check(bitmap.compress(format, JPEG_QUALITY, out)) { "Could not encode the picture" }
                }
                PreparedShare(target, ShareNames.forShare(file.displayName, type), type.mimeType)
            }
        } finally {
            bitmap.recycle()
        }
        emit(CopyProgress.Done(share))
    }.flowOn(io)

    private companion object {
        const val JPEG_QUALITY = 92
        const val DECODED = 0.6f

        /** Photos stay JPEG; anything that may be transparent or drawn becomes PNG. */
        fun outputType(source: DetectedType): DetectedType = when (source) {
            DetectedType.JPEG, DetectedType.HEIC, DetectedType.AVIF -> DetectedType.JPEG
            else -> DetectedType.PNG
        }
    }
}

/** A renderer crash or hang marks the file unsafe, as in the viewers, and ends the copy. */
internal suspend fun <T> guardRenderer(repository: VaultRepository, file: VaultFile, block: suspend () -> T): T = try {
    block()
} catch (crashed: RendererCrashedException) {
    repository.markOpenFailed(file.id)
    throw CopyFailedException(CopyFailure.UNSAFE, crashed)
}
