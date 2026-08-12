package com.zabbel.diersapp.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Erstellt eine Erweiterungseigenschaft auf Context, um den DataStore leicht zugänglich zu machen.
// Der Name "app_settings" ist der Dateiname, unter dem die Preferences gespeichert werden.
val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsDataStore(private val context: Context) {

    // Definiere die Schlüssel für deine Preferences
    companion object {
        val IS_FIRST_LAUNCH_KEY = booleanPreferencesKey("is_first_launch")
        val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        val USE_BIOMETRICS_KEY = booleanPreferencesKey("use_biometrics")
        val ENCRYPTED_PIN_KEY = stringPreferencesKey("encrypted_pin") // Für Biometrie-Login
    }

    // Flow, um den "Erster Start"-Status zu lesen
    val isFirstLaunch: Flow<Boolean> = context.appSettingsDataStore.data
        .map { preferences ->
            preferences[IS_FIRST_LAUNCH_KEY] != false
        }

    // Flow für Biometrie-Einstellung
    val useBiometrics: Flow<Boolean> = context.appSettingsDataStore.data
        .map { preferences ->
            preferences[USE_BIOMETRICS_KEY] ?: false
        }

    suspend fun setUseBiometrics(use: Boolean) {
        context.appSettingsDataStore.edit { settings ->
            settings[USE_BIOMETRICS_KEY] = use
        }
    }

    // Flow für verschlüsselten PIN (Keystore-Variante)
    val encryptedPin: Flow<String?> = context.appSettingsDataStore.data
        .map { preferences ->
            preferences[ENCRYPTED_PIN_KEY]
        }

    suspend fun saveEncryptedPin(encryptedBase64: String) {
        context.appSettingsDataStore.edit { settings ->
            settings[ENCRYPTED_PIN_KEY] = encryptedBase64
        }
    }

    // Funktion zum Aktualisieren des "Erster Start"-Status
    suspend fun setIsFirstLaunch(isFirst: Boolean) {
        context.appSettingsDataStore.edit { settings ->
            settings[IS_FIRST_LAUNCH_KEY] = isFirst
        }
    }

    // Flow, um den PIN-Hash zu lesen
    val pinHash: Flow<String?> = context.appSettingsDataStore.data
        .map { preferences ->
            preferences[PIN_HASH_KEY] // Kann null sein, wenn keine PIN gesetzt ist
        }

    // Funktion zum Speichern des PIN-Hashes
    suspend fun savePinHash(hash: String) {
        context.appSettingsDataStore.edit { settings ->
            settings[PIN_HASH_KEY] = hash
        }
    }
}