package dev.ikram.sandbox.feature.share.copy

import dev.ikram.sandbox.core.data.storage.TempShareCleaner
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.filetype.TypePolicy
import dev.ikram.sandbox.core.model.FileCategory
import dev.ikram.sandbox.core.model.ShareMode
import dev.ikram.sandbox.core.model.VaultFile
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A file in `share_tmp/`, ready to hand over, with the name and type the other app is told. */
data class PreparedShare(val file: File, val displayName: String, val mimeType: String)

sealed interface CopyProgress {
    /** [fraction] of the work done, from 0 to 1, or null while there is no telling. */
    data class Working(val fraction: Float?) : CopyProgress

    data class Done(val share: PreparedShare) : CopyProgress
}

enum class CopyFailure {
    /** The renderer crashed or hung on the file. It is now locked like a blocked file. */
    UNSAFE,

    /** Damaged, password protected, or in a form that cannot be rebuilt. */
    UNREADABLE,

    TOO_LARGE,

    /** The rules forbid this kind of share for this file. */
    NOT_ALLOWED,

    FAILED,
}

class CopyFailedException(val reason: CopyFailure, cause: Throwable? = null) : Exception(reason.name, cause)

/** Makes the file another app receives. Cancelling the flow deletes whatever was half made. */
fun interface ShareCopier {
    fun copy(file: VaultFile, mode: ShareMode): Flow<CopyProgress>
}

/**
 * Checks the rules once more, whatever the screen offered, then hands the file to the
 * copy for its category. Links never get here: there is no file to make.
 */
class VaultShareCopier @Inject constructor(
    private val images: ImageCopy,
    private val pdfs: PdfCopy,
    private val texts: TextCopy,
    private val media: MediaCopy,
    private val originals: OriginalCopy,
) : ShareCopier {
    override fun copy(file: VaultFile, mode: ShareMode): Flow<CopyProgress> {
        val rules = TypePolicy.rulesFor(file.category)
        return when {
            file.openFailed -> refused()

            mode == ShareMode.ORIGINAL -> if (rules.canShareOriginal) originals.copy(file) else refused()

            mode == ShareMode.SAFE_COPY && rules.canSafeCopy -> when (file.category) {
                FileCategory.IMAGE -> images.copy(file)
                FileCategory.PDF -> pdfs.copy(file)
                FileCategory.TEXT -> texts.copy(file)
                FileCategory.VIDEO, FileCategory.AUDIO -> media.copy(file)
                else -> refused()
            }

            else -> refused()
        }
    }

    private fun refused(): Flow<CopyProgress> = flow { throw CopyFailedException(CopyFailure.NOT_ALLOWED) }
}

/** New files in `share_tmp/`, after clearing out copies older than an hour. */
class ShareFiles @Inject constructor(private val storage: VaultStorage, private val cleaner: TempShareCleaner) {
    fun create(extension: String): File {
        cleaner.sweep()
        return storage.newShareFile(extension)
    }

    fun source(file: VaultFile): File = storage.fileFor(file.id)
}

/**
 * Runs [write] on a new share file, and deletes the file if anything goes wrong or the
 * work is cancelled, so no half-made copy is ever left to share.
 */
internal inline fun <T> ShareFiles.writing(extension: String, write: (File) -> T): T {
    val target = create(extension)
    var done = false
    try {
        return write(target).also { done = true }
    } finally {
        if (!done) target.delete()
    }
}
