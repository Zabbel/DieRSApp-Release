package com.zabbel.diersapp.pinscreen

import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabbel.diersapp.data.repository.AuftragRepository
import com.zabbel.diersapp.datastore.AppSettingsDataStore
import com.zabbel.diersapp.util.CryptoManager
import com.zabbel.diersapp.util.SecurityUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.crypto.Cipher
import android.util.Log

@HiltViewModel
class PinLoginViewModel @Inject constructor(
    private val appSettingsDataStore: AppSettingsDataStore,
    private val auftragRepository: AuftragRepository
) : ViewModel() {

    private val _attempts = MutableStateFlow(0)
    val attempts: StateFlow<Int> = _attempts

    private val _canNavigateForward = MutableStateFlow(false)
    val canNavigateForward: StateFlow<Boolean> = _canNavigateForward.asStateFlow()

    // Steuert, ob die PIN-Eingabefelder sichtbar sind
    private val _isPinVisible = MutableStateFlow(false)
    val isPinVisible: StateFlow<Boolean> = _isPinVisible.asStateFlow()

    val showBiometricOption: StateFlow<Boolean> = appSettingsDataStore.useBiometrics
        .combine(appSettingsDataStore.encryptedPin) { enabled, encrypted ->
            enabled && !encrypted.isNullOrBlank()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _biometricLoginSuccess = MutableStateFlow(false)
    val biometricLoginSuccess: StateFlow<Boolean> = _biometricLoginSuccess

    init {
        // Initialprüfung: Wenn Biometrie deaktiviert ist, zeige sofort die PIN-Felder
        viewModelScope.launch {
            val bioEnabled = appSettingsDataStore.useBiometrics.first()
            if (!bioEnabled) {
                _isPinVisible.value = true
            }
        }
    }

    fun showPinInput() {
        _isPinVisible.value = true
    }

    fun resetNavigation() {
        _canNavigateForward.value = false
    }

    suspend fun checkPin(enteredPin: String): Boolean {
        val storedCombinedHash = appSettingsDataStore.pinHash.first()
        if (storedCombinedHash.isNullOrBlank()) return false

        val parts = storedCombinedHash.split(":")
        if (parts.size != 2) return false

        val saltString = parts[0]
        val storedHashString = parts[1]

        return try {
            val salt = Base64.decode(saltString, Base64.NO_WRAP)
            val enteredPinHash = SecurityUtils.hashPin(enteredPin, salt)
            val enteredPinHashString = Base64.encodeToString(enteredPinHash, Base64.NO_WRAP)

            val isCorrect = enteredPinHashString == storedHashString

            if (isCorrect) {
                _attempts.value = 0
                // Hier auch die Datenbank initialisieren
                val pinHash = SecurityUtils.hashPin(enteredPin, salt)
                auftragRepository.initDatabase(pinHash)
                _canNavigateForward.value = true
            } else {
                _attempts.value += 1
            }
            isCorrect
        } catch (e: Exception) {
            _attempts.value += 1
            false
        }
    }

    suspend fun getDecryptCipher(): Cipher? {
        return try {
            val encryptedDataCombined = appSettingsDataStore.encryptedPin.first() ?: return null
            val parts = encryptedDataCombined.split("|")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            CryptoManager.getDecryptCipher(iv)
        } catch (e: Exception) {
            null
        }
    }

    fun onBiometricSuccess(result: BiometricPrompt.AuthenticationResult) {
        viewModelScope.launch {
            try {
                val cipher = result.cryptoObject?.cipher ?: return@launch
                val encryptedDataCombined = appSettingsDataStore.encryptedPin.first() ?: return@launch
                val parts = encryptedDataCombined.split("|")
                val encryptedBytes = Base64.decode(parts[0], Base64.NO_WRAP)

                val decryptedHash = CryptoManager.decrypt(encryptedBytes, cipher)
                auftragRepository.initDatabase(decryptedHash)

                _biometricLoginSuccess.value = true
            } catch (e: Exception) {
                Log.e("RS_Bio", "Bio-Login Fehler", e)
            }
        }
    }

    fun resetBiometricSuccess() {
        _biometricLoginSuccess.value = false
    }
}
