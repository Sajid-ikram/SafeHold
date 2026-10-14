package dev.ikram.sandbox.feature.viewer.pdf

import androidx.compose.ui.graphics.asImageBitmap
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.viewer.image.RenderResult
import dev.ikram.sandbox.renderer.RendererCrashedException
import dev.ikram.sandbox.renderer.SandboxRenderer
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OpenedPdf {
    data class Ready(val pageCount: Int) : OpenedPdf

    /** Damaged, password protected, or not really a PDF. */
    data object Unreadable : OpenedPdf

    /** The renderer crashed or hung on this file. It is marked and never tried again. */
    data object Unsafe : OpenedPdf
}

/** PDF pages, rendered only by the isolated renderer (golden rule 6). */
@Singleton
class RenderedPdf @Inject constructor(
    private val renderer: SandboxRenderer,
    private val storage: VaultStorage,
    private val repository: VaultRepository,
) {
    suspend fun open(file: VaultFile): OpenedPdf {
        if (file.category != FileCategory.PDF || file.openFailed) return OpenedPdf.Unsafe
        return guarded(onCrash = { OpenedPdf.Unsafe }, file) {
            renderer.pdfPageCount(storage.fileFor(file.id))?.let(OpenedPdf::Ready) ?: OpenedPdf.Unreadable
        }
    }

    suspend fun page(file: VaultFile, index: Int, width: Int): RenderResult =
        guarded(onCrash = { RenderResult.Unsafe }, file) {
            renderer.renderPdfPage(storage.fileFor(file.id), index, width)
                ?.let { RenderResult.Ready(it.asImageBitmap()) }
                ?: RenderResult.NotDecodable
        }

    private suspend fun <T> guarded(onCrash: () -> T, file: VaultFile, block: suspend () -> T): T = try {
        block()
    } catch (@Suppress("SwallowedException") crashed: RendererCrashedException) {
        repository.markOpenFailed(file.id)
        onCrash()
    }
}
