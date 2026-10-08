package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.ui.components.SignaturePad
import com.zabbel.diersapp.ui.components.bitmapToBase64
import com.zabbel.diersapp.ui.components.createBitmapFromPaths
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KundenSignatureScreen(
    auftrag: Betriebsauftrag,
    jahr: Int,
    kw: Int,
    viewModel: AuftragViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    var signaturePaths by remember { mutableStateOf(listOf<Path>()) }
    var unterzeichnerName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unterschrift Kunde") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Montagebericht abzeichnen",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Auftrag: ${auftrag.auftragsNummer}", style = MaterialTheme.typography.titleMedium)
                    Text("Pos: ${auftrag.positionsNummer}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Kunde: ${auftrag.kunde}", style = MaterialTheme.typography.bodySmall)
                    Text("Projekt: ${auftrag.projektNr ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Kalenderwoche: $kw / $jahr", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = unterzeichnerName,
                onValueChange = { unterzeichnerName = it },
                label = { Text("Name in Druckbuchstaben") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = unterzeichnerName.isBlank(),
                supportingText = { if (unterzeichnerName.isBlank()) Text("Name ist ein Pflichtfeld!") }
            )

            Text("Bitte unterschreiben Sie hier:", style = MaterialTheme.typography.bodyMedium)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp) // Etwas kleiner für den Namen
            ) {
                SignaturePad(
                    initialSignatureBase64 = null,
                    isLocked = false,
                    currentPaths = signaturePaths,
                    onPathsChanged = { signaturePaths = it },
                    onClear = { signaturePaths = emptyList() }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (signaturePaths.isNotEmpty() && unterzeichnerName.isNotBlank()) {
                        scope.launch {
                            val finalSignature = bitmapToBase64(createBitmapFromPaths(signaturePaths, 0, 0))
                            viewModel.saveKundenUnterschrift(auftrag.id, jahr, kw, finalSignature, unterzeichnerName.trim())
                            onSaved()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = signaturePaths.isNotEmpty() && unterzeichnerName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Unterschrift speichern")
            }
        }
    }
}
