package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnterschriftenListScreen(
    viewModel: AuftragViewModel,
    onBack: () -> Unit,
    onNavigateToSignature: (Long, Int, Int) -> Unit
) {
    val auftraege by viewModel.auftraege.collectAsStateWithLifecycle()
    val allSignatures by viewModel.allKundenUnterschriften.collectAsStateWithLifecycle()
    val allArbeitszeiten by viewModel.getArbeitszeitenInPeriodRaw(0, Long.MAX_VALUE).collectAsStateWithLifecycle(initialValue = emptyList())

    val aufwandAuftraege = remember(auftraege) {
        auftraege.filter { it.abrechnungsArt.contains("Aufwand", ignoreCase = true) }
    }

    // Wir ermitteln dynamisch alle (Jahr, KW) Kombinationen, an denen Zeiten für Aufwands-Aufträge erfasst wurden
    val weeksWithEntries = remember(allArbeitszeiten, aufwandAuftraege) {
        val aufwandIds = aufwandAuftraege.map { it.id }.toSet()
        val weeksMap = mutableMapOf<Pair<Int, Int>, MutableList<Betriebsauftrag>>()
        
        allArbeitszeiten.forEach { entry ->
            if (entry.auftragId in aufwandIds) {
                val cal = Calendar.getInstance(Locale.GERMANY).apply { timeInMillis = entry.datum }
                val jahr = cal.get(Calendar.YEAR)
                val kw = cal.get(Calendar.WEEK_OF_YEAR)
                
                // Jahreswechsel korrigieren (z.B. 1. Januar in KW 52 gehört zum Vorjahr)
                val correctedJahr = if (cal.get(Calendar.MONTH) == Calendar.DECEMBER && kw == 1) {
                    jahr + 1
                } else if (cal.get(Calendar.MONTH) == Calendar.JANUARY && kw >= 52) {
                    jahr - 1
                } else {
                    jahr
                }
                
                val weekKey = Pair(correctedJahr, kw)
                if (!weeksMap.containsKey(weekKey)) {
                    weeksMap[weekKey] = mutableListOf()
                }
                
                val auftrag = aufwandAuftraege.find { it.id == entry.auftragId }
                if (auftrag != null && !weeksMap[weekKey]!!.contains(auftrag)) {
                    weeksMap[weekKey]!!.add(auftrag)
                }
            }
        }
        
        // Sortieren nach Jahr absteigend, dann KW absteigend
        weeksMap.toList().sortedWith(
            compareByDescending<Pair<Pair<Int, Int>, List<Betriebsauftrag>>> { it.first.first }
                .thenByDescending { it.first.second }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kundenunterschriften") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { padding ->
        if (weeksWithEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine erfassbaren Berichte gefunden.",
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                weeksWithEntries.forEach { (weekKey, auftraegeInWeek) ->
                    val (jahr, kw) = weekKey
                    item {
                        Text(
                            text = "Kalenderwoche $kw / $jahr",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(auftraegeInWeek) { auftrag ->
                        val hasSignature = allSignatures.any { it.auftragId == auftrag.id && it.jahr == jahr && it.kw == kw }
                        UnterschriftListItem(
                            auftrag = auftrag,
                            hasSignature = hasSignature,
                            onClick = { onNavigateToSignature(auftrag.id, jahr, kw) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnterschriftListItem(
    auftrag: Betriebsauftrag,
    hasSignature: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (hasSignature) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (hasSignature) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${auftrag.auftragsNummer} | Pos: ${auftrag.positionsNummer}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = auftrag.titelKurz,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            if (hasSignature) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Unterschrieben",
                    tint = Color(0xFF4CAF50), // Grün
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Draw,
                    contentDescription = "Fehlt",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
