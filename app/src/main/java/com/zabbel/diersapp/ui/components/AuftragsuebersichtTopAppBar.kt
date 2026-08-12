package com.zabbel.diersapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
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