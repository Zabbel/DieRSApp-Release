package com.zabbel.diersapp.data.model

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.*

@Entity(
    tableName = "arbeitszeiten",
    foreignKeys = [
        ForeignKey(
            entity = Betriebsauftrag::class,
            parentColumns = ["id"],
            childColumns = ["auftragId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["auftragId"])]
)
data class Arbeitszeit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val auftragId: Long,
    val datum: Long,
    val vonUhrzeit: String,
    val bisUhrzeit: String,
    val pauseMinuten: Int = 45,
    val outdoor: String? = null,
    val beschreibung: String? = null,
    val ueberstunden25: Double = 0.0,
    val ueberstunden50: Double = 0.0,
    val ueberstunden125: Double = 0.0,
    val fahrtstunden: Double = 0.0,
    val ausloese: Double = 0.0,
    val liegeplatzOverride: String? = null,
    val reiseVon: String? = null,
    val reiseBis: String? = null,
    val rueckreiseVon: String? = null,
    val rueckreiseBis: String? = null
)

data class QuickTask(
    val label: String,
    val description: String,
    val isSystemState: Boolean = false,
    val auftragsNummer: String? = null,
    val positionsNummer: String? = null
)

val quickTasks = listOf(
    QuickTask("Urlaub", "Urlaub", true),
    QuickTask("FA Abbau", "FA Abbau von Überstunden", true),
    QuickTask("Krank", "Krank", true),
    QuickTask("Arztbesuch", "Arztbesuch", true),
    QuickTask("Feiertag", "Gesetzlicher Feiertag", true),
    QuickTask("Aufräumen", "Werkstatt aufräumen", false, "1000", "10"),
    QuickTask("Betriebsfahrten", "Betriebsfahrten", false, "1002", "20"),
    QuickTask("Werkstatt", "Allgemeine Werkstatttätigkeiten", false, "1002", "30"),
    QuickTask("Stundenzettel", "Stundenzettel schreiben", false, "1002", "40"),
    QuickTask("Schulung", "Interne Schulungen", false, "1002", "50"),
    QuickTask("Besichtigung", "Ausschreibung und Besichtigungen", false, "1002", "100")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTrackingDialog(
    initialDate: Long = System.currentTimeMillis(),
    initialAuftragId: Long = 0L,
    availableAuftraege: List<Betriebsauftrag> = emptyList(),
    preselectedQuickTask: QuickTask? = null,
    existingEntry: Arbeitszeit? = null,
    dayEntries: List<Arbeitszeit> = emptyList(),
    allWeekEntries: Map<Long, List<Arbeitszeit>>? = null,
    weekRange: Pair<Long, Long>? = null,
    showQuickTasks: Boolean = true,
    maxLines: Int = 7,
    onDismiss: () -> Unit,
    onDelete: (Arbeitszeit) -> Unit = {},
    onSave: (Long, Long, String, String, Int, Double, String, Double, String?, String?, String?, String?, String?, QuickTask?, Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = existingEntry?.datum?.let { normalizeToUtcMidnight(it) } ?: normalizeToUtcMidnight(initialDate),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                if (weekRange == null) return true
                return utcTimeMillis >= weekRange.first && utcTimeMillis <= weekRange.second
            }
        }
    )
    val selectedDate = datePickerState.selectedDateMillis ?: normalizeToUtcMidnight(initialDate)

    val currentDayView = remember(selectedDate, dayEntries, allWeekEntries) {
        if (allWeekEntries != null) {
            allWeekEntries[selectedDate] ?: emptyList()
        } else {
            dayEntries
        }
    }

    val showDatePicker = remember { mutableStateOf(false) }
    val showStartPicker = remember { mutableStateOf(false) }
    val showEndPicker = remember { mutableStateOf(false) }
    val showReiseStartPicker = remember { mutableStateOf(false) }
    val showReiseEndPicker = remember { mutableStateOf(false) }
    val showRueckStartPicker = remember { mutableStateOf(false) }
    val showRueckEndPicker = remember { mutableStateOf(false) }

    var selectedAuftragId by remember(existingEntry, initialAuftragId) { mutableLongStateOf(existingEntry?.auftragId ?: initialAuftragId) }
    var currentQuickTask by remember { mutableStateOf(preselectedQuickTask) }
    var travelOption by remember(existingEntry) {
        mutableIntStateOf(when {
            !existingEntry?.rueckreiseVon.isNullOrBlank() -> 2
            !existingEntry?.reiseVon.isNullOrBlank() -> 1
            else -> 0
        })
    }

    val startPickerState = rememberTimePickerState(is24Hour = true)
    val endPickerState = rememberTimePickerState(is24Hour = true)
    val reiseStartPickerState = rememberTimePickerState(initialHour = 6, is24Hour = true)
    val reiseEndPickerState = rememberTimePickerState(initialHour = 9, initialMinute = 30, is24Hour = true)
    val rueckStartPickerState = rememberTimePickerState(initialHour = 16, is24Hour = true)
    val rueckEndPickerState = rememberTimePickerState(initialHour = 19, initialMinute = 30, is24Hour = true)

    val isFriday = remember(selectedDate) {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selectedDate }.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
    }
    
    fun formatTime(hour: Int, minute: Int) = String.format(Locale.GERMANY, "%02d:%02d", hour, minute)

    val suggestedRange = remember(currentDayView, selectedDate, existingEntry, currentQuickTask, travelOption, reiseEndPickerState.hour, reiseEndPickerState.minute) {
        if (existingEntry != null) Pair(existingEntry.vonUhrzeit, existingEntry.bisUhrzeit)
        else if (currentQuickTask?.isSystemState == true) Pair("07:00", if (isFriday) "12:15" else "15:45")
        else if (travelOption > 0) {
            val arr = formatTime(reiseEndPickerState.hour, reiseEndPickerState.minute)
            val workEnd = if (isFriday) "12:15" else "15:45"
            Pair(arr, if (compareTimes(arr, workEnd) < 0) workEnd else arr)
        } else findNextGap(currentDayView, isFriday)
    }

    LaunchedEffect(suggestedRange) {
        val parts1 = suggestedRange.first.split(":")
        val parts2 = suggestedRange.second.split(":")
        startPickerState.hour = parts1[0].toInt()
        startPickerState.minute = parts1[1].toInt()
        endPickerState.hour = parts2[0].toInt()
        endPickerState.minute = parts2[1].toInt()
    }

    var pauseMinutes by remember(existingEntry) { mutableIntStateOf(existingEntry?.pauseMinuten ?: 0) }
    var beschreibung by remember(existingEntry) { mutableStateOf(existingEntry?.beschreibung ?: currentQuickTask?.description ?: "") }
    var fahrtstundenStr by remember(existingEntry) { mutableStateOf(existingEntry?.fahrtstunden?.toString() ?: "") }
    
    // --- ZEILEN-CHECK LOGIK ---
    val paint = remember { android.graphics.Paint().apply { textSize = 9f } }
    val maxWorkWidth = 275f
    val currentUsedLines = remember(beschreibung, selectedAuftragId, currentDayView, existingEntry) {
        val otherLines = currentDayView
            .filter { it.auftragId == selectedAuftragId && it.id != (existingEntry?.id ?: -1L) }
            .sumOf { calculateLinesForText(it.beschreibung ?: "", paint, maxWorkWidth) }
        val thisLines = calculateLinesForText(beschreibung, paint, maxWorkWidth)
        otherLines + thisLines
    }
    var selectedLiegeplatzOverride by remember(existingEntry, selectedAuftragId) { mutableStateOf(existingEntry?.liegeplatzOverride) }

    val currentAuftrag = availableAuftraege.find { it.id == selectedAuftragId }
    val originalLiegeplatz = currentAuftrag?.liegeplatz ?: ""
    val effectiveLiegeplatz = selectedLiegeplatzOverride ?: originalLiegeplatz
    val isMontageLocation = remember(effectiveLiegeplatz) {
        val lp = effectiveLiegeplatz.trim().lowercase()
        // Wir nutzen 'contains', damit auch "4. Einfahrt" oder "Hannoverkai LP 3" erkannt werden
        val internalKeywords = listOf("njw", "hannoverkai", "werkstatt", "einfahrt", "mars")
        lp.isNotBlank() && !internalKeywords.any { lp.contains(it) }
    }

    LaunchedEffect(startPickerState.hour, startPickerState.minute, endPickerState.hour, endPickerState.minute) {
        pauseMinutes = calculatePauseForPeriod(startPickerState.hour, startPickerState.minute, endPickerState.hour, endPickerState.minute)
    }

    LaunchedEffect(travelOption, reiseStartPickerState.hour, reiseStartPickerState.minute, reiseEndPickerState.hour, reiseEndPickerState.minute, rueckStartPickerState.hour, rueckStartPickerState.minute, rueckEndPickerState.hour, rueckEndPickerState.minute) {
        if (travelOption > 0) {
            val h1 = calculateDecimalHours(reiseStartPickerState.hour, reiseStartPickerState.minute, reiseEndPickerState.hour, reiseEndPickerState.minute, 0)
            val h2 = if (travelOption == 2) calculateDecimalHours(rueckStartPickerState.hour, rueckStartPickerState.minute, rueckEndPickerState.hour, rueckEndPickerState.minute, 0) else 0.0
            fahrtstundenStr = String.format(Locale.GERMANY, "%.2f", h1 + h2).replace(",", ".")
        }
    }

    // Automatisches Setzen der Fahrtstunden (0.25h) bei bestimmten internen Liegeplätzen
    LaunchedEffect(effectiveLiegeplatz, existingEntry) {
        if (existingEntry == null && travelOption == 0) {
            val lp = effectiveLiegeplatz.lowercase()
            if (lp.contains("mars") || lp.contains("einfahrt")) {
                fahrtstundenStr = "0.25"
            } else if (lp.contains("werkstatt") || lp.contains("njw") || lp.contains("hannoverkai")) {
                fahrtstundenStr = "0.0"
            }
        }
    }

    var expanded by remember { mutableStateOf(false) }
    val showEditWarning = remember { mutableStateOf(false) }

    val autoFoundEntry = remember(selectedDate, selectedAuftragId, currentDayView) {
        currentDayView.find { it.auftragId == selectedAuftragId && it.id != (existingEntry?.id ?: -1L) }
    }

    LaunchedEffect(autoFoundEntry) {
        autoFoundEntry?.let { entry ->
            if (existingEntry == null) showEditWarning.value = true
            startPickerState.hour = entry.vonUhrzeit.split(":")[0].toInt()
            startPickerState.minute = entry.vonUhrzeit.split(":")[1].toInt()
            endPickerState.hour = entry.bisUhrzeit.split(":")[0].toInt()
            endPickerState.minute = entry.bisUhrzeit.split(":")[1].toInt()
            pauseMinutes = entry.pauseMinuten
            beschreibung = entry.beschreibung ?: ""
            fahrtstundenStr = entry.fahrtstunden.toString()
            selectedLiegeplatzOverride = entry.liegeplatzOverride
        }
    }

    val decimalHours = remember(startPickerState.hour, startPickerState.minute, endPickerState.hour, endPickerState.minute, pauseMinutes) {
        calculateDecimalHours(startPickerState.hour, startPickerState.minute, endPickerState.hour, endPickerState.minute, pauseMinutes)
    }

    val tomorrowMidnight = remember { 
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis 
    }
    val isFutureDate = selectedDate >= tomorrowMidnight
    val isPlanableTask = currentQuickTask?.isSystemState == true
    val isDateAllowed = !isFutureDate || isPlanableTask

    val isWorkTimeValid = (startPickerState.hour * 60 + startPickerState.minute) < (endPickerState.hour * 60 + endPickerState.minute)
    val isTravelTimeValid = travelOption == 0 || (reiseStartPickerState.hour * 60 + reiseStartPickerState.minute) < (reiseEndPickerState.hour * 60 + reiseEndPickerState.minute)
    val isReturnTimeValid = travelOption != 2 || (rueckStartPickerState.hour * 60 + rueckStartPickerState.minute) < (rueckEndPickerState.hour * 60 + rueckEndPickerState.minute)

    AlertDialog(
        onDismissRequest = { /* Ignorieren, damit man nicht versehentlich schließt */ },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        ),
        confirmButton = {
            Button(
                enabled = (selectedAuftragId != 0L || currentQuickTask != null) && 
                         beschreibung.isNotBlank() && 
                         currentUsedLines <= maxLines &&
                         isWorkTimeValid && isTravelTimeValid && isReturnTimeValid && 
                         isDateAllowed && (!isMontageLocation || travelOption > 0 || fahrtstundenStr.isNotBlank()),
                onClick = {
                    val rV = if (isMontageLocation && travelOption > 0) formatTime(reiseStartPickerState.hour, reiseStartPickerState.minute) else null
                    val rB = if (isMontageLocation && travelOption > 0) formatTime(reiseEndPickerState.hour, reiseEndPickerState.minute) else null
                    val rrV = if (isMontageLocation && travelOption == 2) formatTime(rueckStartPickerState.hour, rueckStartPickerState.minute) else null
                    val rrB = if (isMontageLocation && travelOption == 2) formatTime(rueckEndPickerState.hour, rueckEndPickerState.minute) else null
                    onSave(selectedDate, selectedAuftragId, formatTime(startPickerState.hour, startPickerState.minute), formatTime(endPickerState.hour, endPickerState.minute), pauseMinutes, decimalHours, beschreibung, fahrtstundenStr.toDoubleOrNull() ?: 0.0, selectedLiegeplatzOverride, rV, rB, rrV, rrB, currentQuickTask, existingEntry?.id ?: autoFoundEntry?.id ?: 0L)
                }
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                val isEditing = existingEntry != null || autoFoundEntry != null
                Text(if (isEditing) "Eintrag bearbeiten" else "Zeit buchen")
                if (isEditing) IconButton(onClick = { (existingEntry ?: autoFoundEntry)?.let { onDelete(it) }; onDismiss() }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentDayView.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Bisher gebucht:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            currentDayView.sortedBy { it.vonUhrzeit }.forEach { entry ->
                                val a = availableAuftraege.find { it.id == entry.auftragId }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "${entry.vonUhrzeit}-${entry.bisUhrzeit} ${a?.titelKurz ?: ""}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(text = String.format(Locale.GERMANY, "%.2f h", calculateDecimalHoursFromEntry(entry)), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("Tagessumme: ${String.format(Locale.GERMANY, "%.2f", currentDayView.sumOf { calculateDecimalHoursFromEntry(it) })} h", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                        }
                    }
                }
                if (showQuickTasks) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickTasks) { task ->
                            FilterChip(selected = currentQuickTask == task, onClick = { currentQuickTask = if (currentQuickTask == task) null else task; if (currentQuickTask != null) { beschreibung = if (task.label in listOf("Schulung", "Werkstatt", "Betriebsfahrten", "Besichtigung")) "" else task.description; if (!(task.isSystemState || task.label == "Betriebsfahrten" || task.label == "Besichtigung" || task.label == "Schulung")) selectedLiegeplatzOverride = "Werkstatt" } }, label = { Text(task.label, fontSize = 12.sp) })
                        }
                    }
                }
                if (currentQuickTask == null && initialAuftragId == 0L && availableAuftraege.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        val cA = availableAuftraege.find { it.id == selectedAuftragId }
                        OutlinedTextField(value = if (cA != null) "${cA.auftragsNummer} - ${cA.titelKurz}" else "Auftrag wählen...", onValueChange = {}, readOnly = true, label = { Text("Auftrag") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            availableAuftraege.forEach { a -> DropdownMenuItem(text = { Text("${a.auftragsNummer} - ${a.titelKurz}") }, onClick = { selectedAuftragId = a.id; expanded = false }) }
                        }
                    }
                }
                if (currentQuickTask == null && selectedAuftragId != 0L) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = selectedLiegeplatzOverride == null, onClick = { selectedLiegeplatzOverride = null }, label = { Text(originalLiegeplatz.ifBlank { "Schiff/Ort" }, fontSize = 11.sp) })
                        FilterChip(selected = selectedLiegeplatzOverride == "Werkstatt", onClick = { selectedLiegeplatzOverride = "Werkstatt" }, label = { Text("Werkstatt", fontSize = 11.sp) })
                        IconButton(onClick = { selectedLiegeplatzOverride = "" }) { Icon(Icons.Default.EditLocation, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    if (selectedLiegeplatzOverride != null && selectedLiegeplatzOverride != "Werkstatt") {
                        OutlinedTextField(value = selectedLiegeplatzOverride ?: "", onValueChange = { selectedLiegeplatzOverride = it }, label = { Text("Manueller Liegeplatz") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = { showDatePicker.value = true }, modifier = Modifier.fillMaxWidth(), colors = if (isFutureDate && !isPlanableTask) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)) else ButtonDefaults.outlinedButtonColors()) {
                    Icon(if (isFutureDate && !isPlanableTask) Icons.Default.Warning else Icons.Default.DateRange, null, modifier = Modifier.size(18.dp), tint = if (isFutureDate && !isPlanableTask) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Tag: ${SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(selectedDate)}", color = if (isFutureDate && !isPlanableTask) MaterialTheme.colorScheme.error else Color.Unspecified)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = formatTime(startPickerState.hour, startPickerState.minute), onValueChange = {}, label = { Text("Von") }, modifier = Modifier.weight(1f), readOnly = true, trailingIcon = { IconButton(onClick = { showStartPicker.value = true }) { Icon(Icons.Default.Schedule, null) } })
                    OutlinedTextField(value = formatTime(endPickerState.hour, endPickerState.minute), onValueChange = {}, label = { Text("Bis") }, modifier = Modifier.weight(1f), readOnly = true, trailingIcon = { IconButton(onClick = { showEndPicker.value = true }) { Icon(Icons.Default.Schedule, null) } })
                }
                OutlinedTextField(
                    value = beschreibung,
                    onValueChange = { beschreibung = it },
                    label = { Text("Beschreibung") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    isError = currentUsedLines > maxLines,
                    supportingText = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Zeilen: $currentUsedLines / $maxLines")
                            if (currentUsedLines > maxLines) {
                                Text("Zu viel Text für das PDF!", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
                if (isMontageLocation) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = travelOption == 0, onClick = { travelOption = 0 }, label = { Text("Keine Reise", fontSize = 11.sp) })
                        FilterChip(selected = travelOption == 1, onClick = { travelOption = 1 }, label = { Text("Einfach", fontSize = 11.sp) })
                        FilterChip(selected = travelOption == 2, onClick = { travelOption = 2 }, label = { Text("Hin & Rück", fontSize = 11.sp) })
                    }
                    if (travelOption > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = formatTime(reiseStartPickerState.hour, reiseStartPickerState.minute), onValueChange = {}, label = { Text("Abfahrt") }, modifier = Modifier.weight(1f), readOnly = true, trailingIcon = { IconButton(onClick = { showReiseStartPicker.value = true }) { Icon(Icons.Default.Schedule, null) } })
                            OutlinedTextField(value = formatTime(reiseEndPickerState.hour, reiseEndPickerState.minute), onValueChange = {}, label = { Text("Ankunft") }, modifier = Modifier.weight(1f), readOnly = true, trailingIcon = { IconButton(onClick = { showReiseEndPicker.value = true }) { Icon(Icons.Default.Schedule, null) } })
                        }
                    }
                    if (travelOption == 2) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = formatTime(rueckStartPickerState.hour, rueckStartPickerState.minute), onValueChange = {}, label = { Text("Rück: Start") }, modifier = Modifier.weight(1f), readOnly = true, trailingIcon = { IconButton(onClick = { showReiseStartPicker.value = true }) { Icon(Icons.Default.Schedule, null) } })
                            OutlinedTextField(value = formatTime(rueckEndPickerState.hour, rueckEndPickerState.minute), onValueChange = {}, label = { Text("Heim") }, modifier = Modifier.weight(1f), readOnly = true, trailingIcon = { IconButton(onClick = { showReiseEndPicker.value = true }) { Icon(Icons.Default.Schedule, null) } })
                        }
                    }
                }
                if (currentQuickTask?.isSystemState != true && effectiveLiegeplatz != "NJW") {
                    OutlinedTextField(value = fahrtstundenStr, onValueChange = { fahrtstundenStr = it }, label = { Text("Fahrtstunden") }, modifier = Modifier.fillMaxWidth())
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Pause: $pauseMinutes Min", style = MaterialTheme.typography.bodySmall); Text(text = String.format(Locale.GERMANY, "Gesamt: %.2f h", decimalHours), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                    Row { IconButton(onClick = { pauseMinutes = maxOf(0, pauseMinutes - 15) }) { Icon(Icons.Default.RemoveCircleOutline, null) }; IconButton(onClick = { pauseMinutes += 15 }) { Icon(Icons.Default.AddCircleOutline, null) } }
                }
            }
        }
    )

    if (showDatePicker.value) DatePickerDialog(onDismissRequest = { showDatePicker.value = false }, confirmButton = { TextButton(onClick = { showDatePicker.value = false }) { Text("OK") } }) { DatePicker(state = datePickerState) }
    if (showStartPicker.value) InternalTimePickerDialog(onDismiss = { showStartPicker.value = false }, onConfirm = { showStartPicker.value = false }) { TimePicker(state = startPickerState) }
    if (showEndPicker.value) InternalTimePickerDialog(onDismiss = { showEndPicker.value = false }, onConfirm = { showEndPicker.value = false }) { TimePicker(state = endPickerState) }
    if (showReiseStartPicker.value) InternalTimePickerDialog(onDismiss = { showReiseStartPicker.value = false }, onConfirm = { showReiseStartPicker.value = false }) { TimePicker(state = reiseStartPickerState) }
    if (showReiseEndPicker.value) InternalTimePickerDialog(onDismiss = { showReiseEndPicker.value = false }, onConfirm = { showReiseEndPicker.value = false }) { TimePicker(state = reiseEndPickerState) }
    if (showRueckStartPicker.value) InternalTimePickerDialog(onDismiss = { showRueckStartPicker.value = false }, onConfirm = { showRueckStartPicker.value = false }) { TimePicker(state = rueckStartPickerState) }
    if (showRueckEndPicker.value) InternalTimePickerDialog(onDismiss = { showRueckEndPicker.value = false }, onConfirm = { showRueckEndPicker.value = false }) { TimePicker(state = rueckEndPickerState) }

    if (showEditWarning.value) {
        AlertDialog(
            onDismissRequest = { showEditWarning.value = false },
            title = { Text("Eintrag existiert bereits") },
            text = { Text("Für diesen Auftrag wurde am gewählten Tag bereits Arbeitszeit gebucht. Der bestehende Eintrag wurde geladen und kann nun bearbeitet werden.") },
            confirmButton = { Button(onClick = { showEditWarning.value = false }) { Text("OK") } }
        )
    }
}

fun findNextGap(dayEntries: List<Arbeitszeit>, isFriday: Boolean): Pair<String, String> {
    val workEnd = if (isFriday) "12:15" else "15:45"
    if (dayEntries.isEmpty()) return Pair("07:00", workEnd)
    val sorted = dayEntries.sortedBy { it.vonUhrzeit }
    var currentStart = "07:00"
    for (entry in sorted) {
        // Wenn zwischen dem aktuellen Ende (currentStart) und dem nächsten Eintrag eine Lücke ist
        if (compareTimes(currentStart, entry.vonUhrzeit) < 0) return Pair(currentStart, entry.vonUhrzeit)
        
        // Wir setzen den Zeiger auf das Ende des Eintrags, aber nur wenn dieser später liegt
        // (Wichtig bei überlappenden oder verschachtelten Einträgen)
        if (compareTimes(entry.bisUhrzeit, currentStart) > 0) {
            currentStart = entry.bisUhrzeit
        }
    }
    return if (compareTimes(currentStart, workEnd) < 0) Pair(currentStart, workEnd) else Pair(currentStart, currentStart)
}

fun calculatePauseForPeriod(startH: Int, startM: Int, endH: Int, endM: Int): Int {
    val startTotal = startH * 60 + startM
    val endTotal = endH * 60 + endM
    var totalPause = 0
    if (startTotal < 9 * 60 && endTotal > 8 * 60 + 45) {
        totalPause += 15
    }
    if (startTotal < 13 * 60 && endTotal > 12 * 60 + 30) {
        totalPause += 30
    }
    return totalPause
}

fun compareTimes(t1: String, t2: String): Int {
    val p1 = t1.split(":").map { it.toInt() }
    val p2 = t2.split(":").map { it.toInt() }
    return (p1[0] * 60 + p1[1]) - (p2[0] * 60 + p2[1])
}

fun calculateDecimalHours(startHour: Int, startMin: Int, endHour: Int, endMin: Int, breakMin: Int): Double {
    val durationMinutes = (endHour * 60 + endMin) - (startHour * 60 + startMin) - breakMin
    return if (durationMinutes > 0) durationMinutes / 60.0 else 0.0
}

fun calculateDecimalHoursFromEntry(zeit: Arbeitszeit): Double {
    val von = zeit.vonUhrzeit.split(":")
    val bis = zeit.bisUhrzeit.split(":")
    return if (von.size == 2 && bis.size == 2) {
        calculateDecimalHours(von[0].toInt(), von[1].toInt(), bis[0].toInt(), bis[1].toInt(), zeit.pauseMinuten)
    } else {
        0.0
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalTimePickerDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }, text = content)
}

fun calculateLinesForText(text: String, paint: android.graphics.Paint, maxWidth: Float): Int {
    if (text.isBlank()) return 1
    val words = text.split(" ")
    var lines = 0
    var currentLine = ""
    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(testLine) <= maxWidth) {
            currentLine = testLine
        } else {
            lines++
            currentLine = word
        }
    }
    if (currentLine.isNotEmpty()) {
        lines++
    }
    return maxOf(1, lines)
}

fun normalizeToUtcMidnight(timestamp: Long): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
