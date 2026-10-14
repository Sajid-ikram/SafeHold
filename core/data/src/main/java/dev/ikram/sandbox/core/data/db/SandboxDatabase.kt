package dev.ikram.sandbox.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** Vault metadata. Lives in the app's private database directory, excluded from every backup. */
@Database(entities = [VaultFileEntity::class], version = 1, exportSchema = true)
abstract class SandboxDatabase : RoomDatabase() {
    abstract fun vaultFileDao(): VaultFileDao

    companion object {
        const val NAME = "sandbox.db"
    }
}
