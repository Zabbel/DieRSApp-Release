package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zabbel.diersapp.data.model.Arbeitszeit
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.data.model.TimeTrackingDialog
import com.zabbel.diersapp.ui.components.AuftragDetailTopAppBar
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri
import android.content.Intent
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuftragDetailScreen(
    auftrag: Betriebsauftrag,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onBestellung: () -> Unit,
    viewModel: AuftragViewModel
) {
    val schiffAnzeige = if (auftrag.kunde.contains("🚢")) auftrag.kunde.substringAfter("🚢 ").substringBefore(" |") else ""
    val kundeAnzeige = if (auftrag.kunde.contains("|")) auftrag.kunde.substringAfter("| ") else auftrag.kunde
    
    var showTimeTrackingDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<Arbeitszeit?>(null) }
    var selectedTimestamp by remember { mutableStateOf<Long?>(null) }

    // Wir laden die Arbeitsstunden der Woche, um die Tagesübersicht im Dialog anzuzeigen
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val weekRange = remember(selectedCalendar) { viewModel.getWeekRange(selectedCalendar) }
    val weeklyWorkHours by viewModel.getWorkHoursForWeek(weekRange.first, weekRange.second)
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    // Die Einträge für den aktuell gewählten Tag im Dialog (reaktiv)
    val currentDayEntries = remember(weeklyWorkHours, selectedTimestamp) {
        if (selectedTimestamp == null) emptyList()
        else {
            // WICHTIG: UTC nutzen, da DatePicker und DB in UTC arbeiten
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selectedTimestamp!!
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            weeklyWorkHours[cal.timeInMillis] ?: emptyList()
        }
    }

    // Hilfsfunktion zum Öffnen des Dialogs
    val openTimeTracking: (Arbeitszeit?) -> Unit = { entry ->
        selectedEntry = entry
        val targetDate = entry?.datum ?: System.currentTimeMillis()
        selectedTimestamp = targetDate
        
        // Kalender auf das Datum setzen, damit die richtige Woche geladen wird
        val cal = Calendar.getInstance().apply { timeInMillis = targetDate }
        selectedCalendar = cal
        
        showTimeTrackingDialog = true
    }
    
    val totalHours by viewModel.getTotalHoursForAuftrag(auftrag.id).collectAsStateWithLifecycle(initialValue = 0.0)
    val entries by viewModel.getArbeitszeitenForAuftrag(auftrag.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val allAuftraege by viewModel.allAuftraege.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AuftragDetailTopAppBar(
                title = "Auftrag ${auftrag.auftragsNummer}",
                onBack = onBack,
                onEdit = onEdit,
                onTimeTracking = { openTimeTracking(null) },
                onCreateOrder = onBestellung
            )
        }
    ) { padding ->
        if (showTimeTrackingDialog) {
            TimeTrackingDialog(
                initialDate = selectedTimestamp ?: System.currentTimeMillis(),
                initialAuftragId = auftrag.id,
                availableAuftraege = allAuftraege,
                existingEntry = selectedEntry,
                dayEntries = currentDayEntries,
                allWeekEntries = weeklyWorkHours, // NEU: Damit die Übersicht beim Datumswechsel mitspringt
                weekRange = weekRange, // Richtige Woche übergeben
                showQuickTasks = false, // Chips ausblenden
                onDismiss = { showTimeTrackingDialog = false },
                onDelete = { viewModel.deleteArbeitszeit(it) },
                onSave = { date, auftragId, von, bis, pause, _, beschreibung, fahrtstunden, override, rVon, rBis, rrVon, rrBis, quickTask, id ->
                    viewModel.saveQuickArbeitszeit(
                        currentAuftragId = auftragId,
                        datum = date,
                        von = von,
                        bis = bis,
                        pause = pause,
                        beschreibung = beschreibung,
                        fahrtstunden = fahrtstunden,
                        liegeplatzOverride = override,
                        reiseVon = rVon,
                        reiseBis = rBis,
                        rueckreiseVon = rrVon,
                        rueckreiseBis = rrBis,
                        quickTask = quickTask,
                        id = id
                    )
                    showTimeTrackingDialog = false
                }
            )
        }
        
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- HEADER: GESAMTSTUNDEN ---
            if (totalHours > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Gesamte Arbeitszeit", style = MaterialTheme.typography.labelSmall)
                            Text(String.format(Locale.GERMANY, "%.2f h", totalHours), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- SEKTION: STAMMDATEN ---
            Text("Stammdaten", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            
            if (schiffAnzeige.isNotBlank()) InfoCard("Schiff / Objekt", schiffAnzeige, Icons.Default.DirectionsBoat)
            if (!auftrag.projektNr.isNullOrBlank()) InfoCard("Projekt-Nr.", auftrag.projektNr, Icons.Default.Tag)
            InfoCard("Auftragsnummer", auftrag.auftragsNummer, Icons.Default.Numbers)
            InfoCard("Kunde", kundeAnzeige, Icons.Default.Business)

            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                Box(modifier = Modifier.weight(1f)) {
                    InfoCard("Position", auftrag.positionsNummer, Icons.Default.Info, Modifier.fillMaxHeight())
                }
                if (!auftrag.ihNummer.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        InfoCard("IH-Nummer", auftrag.ihNummer, Icons.Default.Fingerprint, Modifier.fillMaxHeight())
                    }
                }
            }

            // --- SEKTION: DURCHFÜHRUNG & KONTAKT ---
            val hasExecutionData = !auftrag.liegeplatz.isNullOrBlank() || 
                                  !auftrag.durchfuehrungsZeitraum.isNullOrBlank() || 
                                  !auftrag.meldungsNummer.isNullOrBlank() || 
                                  !auftrag.guesi.isNullOrBlank()

            if (hasExecutionData) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Durchführung & Details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                
                if (!auftrag.liegeplatz.isNullOrBlank()) InfoCard("Liegeplatz", auftrag.liegeplatz, Icons.Default.Place)
                if (!auftrag.durchfuehrungsZeitraum.isNullOrBlank()) InfoCard("Zeitraum / Termin", auftrag.durchfuehrungsZeitraum, Icons.Default.DateRange)
                
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                    Box(modifier = Modifier.weight(1f)) {
                        InfoCard("Abrechnung", auftrag.abrechnungsArt, Icons.Default.Payments, Modifier.fillMaxHeight())
                    }
                    if (!auftrag.meldungsNummer.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            InfoCard("Meldungsnummer", auftrag.meldungsNummer, Icons.Default.ConfirmationNumber, Modifier.fillMaxHeight())
                        }
                    }
                }
                if (!auftrag.guesi.isNullOrBlank()) InfoCard("Gütesicherung (GüSi)", auftrag.guesi, Icons.Default.VerifiedUser)
            }

            // --- SEKTION: POC (ANSPRECHPARTNER) ---
            val pocNames = auftrag.ansprechpartnerName?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            val pocTels = auftrag.ansprechpartnerTelefon?.split("\n") ?: emptyList()
            val pocEmails = auftrag.ansprechpartnerEmail?.split("\n") ?: emptyList()

            if (pocNames.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Ansprechpartner (POC)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                pocNames.forEachIndexed { index, name ->
                    ContactInfoCard(
                        name = name,
                        tel = pocTels.getOrNull(index) ?: "",
                        email = pocEmails.getOrNull(index) ?: ""
                    )
                }
            }

            // --- SEKTION: INHALT ---
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text("Inhalt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(auftrag.titelKurz, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (auftrag.beschreibungLang.isNotBlank()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(auftrag.beschreibungLang, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // --- NEUE SEKTION: BUCHUNGEN (Ganz unten) ---
            if (entries.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Buchungen", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${entries.size} Einträge", style = MaterialTheme.typography.labelSmall)
                }
                
                entries.forEach { zeit ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openTimeTracking(zeit) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMANY).format(Date(zeit.datum)), style = MaterialTheme.typography.labelSmall)
                                Text(zeit.beschreibung ?: "Keine Beschreibung", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                if (zeit.fahrtstunden > 0) {
                                    Text("Fahrtzeit: ${String.format(Locale.GERMANY, "%.2f", zeit.fahrtstunden)} h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${zeit.vonUhrzeit} - ${zeit.bisUhrzeit}", fontWeight = FontWeight.Bold)
                                Text("Pause: ${zeit.pauseMinuten} min", style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, content: String, icon: ImageVector, modifier: Modifier = Modifier) {
    if (content.isNotBlank()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.labelSmall)
                    Text(content, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ContactInfoCard(name: String, tel: String, email: String) {
    val context = LocalContext.current
    var showSelectionDialog by remember { mutableStateOf(false) }

    fun launchDialer() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = "tel:${tel.replace(" ", "")}".toUri()
        }
        context.startActivity(intent)
    }

    fun launchEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$email".toUri()
        }
        context.startActivity(intent)
    }

    if (showSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showSelectionDialog = false },
            title = { Text("Kontaktieren") },
            text = { Text("Wie möchtest du $name kontaktieren?") },
            confirmButton = {
                TextButton(onClick = { launchDialer(); showSelectionDialog = false }) {
                    Icon(Icons.Default.Phone, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Anrufen")
                }
            },
            dismissButton = {
                TextButton(onClick = { launchEmail(); showSelectionDialog = false }) {
                    Icon(Icons.Default.Email, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("E-Mail")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tel.isNotBlank() || email.isNotBlank()) {
                when {
                    tel.isNotBlank() && email.isNotBlank() -> showSelectionDialog = true
                    tel.isNotBlank() -> launchDialer()
                    email.isNotBlank() -> launchEmail()
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            }
            if (tel.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tel, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (email.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(email, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
