@file:OptIn(ExperimentalLayoutApi::class)

package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zabbel.diersapp.data.model.Betriebsauftrag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class, 
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun AuftragEditScreen(
    initialAuftrag: Betriebsauftrag,
    onSave: (Betriebsauftrag) -> Unit,
    onCancel: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var lastTouchY by remember { mutableFloatStateOf(0f) }
    var isDescriptionFocused by remember { mutableStateOf(false) }

    // Lokale States für die Bearbeitung (Erweitert um die neuen Felder)
    var auftragsNummer by remember { mutableStateOf(initialAuftrag.auftragsNummer) }
    var kunde by remember { mutableStateOf(initialAuftrag.kunde) }
    var liegeplatz by remember { mutableStateOf(initialAuftrag.liegeplatz ?: "") }
    var projektNr by remember { mutableStateOf(initialAuftrag.projektNr ?: "") }
    var ihNummer by remember { mutableStateOf(initialAuftrag.ihNummer ?: "") }
    var meldungsNummer by remember { mutableStateOf(initialAuftrag.meldungsNummer ?: "") }
    var kurzText by remember { mutableStateOf(initialAuftrag.titelKurz) }
    var langText by remember { mutableStateOf(initialAuftrag.beschreibungLang) }
    var positionsNummer by remember { mutableStateOf(initialAuftrag.positionsNummer) }
    var kundenBestellText by remember { mutableStateOf(initialAuftrag.kundenBestellText ?: "") }

    // NEU: States für die weiteren Felder aus deiner Entity
    var zeitraum by remember { mutableStateOf(initialAuftrag.durchfuehrungsZeitraum ?: "") }
    var abrechnung by remember { mutableStateOf(initialAuftrag.abrechnungsArt) }
    val rawNames = initialAuftrag.ansprechpartnerName?.split("\n")?.filter { it.isNotBlank() } ?: listOf("")
    val rawTels = initialAuftrag.ansprechpartnerTelefon?.split("\n") ?: emptyList()
    val rawEmails = initialAuftrag.ansprechpartnerEmail?.split("\n") ?: emptyList()

    var pocNames by remember { mutableStateOf(rawNames.ifEmpty { listOf("") }) }
    var pocTels by remember {
        mutableStateOf(List(pocNames.size) { index -> rawTels.getOrElse(index) { "" } })
    }
    var pocEmails by remember {
        mutableStateOf(List(pocNames.size) { index -> rawEmails.getOrElse(index) { "" } })
    }
    var guesi by remember { mutableStateOf(initialAuftrag.guesi ?: "") }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daten kontrollieren") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding() // Puffer für Tastatur
                .imeNestedScroll() // Synchronisiert Scrollen mit Tastatur-Animation
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Sektion
            Text("Stammdaten", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            val schiffAnzeige = if (kunde.contains("🚢")) kunde.substringAfter("🚢 ").substringBefore(" |") else ""
            val kundeAnzeige = if (kunde.contains("|")) kunde.substringAfter("| ") else kunde

            var schiffState by remember { mutableStateOf(schiffAnzeige) }
            var kundeState by remember { mutableStateOf(kundeAnzeige) }

            EditInfoCard(label = "Schiff / Objekt", value = schiffState, onValueChange = { schiffState = it }, icon = Icons.Default.DirectionsBoat)
            EditInfoCard(label = "Projekt-Nr.", value = projektNr, onValueChange = { projektNr = it }, icon = Icons.Default.Tag)
            EditInfoCard(label = "Auftragsnummer", value = auftragsNummer, onValueChange = { auftragsNummer = it }, icon = Icons.Default.Numbers)
            EditInfoCard(label = "Kunde", value = kundeState, onValueChange = { kundeState = it }, icon = Icons.Default.Business)
            EditInfoCard(label = "K-Bestelltext", value = kundenBestellText, onValueChange = { kundenBestellText = it }, icon = Icons.Default.ShoppingCart)

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    EditInfoCard(label = "Pos.", value = positionsNummer, onValueChange = { positionsNummer = it }, icon = Icons.Default.Info)
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    EditInfoCard(label = "IH-Nummer", value = ihNummer, onValueChange = { ihNummer = it }, icon = Icons.Default.Fingerprint)
                }
            }

            // --- Durchführung & Kontakt Sektion ---
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Durchführung & Kontakt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            EditInfoCard(
                label = "Liegeplatz",
                value = liegeplatz,
                onValueChange = { newValue ->
                    liegeplatz = when {
                        newValue.contains("Marinestützpunkt Wilhelmshaven", ignoreCase = true) -> "4.Einfahrt"
                        newValue.contains("Marinearsenal Wilhelmshaven", ignoreCase = true) -> "MArs"
                        else -> newValue
                    }
                },
                icon = Icons.Default.Place
            )
            EditInfoCard(label = "Zeitraum / Termin", value = zeitraum, onValueChange = { zeitraum = it }, icon = Icons.Default.DateRange)

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    EditInfoCard(label = "Abrechnung", value = abrechnung, onValueChange = { abrechnung = it }, icon = Icons.Default.Payments)
                }
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    EditInfoCard(label = "Meldungsnummer", value = meldungsNummer, onValueChange = { meldungsNummer = it }, icon = Icons.Default.ConfirmationNumber)
                }
            }
            EditInfoCard(
                label = "Gütesicherung (GüSi)",
                value = guesi,
                onValueChange = { guesi = it },
                icon = Icons.Default.VerifiedUser
            )

            // --- DYNAMISCHE ANSPRECHPARTNER SEKTION ---
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Ansprechpartner (POC)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                // Button um manuell einen weiteren Kontakt hinzuzufügen, falls nötig
                TextButton(onClick = {
                    pocNames = pocNames + ""
                    pocTels = pocTels + ""
                    pocEmails = pocEmails + ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Hinzufügen", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Wir loopen über die Anzahl der Namen (mindestens 1)
            pocNames.forEachIndexed { index, name ->
                ContactCard(
                    name = name,
                    tel = pocTels.getOrElse(index) { "" },
                    email = pocEmails.getOrElse(index) { "" },
                    onNameChange = { newName ->
                        val newList = pocNames.toMutableList()
                        newList[index] = newName
                        pocNames = newList
                    },
                    onTelChange = { newTel ->
                        val newList = pocTels.toMutableList()
                        newList[index] = newTel
                        pocTels = newList
                    },
                    onEmailChange = { newEmail ->
                        val newList = pocEmails.toMutableList()
                        newList[index] = newEmail
                        pocEmails = newList
                    },
                    onDelete = {
                        pocNames = pocNames.toMutableList().apply { removeAt(index) }
                        pocTels = pocTels.toMutableList().apply { removeAt(index) }
                        pocEmails = pocEmails.toMutableList().apply { removeAt(index) }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            // Text Sektion
            Text("Auftragstext", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = kurzText,
                maxLines = 2,
                onValueChange = { kurzText = it },
                label = { Text("Betreff (Kurz)") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )

            OutlinedTextField(
                value = langText,
                onValueChange = { langText = it },
                label = { Text("Ausführliche Beschreibung") },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.firstOrNull { it.changedToDown() }
                                if (down != null) {
                                    lastTouchY = down.position.y
                                    
                                    // Triggere den Scroll-Befehl bei JEDEM Tippen
                                    if (isDescriptionFocused) {
                                        coroutineScope.launch {
                                            delay(100)
                                            val extraMargin = 400f 
                                            bringIntoViewRequester.bringIntoView(
                                                Rect(0f, lastTouchY, 1f, lastTouchY + extraMargin)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .onFocusEvent { 
                        isDescriptionFocused = it.isFocused
                        if (it.isFocused) {
                            coroutineScope.launch {
                                delay(400)
                                val extraMargin = 400f
                                bringIntoViewRequester.bringIntoView(
                                    Rect(0f, lastTouchY, 1f, lastTouchY + extraMargin)
                                )
                            }
                        }
                    },
                minLines = 5
            )

            // Puffer am Ende, damit man über das letzte Feld hinaus scrollen kann
            //Spacer(Modifier.height(400.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Abbrechen")
                }
                Button(
                    onClick = {
                        val finalKunde = if (schiffState.isNotEmpty()) "🚢 $schiffState | $kundeState" else kundeState
                        onSave(initialAuftrag.copy(
                            auftragsNummer = auftragsNummer,
                            kunde = finalKunde,
                            projektNr = projektNr,
                            liegeplatz = liegeplatz,
                            ihNummer = ihNummer,
                            meldungsNummer = meldungsNummer,
                            titelKurz = kurzText,
                            beschreibungLang = langText,
                            positionsNummer = positionsNummer,
                            durchfuehrungsZeitraum = zeitraum,
                            abrechnungsArt = abrechnung,
                            ansprechpartnerName = pocNames.joinToString("\n"),
                            ansprechpartnerTelefon = pocTels.joinToString("\n"),
                            ansprechpartnerEmail = pocEmails.joinToString("\n"),
                            kundenBestellText = kundenBestellText
                        ))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Speichern")
                }
            }
        }
    }
}

@Composable
fun EditInfoCard(label: String, value: String, onValueChange: (String) -> Unit, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall)
                TextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (value.length > 18) 12.sp else if (value.length > 14) 14.sp else 16.sp
                    ),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun ContactCard(
    name: String,
    tel: String,
    email: String,
    onNameChange: (String) -> Unit,
    onTelChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ansprechpartner", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name / Abteilung") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 2,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tel,
                    onValueChange = onTelChange,
                    label = { Text("Telefon") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp)) }
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text("E-Mail") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}