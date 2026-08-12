package com.zabbel.diersapp.pinscreen

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabbel.diersapp.data.repository.AuftragRepository
import com.zabbel.diersapp.datastore.AppSettingsDataStore
import com.zabbel.diersapp.util.SecurityUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val appSettingsDataStore: AppSettingsDataStore,
    private val auftragRepository: AuftragRepository
) : ViewModel() {

    // State für Fehlermeldungen (wird von der UI beobachtet)
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Status für erfolgreiche Speicherung (um im Screen zu navigieren)
    var isSuccess by mutableStateOf(false)
        private set

    fun validateAndSave(pin: String, confirmPin: String) {
        // 1. Übereinstimmung prüfen
        if (pin != confirmPin) {
            errorMessage = "Die PINs stimmen nicht überein."
            return
        }

        // 2. Sicherheit prüfen
        if (!PinValidator.isPinSecure(pin)) {
            errorMessage = "Diese PIN ist zu einfach! Bitte wähle eine andere Kombination."
            return
        }

        // Wenn alles okay ist: Speichern
        errorMessage = null
        savePin(pin)
    }

    private fun savePin(pin: String) {
        viewModelScope.launch {
            try {
                val salt = SecurityUtils.generateSalt()
                val hash = SecurityUtils.hashPin(pin, salt)

                // Wenn die Datenbank schon initialisiert ist (z.B. nach Bio-Login), rekeyen wir
                auftragRepository.changePassphrase(hash)
                // Sicherheitshalber trotzdem init aufrufen, falls sie noch null war
                auftragRepository.initDatabase(hash)

                val saltString = Base64.encodeToString(salt, Base64.NO_WRAP)
                val hashString = Base64.encodeToString(hash, Base64.NO_WRAP)
                val combinedHash = "$saltString:$hashString"

                appSettingsDataStore.savePinHash(combinedHash)
                appSettingsDataStore.setIsFirstLaunch(false)
                
                // WICHTIG: Wenn Biometrie aktiv ist, müssen wir auch das Biometrie-Geheimnis erneuern.
                // Da wir aber hier nicht direkt auf den CryptoManager mit Prompt zugreifen können,
                // markieren wir in den Settings, dass Biometrie beim nächsten Login neu eingerichtet werden muss
                // ODER wir bitten den User im nächsten Login-Screen darum.
                // Einfachste Lösung hier: Biometrie vorerst deaktivieren, User muss sie neu einschalten.
                appSettingsDataStore.setUseBiometrics(false)

                isSuccess = true
            } catch (e: Exception) {
                errorMessage = "Fehler beim Speichern der PIN."
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}