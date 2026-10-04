package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zabbel.diersapp.data.model.Arbeitszeit
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.data.model.TimeTrackingDialog
import com.zabbel.diersapp.data.model.WochenberichtInfo
import com.zabbel.diersapp.util.MontageReportGenerator
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import java.text.SimpleDateFormat
import java.util.*
import android.net.Uri
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MontageberichtScreen(
    viewModel: AuftragViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val weekRange = remember(selectedCalendar) { viewModel.getWeekRange(selectedCalendar) }

    // Alle Buchungen der Woche
    val weeklyWorkHours by viewModel.getWorkHoursForWeek(weekRange.first, weekRange.second)
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    val auftraege by viewModel.auftraege.collectAsStateWithLifecycle()
    val fullAuftraege by viewModel.allAuftraege.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val wochenInfo by viewModel.getWochenInfo(selectedCalendar).collectAsStateWithLifecycle(initialValue = null)

    // Filter für Aufträge "nach Aufwand"
    // INTERN wird durch viewModel.auftraege bereits ausgeschlossen
    val aufwandAuftraege = remember(auftraege) {
        auftraege.filter { it.abrechnungsArt.contains("Aufwand", ignoreCase = true) }
    }
    
    var selectedAuftrag by remember { mutableStateOf<Betriebsauftrag?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Filtern der Stunden: nur die für den ausgewählten Auftrag
    val filteredWorkHours = remember(weeklyWorkHours, selectedAuftrag) {
        if (selectedAuftrag == null) emptyMap()
        else {
            weeklyWorkHours.mapValues { (_, entries) ->
                entries.filter { it.auftragId == selectedAuftrag!!.id }
            }.filter { it.value.isNotEmpty() }
        }
    }

    // --- VALIDIERUNG ---
    val overflowDays = remember(filteredWorkHours) {
        filteredWorkHours.filter { (_, entries) ->
            calculateTotalLinesForMontageDay(entries) > 4
        }.keys
    }
    val hasLineOverflow = overflowDays.isNotEmpty()
    val canExport = selectedAuftrag != null && filteredWorkHours.isNotEmpty() && !hasLineOverflow

    var showDialog by remember { mutableStateOf(false) }
    var showReisekostenDialog by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<Arbeitszeit?>(null) }
    var currentDayEntries by remember { mutableStateOf<List<Arbeitszeit>>(emptyList()) }
    var selectedTimestamp by remember { mutableStateOf<Long?>(null) }

    val openTimeTracking: (Arbeitszeit?, Long?, List<Arbeitszeit>) -> Unit = { entry, timestamp, dayEntries ->
        selectedEntry = entry
        selectedTimestamp = timestamp
        currentDayEntries = dayEntries
        showDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Montagebericht") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            if (selectedAuftrag != null && filteredWorkHours.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!canExport) return@ExtendedFloatingActionButton
                        val file = MontageReportGenerator.erzeugeMontageberichtPdf(
                            context = context,
                            kalenderWoche = selectedCalendar.get(Calendar.WEEK_OF_YEAR),
                            jahr = selectedCalendar.get(Calendar.YEAR),
                            daten = filteredWorkHours,
                            selectedAuftrag = selectedAuftrag!!,
                            vorname = userSettings?.vorname ?: "",
                            nachname = userSettings?.nachname ?: "",
                            signatureBase64 = userSettings?.signatureBase64
                        )

                        if (file != null) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val encodedUri = Uri.encode(uri.toString())
                            navController.navigate("pdf_viewer/$encodedUri")
                        }
                    },
                    icon = { Icon(if (hasLineOverflow) Icons.Default.Error else Icons.Default.PictureAsPdf, null) },
                    text = { Text(if (hasLineOverflow) "Text zu lang!" else "Exportieren") },
                    containerColor = if (canExport) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.errorContainer
                )
            }
        }
    ) { padding ->
        if (showDialog) {
            TimeTrackingDialog(
                initialDate = selectedTimestamp ?: System.currentTimeMillis(),
                initialAuftragId = selectedAuftrag?.id ?: 0L,
                availableAuftraege = aufwandAuftraege, // Hier nur die relevanten zeigen
                existingEntry = selectedEntry,
                dayEntries = currentDayEntries,
                weekRange = weekRange,
                showQuickTasks = false,
                maxLines = 4, // Montagebericht hat nur 4 Zeilen pro Tag
                onDismiss = { showDialog = false },
                onDelete = { viewModel.deleteArbeitszeit(it) },
                onSave = { date, auftragId, von, bis, pause, _, beschreibung, fahrtstunden, override, rVon, rBis, rrVon, rrBis, task, id ->
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
                        quickTask = task,
                        id = id
                    )
                    showDialog = false
                }
            )
        }

        if (showReisekostenDialog) {
            ReisekostenDialog(
                currentInfo = wochenInfo ?: WochenberichtInfo(selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.WEEK_OF_YEAR)),
                onDismiss = { showReisekostenDialog = false },
                onSave = { monat, euro, km ->
                    viewModel.saveWochenInfo(
                        jahr = selectedCalendar.get(Calendar.YEAR),
                        kw = selectedCalendar.get(Calendar.WEEK_OF_YEAR),
                        telefonMonat = monat,
                        telefonEuro = euro,
                        privatKm = km
                    )
                    showReisekostenDialog = false
                }
            )
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            WeekSelector(
                calendar = selectedCalendar,
                onPreviousWeek = {
                    val newCal = selectedCalendar.clone() as Calendar
                    newCal.add(Calendar.WEEK_OF_YEAR, -1)
                    selectedCalendar = newCal
                },
                onNextWeek = {
                    val newCal = selectedCalendar.clone() as Calendar
                    newCal.add(Calendar.WEEK_OF_YEAR, 1)
                    selectedCalendar = newCal
                }
            )

            // --- AUFTRAGS-AUSWAHL ---
            Box(modifier = Modifier.padding(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = when {
                            selectedAuftrag != null -> "${selectedAuftrag!!.auftragsNummer} - ${selectedAuftrag!!.titelKurz}"
                            aufwandAuftraege.isEmpty() -> "Kein Auftrag vorhanden"
                            else -> "Auftrag wählen..."
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Auftrag (nur nach Aufwand)") },
                        trailingIcon = { if (aufwandAuftraege.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    if (aufwandAuftraege.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            aufwandAuftraege.forEach { auftrag ->
                                DropdownMenuItem(
                                    text = { Text("${auftrag.auftragsNummer} - ${auftrag.titelKurz}") },
                                    onClick = {
                                        selectedAuftrag = auftrag
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedAuftrag == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Bitte wählen Sie einen Auftrag aus.")
                }
            } else if (filteredWorkHours.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Keine Einträge für diesen Auftrag in dieser Woche.")
                        Button(onClick = { openTimeTracking(null, null, emptyList()) }) {
                            Text("Ersten Eintrag hinzufügen")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    val sortedDays = filteredWorkHours.keys.sorted()
                    items(sortedDays) { timestamp ->
                        val entries = filteredWorkHours[timestamp] ?: emptyList()
                        val isOverflow = overflowDays.contains(timestamp)
                        MontageberichtTagCard(
                            timestamp = timestamp,
                            entries = entries,
                            auftraege = fullAuftraege,
                            isOverflow = isOverflow,
                            onAddEntry = { openTimeTracking(null, timestamp, entries) },
                            onEditEntry = { entry -> openTimeTracking(entry, timestamp, entries) }
                        )
                    }
                }
            }
        }
    }
}

fun calculateTotalLinesForMontageDay(entries: List<Arbeitszeit>): Int {
    val paint = android.graphics.Paint().apply { textSize = 9f }
    var totalLines = 0
    entries.forEach { zeit ->
        // Hauptzeile (Arbeit + ggf. Hinfahrt)
        val words = (zeit.beschreibung ?: "").split(" ")
        var currentLine = ""
        var entryLines = 0
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= 275f) {
                currentLine = testLine
            } else {
                entryLines++
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) entryLines++
        totalLines += maxOf(1, entryLines)
        
        // Zusatzzeile für Rückfahrt
        if (!zeit.rueckreiseVon.isNullOrBlank()) {
            totalLines++
        }
    }
    return totalLines
}

@Composable
fun MontageberichtTagCard(
    timestamp: Long,
    entries: List<Arbeitszeit>,
    auftraege: List<Betriebsauftrag>,
    isOverflow: Boolean = false,
    onAddEntry: () -> Unit,
    onEditEntry: (Arbeitszeit) -> Unit
) {
    val dateStr = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMANY).format(Date(timestamp))
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverflow) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isOverflow) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, fontWeight = FontWeight.Bold, color = if (isOverflow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                IconButton(onClick = onAddEntry) { Icon(Icons.Default.Add, null) }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            entries.forEach { zeit ->
                val auftrag = auftraege.find { it.id == zeit.auftragId }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditEntry(zeit) }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = auftrag?.titelKurz ?: "Unbekannt",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = zeit.beschreibung ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                        if (zeit.ausloese > 0) {
                            Text("Auslöse: ${String.format(Locale.GERMANY, "%.2f €", zeit.ausloese)}",
                                 style = MaterialTheme.typography.labelSmall, 
                                 color = Color(0xFF2E7D32))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${zeit.vonUhrzeit} - ${zeit.bisUhrzeit}", style = MaterialTheme.typography.bodySmall)
                        if (zeit.fahrtstunden > 0) {
                            Text("Fahrt: ${zeit.fahrtstunden}h", 
                                 style = MaterialTheme.typography.bodySmall, 
                                 color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }

            if (isOverflow) {
                Text(
                    "Dieser Tag hat zu viele Zeilen für den Vordruck (max. 4)!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
