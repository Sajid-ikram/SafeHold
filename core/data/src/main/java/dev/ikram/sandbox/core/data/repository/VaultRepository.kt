package dev.ikram.sandbox.core.data.repository

import dev.ikram.sandbox.core.data.db.VaultFileDao
import dev.ikram.sandbox.core.data.db.toModel
import dev.ikram.sandbox.core.data.di.IoDispatcher
import dev.ikram.sandbox.core.data.storage.VaultStorage
import dev.ikram.sandbox.core.model.VaultFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class VaultRepository @Inject constructor(
    private val dao: VaultFileDao,
    private val storage: VaultStorage,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Newest first. */
    val files: Flow<List<VaultFile>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    fun file(id: String): Flow<VaultFile?> = dao.observe(id).map { it?.toModel() }

    /** Removes the bytes first: a row without a file is harmless, a file without a row is a leak. */
    suspend fun delete(id: String) = withContext(io) {
        storage.delete(id)
        dao.delete(id)
    }

    suspend fun acknowledgeMismatch(id: String) = withContext(io) { dao.acknowledgeMismatch(id) }

    suspend fun markOpenFailed(id: String) = withContext(io) { dao.markOpenFailed(id) }

    /**
     * Brings disk and database back in step after the process died mid-download or
     * mid-delete: drops half-written downloads, files nothing refers to, and rows whose
     * file is gone. Only files from earlier processes are touched, so a download this
     * process has already started is safe.
     */
    suspend fun reconcile() = withContext(io) {
        storage.clearPartials()
        val recorded = dao.ids().toSet()
        (storage.earlierSessionIds() - recorded).forEach(storage::delete)
        (recorded - storage.storedIds()).forEach { dao.delete(it) }
    }
}
