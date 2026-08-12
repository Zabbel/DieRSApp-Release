package com.zabbel.diersapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuftragDetailTopAppBar(
    title: String,
    onBack: () -> Unit,
    onTimeTracking: () -> Unit,
    onEdit: () -> Unit,
    onCreateOrder: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
            }
        },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menü öffnen")
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Zeiterfassung") },
                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onTimeTracking()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Bearbeiten") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onEdit()
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Bestellung") },
                    leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onCreateOrder()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}