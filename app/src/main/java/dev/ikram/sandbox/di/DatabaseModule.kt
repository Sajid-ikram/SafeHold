package dev.ikram.sandbox.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.ikram.sandbox.core.data.db.SandboxDatabase
import dev.ikram.sandbox.core.data.db.VaultFileDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SandboxDatabase =
        Room.databaseBuilder(context, SandboxDatabase::class.java, SandboxDatabase.NAME).build()

    @Provides
    fun vaultFileDao(database: SandboxDatabase): VaultFileDao = database.vaultFileDao()

    /** Settings, in the app's private files like everything else (and excluded from backup). */
    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
