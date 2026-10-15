package dev.ikram.sandbox.core.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Vault metadata. Lives in the app's private database directory, excluded from every backup.
 * Version 2 added the VirusTotal engine count, check time and error columns.
 */
@Database(
    entities = [VaultFileEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SandboxDatabase : RoomDatabase() {
    abstract fun vaultFileDao(): VaultFileDao

    companion object {
        const val NAME = "sandbox.db"
    }
}
