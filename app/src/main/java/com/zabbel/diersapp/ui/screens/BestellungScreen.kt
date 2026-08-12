package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.data.model.Bestellung
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import com.zabbel.diersapp.util.OrderReportGenerator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BestellungScreen(
    auftrag: Betriebsauftrag,
    viewModel: AuftragViewModel,
    onBack: () -> Unit
) {
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var typ by remember { mutableStateOf(Bestellung.BestellTyp.ANFORDERUNG) }
    var kategorie by remember { mutableStateOf(Bestellung.BestellKategorie.MATERIAL) }
    var name by remember(userSettings) { mutableStateOf("${userSettings?.vorname ?: ""} ${userSettings?.nachname ?: ""}") }
    var datum by remember { mutableStateOf(SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date())) }
    var projekt by remember { 
        val schiff = if (auftrag.kunde.contains("🚢")) auftrag.kunde.substringAfter("🚢 ").substringBefore(" |") else ""
        mutableStateOf(schiff.ifBlank { auftrag.titelKurz }) 
    }
    var projektNr by remember { mutableStateOf(auftrag.projektNr ?: "") }
    var auftragsNr by remember { mutableStateOf(auftrag.auftragsNummer) }
    var ihNr by remember { mutableStateOf(auftrag.ihNummer ?: "") }
    var posNr by remember { mutableStateOf(auftrag.positionsNummer) }
    var anforderungstext by remember { mutableStateOf("") }
    var lieferantenVorschlag by remember { mutableStateOf("") }
    var prioritaet by remember { mutableStateOf(Bestellung.BestellPrio.EILIG) }
    var sofortBisDatum by remember { mutableStateOf("") }

    val isFormValid = name.isNotBlank() &&
            datum.isNotBlank() &&
            projekt.isNotBlank() &&
            projektNr.isNotBlank() &&
            auftragsNr.isNotBlank() &&
            posNr.isNotBlank() &&
            anforderungstext.isNotBlank() &&
            lieferantenVorschlag.isNotBlank() &&
            (prioritaet != Bestellung.BestellPrio.SOFORT || sofortBisDatum.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Materialanforderung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isFormValid) return@ExtendedFloatingActionButton
                    val bestellung = Bestellung(
                        typ = typ,
                        kategorie = kategorie,
                        name = name,
                        projekt = projekt,
                        projektNr = projektNr,
                        auftragsNr = auftragsNr,
                        ihNr = ihNr,
                        posNr = posNr,
                        anforderungstext = anforderungstext,
                        lieferantenVorschlag = lieferantenVorschlag,
                        prioritaet = prioritaet,
                        sofortBisDatum = sofortBisDatum
                    )
                    
                    val file = OrderReportGenerator.erzeugeBestellungPdf(context, bestellung)
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
                icon = { Icon(Icons.Default.PictureAsPdf, null) },
                text = { Text("Exportieren") },
                containerColor = if (isFormValid) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.errorContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- TYP ---
            Text("Art der Anforderung", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = typ == Bestellung.BestellTyp.ANFORDERUNG,
                    onClick = { typ = Bestellung.BestellTyp.ANFORDERUNG },
                    label = { Text("Anforderung") }
                )
                FilterChip(
                    selected = typ == Bestellung.BestellTyp.ANFRAGE,
                    onClick = { typ = Bestellung.BestellTyp.ANFRAGE },
                    label = { Text("Anfrage") }
                )
            }

            // --- KATEGORIE ---
            Text("Kategorie", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Bestellung.BestellKategorie.entries.forEach { cat ->
                    FilterChip(
                        selected = kategorie == cat,
                        onClick = { kategorie = cat },
                        label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            // --- STAMMDATEN ---
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = datum, onValueChange = { datum = it }, label = { Text("Datum") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = projekt, onValueChange = { projekt = it }, label = { Text("Projekt / Schiff") }, modifier = Modifier.fillMaxWidth())
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = projektNr, onValueChange = { projektNr = it }, label = { Text("Projekt Nr.") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = auftragsNr, onValueChange = { auftragsNr = it }, label = { Text("Auftrags-Nr.") }, modifier = Modifier.weight(1f))
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = ihNr, onValueChange = { ihNr = it }, label = { Text("IH-Nr.") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = posNr, onValueChange = { posNr = it }, label = { Text("Pos.Nr.") }, modifier = Modifier.weight(1f))
            }

            // --- TEXTE ---
            Text("Details", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = anforderungstext, 
                onValueChange = { anforderungstext = it }, 
                label = { Text("Was wird angefordert? (Material, Menge...)") }, 
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            OutlinedTextField(
                value = lieferantenVorschlag, 
                onValueChange = { lieferantenVorschlag = it }, 
                label = { Text("Vorschlag Lieferant") }, 
                modifier = Modifier.fillMaxWidth()
            )

            // --- PRIORITÄT ---
            Text("Dringlichkeit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prioritaet == Bestellung.BestellPrio.EILIG, onClick = { prioritaet = Bestellung.BestellPrio.EILIG })
                    Text("Eilig")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prioritaet == Bestellung.BestellPrio.SOFORT, onClick = { prioritaet = Bestellung.BestellPrio.SOFORT })
                    Text("Sofort bis:")
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = sofortBisDatum, 
                        onValueChange = { sofortBisDatum = it; prioritaet = Bestellung.BestellPrio.SOFORT },
                        modifier = Modifier.height(56.dp),
                        placeholder = { Text("Datum") }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prioritaet == Bestellung.BestellPrio.NORMAL, onClick = { prioritaet = Bestellung.BestellPrio.NORMAL })
                    Text("Bei nächster Gelegenheit")
                }
            }
            
            Spacer(Modifier.height(80.dp)) // Platz für FAB
        }
    }
}
