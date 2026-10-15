package dev.ikram.sandbox.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// One method per query is how Room works; splitting the DAO would only scatter them.
@Suppress("TooManyFunctions")
@Dao
interface VaultFileDao {
    @Query("SELECT * FROM vault_files ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<VaultFileEntity>>

    @Query("SELECT * FROM vault_files WHERE id = :id")
    fun observe(id: String): Flow<VaultFileEntity?>

    @Query("SELECT * FROM vault_files WHERE id = :id")
    suspend fun get(id: String): VaultFileEntity?

    @Query("SELECT id FROM vault_files")
    suspend fun ids(): List<String>

    @Insert
    suspend fun insert(file: VaultFileEntity)

    @Query("DELETE FROM vault_files WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE vault_files SET mismatchAcknowledged = 1 WHERE id = :id")
    suspend fun acknowledgeMismatch(id: String)

    @Query("UPDATE vault_files SET openFailed = 1 WHERE id = :id")
    suspend fun markOpenFailed(id: String)

    @Query(
        "UPDATE vault_files SET scanStatus = :status, scanFlaggedCount = :flagged, scanEngineCount = :engines, " +
            "scanCheckedAtMillis = :checkedAt, scanError = :error WHERE id = :id",
    )
    suspend fun updateScan(id: String, status: String, flagged: Int?, engines: Int?, checkedAt: Long?, error: String?)

    /** A lookup the process did not live to finish starts again from scratch. */
    @Query("UPDATE vault_files SET scanStatus = 'NOT_CHECKED' WHERE scanStatus = 'CHECKING'")
    suspend fun resetUnfinishedScans()

    @Query("SELECT id FROM vault_files WHERE createdAtMillis < :cutoffMillis")
    suspend fun idsCreatedBefore(cutoffMillis: Long): List<String>
}
