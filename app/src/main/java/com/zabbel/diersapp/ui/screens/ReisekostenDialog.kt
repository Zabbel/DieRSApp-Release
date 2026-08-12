package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zabbel.diersapp.data.model.WochenberichtInfo

@Composable
fun ReisekostenDialog(
    currentInfo: WochenberichtInfo,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double) -> Unit
) {
    var monat by remember { mutableStateOf(currentInfo.telefonMonat) }
    var euro by remember { mutableStateOf(currentInfo.telefonEuro.toString()) }
    var km by remember { mutableStateOf(currentInfo.privatKm.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reisekosten erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Telefonkosten", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = monat, onValueChange = { monat = it }, label = { Text("Für Monat") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = euro, onValueChange = { euro = it }, label = { Text("Betrag in €") }, modifier = Modifier.fillMaxWidth())
                
                val euroVal = remember(euro) { euro.replace(',', '.').toDoubleOrNull() ?: 0.0 }
                if (euroVal > 10.0) {
                    Text(
                        text = "Hinweis: Bei Beträgen über 10,- € ist ein Einzelnachweis erforderlich!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                Text("Kilometergeld", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(value = km, onValueChange = { km = it }, label = { Text("Privat gefahrene km") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val euroVal = euro.replace(',', '.').toDoubleOrNull() ?: 0.0
                val kmVal = km.replace(',', '.').toDoubleOrNull() ?: 0.0
                onSave(monat, euroVal, kmVal)
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
