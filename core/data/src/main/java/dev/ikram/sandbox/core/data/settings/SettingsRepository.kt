package dev.ikram.sandbox.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.ikram.sandbox.core.model.AutoDelete
import dev.ikram.sandbox.core.model.Settings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Settings, kept with DataStore in the app's private files (never backed up). A file
 * that cannot be read gives the defaults rather than an error.
 */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun setAskBeforeDownload(ask: Boolean) = store.edit { it[ASK_BEFORE_DOWNLOAD] = ask }

    suspend fun setAutoDelete(autoDelete: AutoDelete) = store.edit { it[AUTO_DELETE] = autoDelete.name }

    /** Only switches on with a key in place. */
    suspend fun setAutoScan(scan: Boolean) = store.edit { it[AUTO_SCAN] = scan && it[VIRUS_TOTAL_KEY] != null }

    /** Null removes the key, which also stops automatic checks. Anything but a key is refused. */
    suspend fun setVirusTotalKey(key: String?) = store.edit { prefs ->
        if (key == null) {
            prefs.remove(VIRUS_TOTAL_KEY)
            prefs[AUTO_SCAN] = false
        } else {
            val cleaned = normalizeKey(key)
            require(cleaned != null) { "Not a VirusTotal key" }
            prefs[VIRUS_TOTAL_KEY] = cleaned
        }
    }

    suspend fun setVaultLock(locked: Boolean) = store.edit { it[VAULT_LOCK] = locked }

    suspend fun setOnboardingDone() = store.edit { it[ONBOARDING_DONE] = true }

    private fun Preferences.toSettings() = Settings(
        askBeforeDownload = this[ASK_BEFORE_DOWNLOAD] ?: true,
        autoDelete = AutoDelete.entries.firstOrNull { it.name == this[AUTO_DELETE] } ?: AutoDelete.NEVER,
        autoScan = (this[AUTO_SCAN] ?: false) && this[VIRUS_TOTAL_KEY] != null,
        virusTotalKey = this[VIRUS_TOTAL_KEY],
        vaultLock = this[VAULT_LOCK] ?: false,
        onboardingDone = this[ONBOARDING_DONE] ?: false,
    )

    companion object {
        private val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        private val AUTO_DELETE = stringPreferencesKey("auto_delete")
        private val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        private val VIRUS_TOTAL_KEY = stringPreferencesKey("virus_total_key")
        private val VAULT_LOCK = booleanPreferencesKey("vault_lock")
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        private val KEY_PATTERN = Regex("^[0-9a-f]{64}$")

        /** VirusTotal keys are 64 hex characters; pasted ones often bring spaces along. Null if it isn't one. */
        fun normalizeKey(raw: String): String? = raw.trim().lowercase().takeIf { KEY_PATTERN.matches(it) }
    }
}
