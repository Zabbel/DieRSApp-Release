package com.zabbel.diersapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabbel.diersapp.datastore.AppSettingsDataStore // Importiere deine DataStore-Klasse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

// Enum für den nächsten Navigationsschritt nach dem Splash
enum class NextScreen {
    LOADING,
    PIN_SETUP,
    PIN_LOGIN,
    AUFTRAGSUEBERSICHT // Oder MAIN_CONTENT
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    appSettingsDataStore: AppSettingsDataStore // Als Konstruktor-Parameter
) : ViewModel() {

    private val _isBasicInitializationDone = MutableStateFlow(false)
    // val isBasicInitializationDone = _isBasicInitializationDone.asStateFlow() // Wird evtl. nicht mehr direkt gebraucht

    // Kombinierter State, der den nächsten Screen bestimmt
    val nextScreenState: StateFlow<NextScreen> = combine(
        _isBasicInitializationDone,
        appSettingsDataStore.isFirstLaunch,
        appSettingsDataStore.pinHash
    ) { isInitialized, isFirstLaunch, pinHash ->
        if (!isInitialized) {
            NextScreen.LOADING
        } else {
            if (isFirstLaunch || pinHash == null) { // Wenn erster Start ODER keine PIN gesetzt ist
                NextScreen.PIN_SETUP
            } else {
                NextScreen.PIN_LOGIN
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NextScreen.LOADING
    )


    // Der alte isAppReady wird ersetzt durch die Logik in nextScreenState
    // private val _isAppReady = MutableStateFlow(false)
    // val isAppReady = _isAppReady.asStateFlow()

    init {
        initializeApp()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            // Deine normale App-Initialisierungslogik (Netzwerk, DB etc.)
            // Hier nur ein Delay als Beispiel
            delay(4000.milliseconds)

            // Nachdem die *grundlegende* App-Initialisierung abgeschlossen ist:
            _isBasicInitializationDone.value = true

            // Die Entscheidung, wohin navigiert wird (PIN_SETUP, PIN_LOGIN),
            // wird jetzt durch `nextScreenState` basierend auf DataStore-Werten getroffen.
            // Der Splash Screen wird durch die MainActivity gesteuert, bis
            // nextScreenState != NextScreen.LOADING ist.
        }
    }
}