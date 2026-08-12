package com.zabbel.diersapp.ui.screens

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
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
import com.zabbel.diersapp.data.model.TimeTrackingDialog
import com.zabbel.diersapp.data.model.WochenberichtInfo
import com.zabbel.diersapp.ui.components.WochenberichtTopAppBar
import com.zabbel.diersapp.util.ReportGenerator
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import com.zabbel.diersapp.viewmodel.calculateDecimalHours
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WochenberichtScreen(
    viewModel: AuftragViewModel,
    onBack: () -> Unit
) {
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val weekRange = remember(selectedCalendar) { viewModel.getWeekRange(selectedCalendar) }

    val weeklyWorkHours by viewModel.getWorkHoursForWeek(weekRange.first, weekRange.second)
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    val auftraege by viewModel.auftraege.collectAsStateWithLifecycle()
    val fullAuftraege by viewModel.allAuftraege.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val wochenInfo by viewModel.getWochenInfo(selectedCalendar).collectAsStateWithLifecycle(initialValue = null)

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

    val openReisekosten: () -> Unit = {
        showReisekostenDialog = true
    }

    val targetHours = userSettings?.wochenstunden ?: 37.0
    val totalHours = weeklyWorkHours.values.flatten().sumOf { calculateDecimalHoursFromEntry(it) }
    val missingDescriptions = weeklyWorkHours.values.flatten().any { it.beschreibung.isNullOrBlank() }

    val firstFreeDay = remember(weeklyWorkHours, weekRange) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            // Wir addieren 12h Puffer, um sicher im richtigen UTC-Tag zu landen (verhindert Sprung auf Sonntag)
            timeInMillis = weekRange.first + 12 * 60 * 60 * 1000L 
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val mondayUtc = cal.timeInMillis
        for (i in 0..6) {
            val currentTs = mondayUtc + i * 24 * 60 * 60 * 1000L
            if (weeklyWorkHours[currentTs].isNullOrEmpty()) return@remember currentTs
        }
        mondayUtc
    }
    
    val overflowDays = remember(weeklyWorkHours, fullAuftraege) {
        weeklyWorkHours.filter { (_, entries) ->
            calculateTotalLinesForDay(entries, fullAuftraege) > 7
        }.keys
    }
    val hasLineOverflow = overflowDays.isNotEmpty()
    
    val isHoursOk = totalHours >= targetHours
    val canExport = isHoursOk && !missingDescriptions && !hasLineOverflow

    Scaffold(
        topBar = {
            WochenberichtTopAppBar(
                onBack = onBack,
                onAddEntry = { openTimeTracking(null, firstFreeDay, emptyList()) },
                onReisekosten = openReisekosten
            )
        },
        floatingActionButton = {
            val context = LocalContext.current
            ExtendedFloatingActionButton(
                onClick = {
                    if (!canExport) return@ExtendedFloatingActionButton

                    val file = ReportGenerator.erzeugeWochenberichtPdf(
                        context = context,
                        kalenderWoche = selectedCalendar.get(Calendar.WEEK_OF_YEAR),
                        jahr = selectedCalendar.get(Calendar.YEAR),
                        daten = weeklyWorkHours,
                        auftraege = fullAuftraege,
                        vorname = userSettings?.vorname ?: "",
                        nachname = userSettings?.nachname ?: "",
                        persNr = userSettings?.personalnummer ?: "",
                        signatureBase64 = userSettings?.signatureBase64,
                        wochenInfo = wochenInfo
                    )

                    if (file != null) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            file
                        )
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "PDF öffnen"))
                    }
                },
                icon = { Icon(if (canExport) Icons.Default.PictureAsPdf else Icons.Default.Error, null) },
                text = { Text(if (hasLineOverflow) "Zu viel Text!" else "Exportieren") },
                containerColor = if (canExport) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (showDialog) {
            TimeTrackingDialog(
                initialDate = selectedTimestamp ?: System.currentTimeMillis(),
                availableAuftraege = auftraege,
                existingEntry = selectedEntry,
                dayEntries = currentDayEntries,
                allWeekEntries = weeklyWorkHours,
                weekRange = weekRange,
                onDismiss = { showDialog = false },
                onDelete = { viewModel.deleteArbeitszeit(it) },
                onSave = { date, auftragId, von, bis, pause, _, beschreibung, fahrtstunden, override, rVon, rBis, rrVon, rrBis, task, id ->
                    viewModel.saveQuickArbeitszeit(
                        currentAuftragId = auftragId,
                        datum = date,
                        von = von,
                        bis = bis,
                        pause = pause,
                        beschreibung = beschreibung, // Hier den unnötigen Null-Check entfernt
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
                onSave = { monat: String, euro: Double, km: Double ->
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

            ValidationSummary(totalHours, targetHours, isHoursOk, missingDescriptions, hasLineOverflow)

            HorizontalDivider()

            if (weeklyWorkHours.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Keine Einträge für diese Woche gefunden.", color = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp).background(MaterialTheme.colorScheme.background),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    val sortedDays = weeklyWorkHours.keys.sorted()
                    items(sortedDays) { timestamp ->
                        val entries = weeklyWorkHours[timestamp] ?: emptyList()
                        val isOverflow = overflowDays.contains(timestamp)
                        val nextDay = timestamp + 24 * 60 * 60 * 1000L
                        val hasNextDayEntries = weeklyWorkHours.containsKey(nextDay) && weeklyWorkHours[nextDay]?.isNotEmpty() == true
                        val isFriday = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
                        
                        WochenberichtTagCard(
                            timestamp = timestamp, 
                            entries = entries, 
                            auftraege = fullAuftraege,
                            isOverflow = isOverflow,
                            canCopy = !hasNextDayEntries && !isFriday,
                            onAddEntry = { openTimeTracking(null, timestamp, entries) },
                            onEditEntry = { entry -> openTimeTracking(entry, timestamp, entries) },
                            onCopyDay = { viewModel.copyDayEntries(timestamp) }
                        )
                    }
                }
            }
        }
    }
}

fun calculateTotalLinesForDay(entries: List<Arbeitszeit>, auftraege: List<com.zabbel.diersapp.data.model.Betriebsauftrag>): Int {
    val paint = Paint().apply { textSize = 9f }
    var totalUsedLines = 0

    entries.sortedBy { it.vonUhrzeit }.forEach { zeit ->
        val auftrag = auftraege.find { it.id == zeit.auftragId }
        val schiff = if (auftrag?.kunde?.contains("🚢") == true) auftrag.kunde.substringAfter("🚢 ").substringBefore(" |") else ""
        val vorschauText = buildString {
            if (schiff.isNotBlank()) append("$schiff -> ")
            else if (auftrag?.kunde == "INTERN" && (auftrag.titelKurz == "Schulung" || auftrag.titelKurz == "Werkstatt" || auftrag.titelKurz == "Besichtigung" || auftrag.titelKurz == "Arztbesuch")) {
                append("${auftrag.titelKurz} -> ")
            }
            if (auftrag != null && auftrag.kunde != "INTERN") append("${auftrag.titelKurz} -> ")
            append(zeit.beschreibung ?: "")
        }

        val lines = wrapTextForCheck(vorschauText, paint)
        totalUsedLines += if (lines.size > 1) lines.size else 1
    }
    return totalUsedLines
}
private const val MAX_WIDTH = 240f
private fun wrapTextForCheck(text: String, paint: Paint): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var currentLine = ""
    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(testLine) <= MAX_WIDTH) {
            currentLine = testLine
        } else {
            if (currentLine.isNotEmpty()) lines.add(currentLine)
            currentLine = word
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine)
    return lines
}

@Composable
fun ValidationSummary(totalHours: Double, targetHours: Double, isHoursOk: Boolean, missingDesc: Boolean, hasOverflow: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHoursOk && !missingDesc && !hasOverflow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isHoursOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Wochenstunden: ${String.format(Locale.GERMANY, "%.2f", totalHours)} / ${String.format(Locale.GERMANY, "%.2f", targetHours)} h",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            if (missingDesc) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Beschreibungen fehlen!", color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (hasOverflow) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Text zu lang für den Vordruck (max. 7 Zeilen)!", color = Color.Yellow, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WeekSelector(calendar: Calendar, onPreviousWeek: () -> Unit, onNextWeek: () -> Unit) {
    val kw = calendar.get(Calendar.WEEK_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousWeek) { Icon(Icons.Default.ChevronLeft, "Vorherige Woche") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Kalenderwoche $kw", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "Jahr $year", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNextWeek) { Icon(Icons.Default.ChevronRight, "Nächste Woche") }
        }
    }
}

@Composable
fun WochenberichtTagCard(
    timestamp: Long,
    entries: List<Arbeitszeit>,
    auftraege: List<com.zabbel.diersapp.data.model.Betriebsauftrag>,
    isOverflow: Boolean = false,
    canCopy: Boolean = true,
    onAddEntry: () -> Unit,
    onEditEntry: (Arbeitszeit) -> Unit,
    onCopyDay: () -> Unit
) {
    val dateStr = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMANY).format(Date(timestamp))
    val totalHours = entries.sumOf { calculateDecimalHoursFromEntry(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverflow) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isOverflow) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onAddEntry() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$dateStr (${String.format(Locale.GERMANY, "%.2f", totalHours)} h)", 
                        fontWeight = FontWeight.Bold, 
                        color = if (isOverflow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canCopy) {
                        IconButton(onClick = onCopyDay) { Icon(Icons.Default.ContentCopy, "Tag kopieren", modifier = Modifier.size(20.dp)) }
                    }
                    if (isOverflow) {
                        Icon(Icons.Default.Warning, "Überlauf", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    } else {
                        IconButton(onClick = onAddEntry) { Icon(Icons.Default.Add, "Eintrag hinzufügen", modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            entries.forEach { zeit ->
                val auftrag = auftraege.find { it.id == zeit.auftragId }
                val stunden = calculateDecimalHoursFromEntry(zeit)
                val schiff = if (auftrag?.kunde?.contains("🚢") == true) auftrag.kunde.substringAfter("🚢 ").substringBefore(" |") else ""
                val vorschauText = buildString {
                    if (schiff.isNotBlank()) append("$schiff -> ")
                    else if (auftrag != null && auftrag.kunde == "INTERN" && (auftrag.titelKurz == "Schulung" || auftrag.titelKurz == "Werkstatt" || auftrag.titelKurz == "Besichtigung")) {
                        append("${auftrag.titelKurz} -> ")
                    }
                    if (auftrag != null && auftrag.kunde != "INTERN") append("${auftrag.titelKurz} -> ")
                    append(zeit.beschreibung ?: "FEHLT")
                }

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onEditEntry(zeit) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vorschauText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (zeit.beschreibung.isNullOrBlank()) Color.Red else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${zeit.vonUhrzeit} - ${zeit.bisUhrzeit} (${String.format(Locale.GERMANY, "%.2f", stunden)} h)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (isOverflow) {
                Text(
                    "Dieser Tag hat zu viele Zeilen für den Druck!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

fun calculateDecimalHoursFromEntry(zeit: Arbeitszeit): Double {
    val von = zeit.vonUhrzeit.split(":")
    val bis = zeit.bisUhrzeit.split(":")
    return if (von.size == 2 && bis.size == 2) {
        calculateDecimalHours(von[0].toInt(), von[1].toInt(), bis[0].toInt(), bis[1].toInt(), zeit.pauseMinuten)
    } else 0.0
}
