package com.zabbel.diersapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalHotel
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zabbel.diersapp.data.model.StatistikDaten
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatistikScreen(
    viewModel: AuftragViewModel,
    onBack: () -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val stats by viewModel.getYearlyStats(currentYear).collectAsStateWithLifecycle(initialValue = StatistikDaten(0, 0, currentYear))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistik $currentYear") },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatistikCard(
                title = "Krankheitstage",
                count = stats.krankheitstage,
                icon = Icons.Default.Sick,
                color = MaterialTheme.colorScheme.error
            )

            StatistikCard(
                title = "Urlaubstage",
                count = stats.urlaubstage,
                icon = Icons.Default.LocalHotel,
                color = MaterialTheme.colorScheme.tertiary
            )
            
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Hinweis: Es werden nur vollständige Tage gezählt, an denen entsprechende Buchungen vorgenommen wurden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun StatistikCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$count Tage",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = color.copy(alpha = 0.8f)
            )
        }
    }
}
