package dev.ikram.sandbox.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
}
