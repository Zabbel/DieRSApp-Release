package com.zabbel.diersapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zabbel.diersapp.ui.components.SignaturePad
import com.zabbel.diersapp.ui.components.bitmapToBase64
import com.zabbel.diersapp.ui.components.createBitmapFromPaths
import com.zabbel.diersapp.util.BiometricHelper
import com.zabbel.diersapp.viewmodel.AuftragViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AuftragViewModel,
    onBack: () -> Unit,
    onNavigateToPinChange: () -> Unit
) {
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val useBiometrics by viewModel.useBiometrics.collectAsStateWithLifecycle()
    val context = LocalActivity.current as FragmentActivity

    var vorname by remember { mutableStateOf("") }
    var nachname by remember { mutableStateOf("") }
    var persNr by remember { mutableStateOf("") }
    var wochenstunden by remember { mutableStateOf("37.0") }
    var signatureBase64 by remember { mutableStateOf<String?>(null) }
    
    var signaturePaths by remember { mutableStateOf(listOf<Path>()) }
    var isSignatureLocked by remember { mutableStateOf(true) }
    var showBioWarning by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        settings?.let {
            vorname = it.vorname
            nachname = it.nachname
            persNr = it.personalnummer
            wochenstunden = it.wochenstunden.toString()
            signatureBase64 = it.signatureBase64
            isSignatureLocked = !it.signatureBase64.isNullOrBlank()
        }
    }

    val hasUnsavedChanges = remember(settings, vorname, nachname, persNr, wochenstunden, signaturePaths) {
        settings?.let { s ->
            vorname != s.vorname || nachname != s.nachname || persNr != s.personalnummer || (wochenstunden.toDoubleOrNull() ?: 37.0) != s.wochenstunden || signaturePaths.isNotEmpty()
        } ?: true
    }

    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = hasUnsavedChanges) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Ungespeicherte Änderungen") },
            text = { Text("Möchtest du die Änderungen verwerfen?") },
            confirmButton = { TextButton(onClick = onBack) { Text("Verwerfen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { Button(onClick = { showExitDialog = false }) { Text("Weiter bearbeiten") } }
        )
    }

    if (showBioWarning) {
        AlertDialog(
            onDismissRequest = { showBioWarning = false },
            title = { Text("Sicherheitshinweis") },
            text = { Text("Wenn du Biometrie deaktivierst und deinen PIN vergisst, können deine Daten nicht wiederhergestellt werden. Die Datenbank muss dann gelöscht werden, um die App wieder zu nutzen.") },
            confirmButton = { 
                Button(onClick = { 
                    viewModel.setUseBiometrics(false)
                    showBioWarning = false 
                }) { Text("Verstanden & Deaktivieren") } 
            },
            dismissButton = { TextButton(onClick = { showBioWarning = false }) { Text("Abbrechen") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = { if (hasUnsavedChanges) showExitDialog = true else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = vorname, onValueChange = { vorname = it }, label = { Text("Vorname") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = nachname, onValueChange = { nachname = it }, label = { Text("Nachname") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = persNr, onValueChange = { persNr = it }, label = { Text("Personalnummer") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = wochenstunden, onValueChange = { wochenstunden = it }, label = { Text("Soll-Stunden pro Woche") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Sicherheit", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Biometrisches Login") },
                supportingContent = { Text("Fingerabdruck oder Gesichtserkennung nutzen.") },
                trailingContent = {
                    var showNoBioDialog by remember { mutableStateOf<String?>(null) }

                    if (showNoBioDialog != null) {
                        AlertDialog(
                            onDismissRequest = { showNoBioDialog = null },
                            title = { Text("Biometrie nicht bereit") },
                            text = { Text(showNoBioDialog ?: "") },
                            confirmButton = { Button(onClick = { showNoBioDialog = null }) { Text("OK") } }
                        )
                    }

                    Switch(
                        checked = useBiometrics,
                        onCheckedChange = { isEnabled ->
                            if (isEnabled) {
                                val status = BiometricHelper.checkBiometricStatus(context)
                                when (status) {
                                    BiometricHelper.BiometricStatus.READY -> {
                                        val cipher = viewModel.getEncryptCipher()
                                        if (cipher != null) {
                                            BiometricHelper.showBiometricPrompt(
                                                activity = context,
                                                title = "Biometrie einrichten",
                                                subtitle = "Bestätige deine Identität",
                                                cryptoObject = BiometricPrompt.CryptoObject(cipher),
                                                onSuccess = { result -> viewModel.setupBiometricSecret(result) },
                                                onError = { _, _ -> }
                                            )
                                        } else {
                                            showNoBioDialog = "Sicherheitsschlüssel konnte nicht erstellt werden. Bitte stelle sicher, dass du einen Fingerabdruck in den Android-Systemeinstellungen hinterlegt hast."
                                        }
                                    }
                                    BiometricHelper.BiometricStatus.NOT_ENROLLED -> {
                                        showNoBioDialog = "Es ist kein Fingerabdruck hinterlegt. Bitte richte Biometrie zuerst in den Android-Systemeinstellungen ein."
                                    }
                                    BiometricHelper.BiometricStatus.NOT_AVAILABLE -> {
                                        showNoBioDialog = "Dein Gerät unterstützt keine biometrische Anmeldung oder die Hardware ist nicht verfügbar."
                                    }
                                    BiometricHelper.BiometricStatus.UNKNOWN -> {
                                        showNoBioDialog = "Biometrie kann aktuell nicht genutzt werden."
                                    }
                                }
                            } else {
                                showBioWarning = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Sicherheitscode") },
                supportingContent = { Text("PIN für den App-Zugriff ändern.") },
                trailingContent = {
                    TextButton(onClick = onNavigateToPinChange) {
                        Text("Ändern")
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Unterschrift", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            SignaturePad(initialSignatureBase64 = signatureBase64, isLocked = isSignatureLocked, currentPaths = signaturePaths, onPathsChanged = { signaturePaths = it }, onClear = { signaturePaths = emptyList() })
            if (isSignatureLocked) {
                Button(
                    onClick = { isSignatureLocked = false; signaturePaths = emptyList() }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("Unterschrift ändern") }
            }
            Button(
                onClick = {
                    val stundenDouble = wochenstunden.toDoubleOrNull() ?: 37.0
                    val finalSignature = if (signaturePaths.isNotEmpty()) bitmapToBase64(createBitmapFromPaths(signaturePaths, 0, 0)) else signatureBase64
                    viewModel.saveSettings(vorname, nachname, persNr, finalSignature, stundenDouble)
                    onBack()
                }, 
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) { Text("Speichern") }
        }
    }
}
