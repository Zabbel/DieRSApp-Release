package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.ui.components.AuftragsuebersichtTopAppBar
import com.zabbel.diersapp.viewmodel.AuftragViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuftragsuebersichtScreen(
    viewModel: AuftragViewModel,
    onScanClick: () -> Unit,
    onAuftragClick: (Long) -> Unit,
    onGenerateWeeklyReport: () -> Unit,
    onMontagebericht: () -> Unit,
    onNavigateToStatistik: () -> Unit,
    onNavigateToArchiv: () -> Unit,
    onNavigateToKundenUnterschriften: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val auftraege by viewModel.auftraege.collectAsStateWithLifecycle()
    var auftragZumLoeschen by remember { mutableStateOf<Betriebsauftrag?>(null) }

    // Sicherheitsabfrage Dialog
    if (auftragZumLoeschen != null) {
        AlertDialog(
            onDismissRequest = { auftragZumLoeschen = null },
            title = { Text("Auftrag löschen?") },
            text = { Text("Möchtest du den Auftrag ${auftragZumLoeschen?.auftragsNummer} wirklich unwiderruflich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    auftragZumLoeschen?.let { viewModel.deleteAuftrag(it) }
                    auftragZumLoeschen = null
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { auftragZumLoeschen = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AuftragsuebersichtTopAppBar(
                onGenerateWeeklyReport = onGenerateWeeklyReport,
                onMontagebericht = onMontagebericht,
                onNavigateToStatistik = onNavigateToStatistik,
                onNavigateToArchiv = onNavigateToArchiv,
                onNavigateToKundenUnterschriften = onNavigateToKundenUnterschriften,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScanClick,
                containerColor = MaterialTheme.colorScheme.secondary, // Jetzt ein schönes Grau
                contentColor = Color.White
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Auftrag scannen")
            }
        },
        containerColor = MaterialTheme.colorScheme.background // Hintergrundfarbe erzwingen
    ) { padding ->
        if (auftraege.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine Aufträge vorhanden.\nKlicke auf die Kamera zum Scannen.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                items(
                    items = auftraege,
                    key = { it.id }
                ) { auftrag ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                auftragZumLoeschen = auftrag
                                false
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .background(color, CardDefaults.shape),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Löschen",
                                    modifier = Modifier.padding(end = 16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    ) {
                        AuftragItem(auftrag = auftrag) {
                            onAuftragClick(auftrag.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuftragItem(auftrag: Betriebsauftrag, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Auftrag: ${auftrag.auftragsNummer} | Pos: ${auftrag.positionsNummer}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary // Bessere Sichtbarkeit
            )
            Text(
                text = auftrag.titelKurz,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "Kunde: ${auftrag.kunde}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
