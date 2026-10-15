package dev.ikram.sandbox.fakes

import dev.ikram.sandbox.core.data.db.VaultFileDao
import dev.ikram.sandbox.core.data.db.VaultFileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory stand-in for the Room DAO, so data tests run on the plain JVM. */
class FakeVaultFileDao : VaultFileDao {
    val rows = MutableStateFlow<List<VaultFileEntity>>(emptyList())

    override fun observeAll(): Flow<List<VaultFileEntity>> = rows.map { list ->
        list.sortedByDescending { it.createdAtMillis }
    }

    override fun observe(id: String): Flow<VaultFileEntity?> = rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: String): VaultFileEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun ids(): List<String> = rows.value.map { it.id }

    override suspend fun insert(file: VaultFileEntity) {
        check(rows.value.none { it.id == file.id }) { "duplicate id" }
        rows.update { it + file }
    }

    override suspend fun delete(id: String) = rows.update { list -> list.filterNot { it.id == id } }

    override suspend fun acknowledgeMismatch(id: String) = change(id) { it.copy(mismatchAcknowledged = true) }

    override suspend fun markOpenFailed(id: String) = change(id) { it.copy(openFailed = true) }

    override suspend fun updateScan(
        id: String,
        status: String,
        flagged: Int?,
        engines: Int?,
        checkedAt: Long?,
        error: String?,
    ) = change(id) {
        it.copy(
            scanStatus = status,
            scanFlaggedCount = flagged,
            scanEngineCount = engines,
            scanCheckedAtMillis = checkedAt,
            scanError = error,
        )
    }

    override suspend fun resetUnfinishedScans() = rows.update { list ->
        list.map { if (it.scanStatus == "CHECKING") it.copy(scanStatus = "NOT_CHECKED") else it }
    }

    override suspend fun idsCreatedBefore(cutoffMillis: Long): List<String> =
        rows.value.filter { it.createdAtMillis < cutoffMillis }.map { it.id }

    private fun change(id: String, transform: (VaultFileEntity) -> VaultFileEntity) =
        rows.update { list -> list.map { if (it.id == id) transform(it) else it } }
}
