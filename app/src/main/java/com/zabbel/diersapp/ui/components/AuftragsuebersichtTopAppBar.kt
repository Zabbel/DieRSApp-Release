package com.zabbel.diersapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuftragsuebersichtTopAppBar(
    onGenerateWeeklyReport: () -> Unit,
    onMontagebericht: () -> Unit,
    onNavigateToStatistik: () -> Unit,
    onNavigateToArchiv: () -> Unit,
    onNavigateToKundenUnterschriften: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("Auftragsübersicht RS") },
        actions = {
            // Die Box ist wichtig, damit das Menü weiß, wo es aufpoppen soll (Anker)
            Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menü öffnen"
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Wochenbericht") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onGenerateWeeklyReport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Montagebericht") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onMontagebericht()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Statistik") },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onNavigateToStatistik()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Archiv") },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onNavigateToArchiv()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Kundenunterschriften") },
                        leadingIcon = {
                            Icon(Icons.Default.Draw, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onNavigateToKundenUnterschriften()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Einstellungen") },
                        leadingIcon = { Icon(Icons.Default.Settings, null) },
                        onClick = {
                            showMenu = false
                            onNavigateToSettings()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        )
    )
}