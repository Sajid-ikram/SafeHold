package dev.ikram.sandbox.feature.share.copy

import android.graphics.Bitmap
import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.data.repository.VaultRepository
import dev.ikram.sandbox.core.model.DetectedType
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.ShareNames
import dev.ikram.sandbox.renderer.SandboxRenderer
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * A PDF redrawn as pictures of its pages. The isolated renderer draws each page, one call
 * (and one timeout) per page; this process turns the pixels into a new file with
 * [ImagePdfWriter]. Links, scripts, forms, attachments and metadata are left behind; so
 * is selectable text, which is the price of the copy being only what the pages showed.
 */
class PdfCopy @Inject constructor(
    private val renderer: SandboxRenderer,
    private val files: ShareFiles,
    private val repository: VaultRepository,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun copy(file: VaultFile): Flow<CopyProgress> = flow {
        emit(CopyProgress.Working(null))
        val source = files.source(file)
        val pages = guardRenderer(repository, file) { renderer.pdfPageCount(source) }
            ?: throw CopyFailedException(CopyFailure.UNREADABLE)
        if (pages == 0) throw CopyFailedException(CopyFailure.UNREADABLE)
        if (pages > MAX_PAGES) throw CopyFailedException(CopyFailure.TOO_LARGE)

        val share = files.writing(DetectedType.PDF.extension) { target ->
            target.outputStream().buffered().use { stream ->
                ImagePdfWriter(stream, pages).use { writer ->
                    for (index in 0 until pages) {
                        val page = guardRenderer(repository, file) {
                            renderer.renderPdfPageSized(source, index, PAGE_WIDTH_PX)
                        } ?: throw CopyFailedException(CopyFailure.UNREADABLE)
                        val bitmap = page.bitmap
                        val width = bitmap.width
                        val height = bitmap.height
                        val jpeg = try {
                            bitmap.toJpeg()
                        } finally {
                            bitmap.recycle()
                        }
                        writer.addPage(jpeg, width, height, page.widthPoints, page.heightPoints)
                        emit(CopyProgress.Working((index + 1f) / pages))
                    }
                }
            }
            PreparedShare(target, ShareNames.forShare(file.displayName, DetectedType.PDF), DetectedType.PDF.mimeType)
        }
        emit(CopyProgress.Done(share))
    }.flowOn(io)

    private fun Bitmap.toJpeg(): ByteArray = ByteArrayOutputStream().also { out ->
        check(compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "Could not encode the page" }
    }.toByteArray()

    private companion object {
        /** About 150 dots per inch on A4 and Letter: sharp enough to read, small enough to send. */
        const val PAGE_WIDTH_PX = 1240
        const val JPEG_QUALITY = 88

        /** Each page takes a renderer call; beyond this a copy would take too long to make. */
        const val MAX_PAGES = 300
    }
}
