package dev.ikram.sandbox.feature.share.copy

import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.model.VaultFile
import dev.ikram.sandbox.feature.share.ShareNames
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The downloaded bytes, copied unchanged into `share_tmp/`: FileProvider never serves
 * the vault itself. Named and typed by what the bytes really are, not what the server said.
 */
class OriginalCopy @Inject constructor(
    private val files: ShareFiles,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    fun copy(file: VaultFile): Flow<CopyProgress> = flow {
        val source = files.source(file)
        val total = source.length().coerceAtLeast(1)
        val type = file.detectedType
        val share = files.writing(type.extension) { target ->
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var copied = 0L
                    var reported = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        copied += count
                        if (copied - reported >= REPORT_EVERY) {
                            emit(CopyProgress.Working(copied.toFloat() / total))
                            reported = copied
                        }
                    }
                }
            }
            PreparedShare(target, ShareNames.forShare(file.displayName, type), type.mimeType)
        }
        emit(CopyProgress.Done(share))
    }.flowOn(io)

    private companion object {
        const val BUFFER = 64 * 1024
        const val REPORT_EVERY = 4L * 1024 * 1024
    }
}
