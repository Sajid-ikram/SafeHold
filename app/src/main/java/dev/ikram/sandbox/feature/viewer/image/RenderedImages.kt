package dev.ikram.sandbox.feature.viewer.image

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.renderer.RendererCrashedException
import dev.ikram.sandbox.renderer.SandboxRenderer
import javax.inject.Inject
import javax.inject.Singleton

sealed interface RenderResult {
    data class Ready(val image: ImageBitmap) : RenderResult

    /** The bytes are an image type the platform cannot decode, or one too large to try. */
    data object NotDecodable : RenderResult

    /** The renderer crashed or hung on this file. It is marked and never tried again. */
    data object Unsafe : RenderResult
}

/** Small previews for lists. Null when there is none to show. */
fun interface ThumbnailSource {
    suspend fun thumbnail(file: VaultFile): ImageBitmap?
}

/**
 * Every image pixel the app shows comes through here, decoded by the isolated renderer
 * (golden rule 6). A crash or timeout marks the file in the vault so nothing tries again.
 */
@Singleton
class RenderedImages @Inject constructor(
    private val renderer: SandboxRenderer,
    private val storage: VaultStorage,
    private val repository: VaultRepository,
) : ThumbnailSource {
    private val thumbnails = LruCache<String, ImageBitmap>(THUMBNAIL_CACHE_SIZE)

    suspend fun full(file: VaultFile, maxSide: Int): RenderResult = render(file, maxSide)

    override suspend fun thumbnail(file: VaultFile): ImageBitmap? {
        thumbnails.get(file.id)?.let { return it }
        val result = render(file, THUMBNAIL_SIDE) as? RenderResult.Ready ?: return null
        thumbnails.put(file.id, result.image)
        return result.image
    }

    private suspend fun render(file: VaultFile, maxSide: Int): RenderResult {
        if (file.category != FileCategory.IMAGE || file.openFailed) return RenderResult.NotDecodable
        return try {
            renderer.decodeImage(storage.fileFor(file.id), maxSide, maxSide)
                ?.let { RenderResult.Ready(it.asImageBitmap()) }
                ?: RenderResult.NotDecodable
        } catch (@Suppress("SwallowedException") crashed: RendererCrashedException) {
            repository.markOpenFailed(file.id)
            RenderResult.Unsafe
        }
    }

    private companion object {
        const val THUMBNAIL_SIDE = 192
        const val THUMBNAIL_CACHE_SIZE = 120
    }
}
