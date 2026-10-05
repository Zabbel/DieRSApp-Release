package com.zabbel.diersapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zabbel.diersapp.pinscreen.PinLoginScreen
import com.zabbel.diersapp.pinscreen.PinSetupScreen
import com.zabbel.diersapp.ui.screens.AuftragDetailScreen
import com.zabbel.diersapp.ui.screens.AuftragEditScreen
import com.zabbel.diersapp.ui.screens.AuftragsuebersichtScreen
import com.zabbel.diersapp.ui.screens.BestellungScreen
import com.zabbel.diersapp.ui.screens.CameraCaptureScreen
import com.zabbel.diersapp.ui.screens.CropScreen
import com.zabbel.diersapp.ui.screens.MontageberichtScreen
import com.zabbel.diersapp.ui.screens.PdfViewerScreen
import com.zabbel.diersapp.ui.screens.SettingsScreen
import com.zabbel.diersapp.ui.screens.ArchivScreen
import com.zabbel.diersapp.ui.screens.StatistikScreen
import com.zabbel.diersapp.ui.screens.WochenberichtScreen
import com.zabbel.diersapp.ui.theme.DieRSAppTheme
import com.zabbel.diersapp.util.OCRService
import com.zabbel.diersapp.util.OrderParser
import com.zabbel.diersapp.viewmodel.AuftragViewModel
import com.zabbel.diersapp.viewmodel.NextScreen
import com.zabbel.diersapp.viewmodel.SplashViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.toRect

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val splashViewModel: SplashViewModel by viewModels()
    private val auftragViewModel: AuftragViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        clearCacheImages() // Sicherheitshalber beim Start aufräumen
        splashScreen.setKeepOnScreenCondition { false }
        enableEdgeToEdge()
        com.zabbel.diersapp.util.UpdateManager.checkForUpdates(this)
        setContent {
            DieRSAppTheme {
                val nextScreen by splashViewModel.nextScreenState.collectAsStateWithLifecycle()
                val appStateViewModel: com.zabbel.diersapp.viewmodel.AppStateViewModel by viewModels()
                AppContent(startScreen = nextScreen, auftragViewModel = auftragViewModel, appStateViewModel = appStateViewModel)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            val appStateViewModel: com.zabbel.diersapp.viewmodel.AppStateViewModel by viewModels()
            if (!appStateViewModel.isIntentionalBackground.value) {
                clearCacheImages()
                finishAffinity()
                exitProcess(0)
            } else {
                // Set the flag back to false for the next time
                appStateViewModel.setIntentionalBackground(false)
            }
        }
    }

    override fun onDestroy() {
        clearCacheImages()
        super.onDestroy()
    }

    private fun clearCacheImages() {
        try {
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.equals("jpg", ignoreCase = true)) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RS_Cleanup", "Fehler beim Löschen der Cache-Bilder", e)
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AppContent(startScreen: NextScreen, auftragViewModel: AuftragViewModel, appStateViewModel: com.zabbel.diersapp.viewmodel.AppStateViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val navController = rememberNavController()

    if (startScreen == NextScreen.LOADING) {
        val context = LocalContext.current
        val versionName = remember {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName
            } catch (_: Exception) {
                "1.0"
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // -----------------------------------------------------------------
            // NEU: Die Puls-Animation vorbereiten
            // -----------------------------------------------------------------
            val infiniteTransition = rememberInfiniteTransition(label = "NeonGlowTransition")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f, // Minimale Leuchtstärke (30% sichtbar)
                targetValue = 1.0f,  // Maximale Leuchtstärke (100% sichtbar)
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "GlowAlphaAnimation"
            )

            // -----------------------------------------------------------------
            // EBENE 1: Dein unbewegtes Hauptlogo im Hintergrund
            // Nutzt painterResource für sofortiges Laden ohne Flackern
            // -----------------------------------------------------------------
            Image(
                painter = painterResource(id = R.drawable.rslogo_old),
                contentDescription = "Statischer Logo Hintergrund",
                modifier = Modifier.size(280.dp),
                contentScale = ContentScale.Fit
            )

            // -----------------------------------------------------------------
            // EBENE 2: Dein neuer, glühender Blitz exakt darübergelegt
            // -----------------------------------------------------------------
            Image(
                painter = painterResource(id = R.drawable.rs_blitz_glow),
                contentDescription = "Animierter Neon Blitz",
                modifier = Modifier
                    .size(280.dp)
                    .graphicsLayer {
                        alpha = glowAlpha
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        val paint = Paint().apply {
                            blendMode = BlendMode.Screen
                        }
                        onDrawWithContent {
                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(size.toRect(), paint)
                                drawContent()
                                canvas.restore()
                            }
                        }
                    }
                    .blur(
                        radius = 12.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    ),
                contentScale = ContentScale.Fit
            )

            // -----------------------------------------------------------------
            // DEIN BESTEHENDER TEXT & UPDATE LOGIK
            // -----------------------------------------------------------------
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val updateState by com.zabbel.diersapp.util.UpdateManager.updateState.collectAsStateWithLifecycle()

                if (updateState is com.zabbel.diersapp.util.UpdateState.Downloading) {
                    val progress = (updateState as com.zabbel.diersapp.util.UpdateState.Downloading).progress
                    Text(
                        text = "Update wird heruntergeladen... ${(progress * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray
                    )
                } else {
                    Text(
                        text = "Version $versionName",
                        color = Color.Gray.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "©2025 EmGee Apps",
                        color = Color.Gray.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Update Dialog Overlay
            val updateStateForDialog by com.zabbel.diersapp.util.UpdateManager.updateState.collectAsStateWithLifecycle()
            if (updateStateForDialog is com.zabbel.diersapp.util.UpdateState.UpdateAvailable) {
                val info = updateStateForDialog as com.zabbel.diersapp.util.UpdateState.UpdateAvailable
                AlertDialog(
                    onDismissRequest = { com.zabbel.diersapp.util.UpdateManager.skipUpdate() },
                    title = { Text("Update verfügbar") },
                    text = { Text("Version ${info.versionName} ist verfügbar.\n\nÄnderungen:\n${info.releaseNotes}") },
                    confirmButton = {
                        Button(onClick = { com.zabbel.diersapp.util.UpdateManager.startDownload(context, info.apkUrl) }) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { com.zabbel.diersapp.util.UpdateManager.skipUpdate() }) {
                            Text("Später")
                        }
                    }
                )
            }
        }
    } else {
        Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
            NavHost(
                navController = navController,
                startDestination = when (startScreen) {
                    NextScreen.PIN_SETUP -> "pin_setup"
                    NextScreen.PIN_LOGIN -> "pin_login"
                    NextScreen.AUFTRAGSUEBERSICHT -> "auftragsuebersicht"
                    else -> "pin_login"
                },
                modifier = Modifier.fillMaxSize() // KEIN Padding mehr für nahtlosen Übergang!
            ) {
                composable("pin_setup") {
                    PinSetupScreen(isReadyToAnimate = true, onPinSetupComplete = {
                        navController.navigate("user_settings") { popUpTo("pin_setup") { inclusive = true } }
                    })
                }
                composable("pin_login") {
                    PinLoginScreen(isReadyToAnimate = true, onPinCorrect = {
                        navController.navigate("auftragsuebersicht") { popUpTo("pin_login") { inclusive = true } }
                    }, onPinIncorrect = {}, onNavigateToSetup = {
                        navController.navigate("pin_setup") { popUpTo("pin_login") { inclusive = true } }
                    })
                }
                composable("auftragsuebersicht") {
                    val context = LocalContext.current
                    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                        if (isGranted) navController.navigate("camera_capture")
                    }
                    AuftragsuebersichtScreen(
                        viewModel = auftragViewModel,
                        onScanClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                navController.navigate("camera_capture")
                            } else permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onAuftragClick = { id -> navController.navigate("auftrag_detail/$id") },
                        onGenerateWeeklyReport = { navController.navigate("weekly_report") },
                        onMontagebericht = { navController.navigate("montagebericht") },
                        onNavigateToStatistik = { navController.navigate("statistik") },
                        onNavigateToArchiv = { navController.navigate("archiv") },
                        onNavigateToSettings = { navController.navigate("user_settings") }
                    )
                }
                composable("camera_capture") {
                    CameraCaptureScreen(onCaptureFinished = { uris ->
                        auftragViewModel.clearCapturedImages()
                        uris.forEach { auftragViewModel.addCapturedImage(it) }
                        navController.navigate("crop_screen")
                    }, onCancel = { navController.popBackStack() })
                }
                composable("crop_screen") {
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current
                    val uris = auftragViewModel.capturedImageUris
                    val currentIndex = auftragViewModel.currentCropIndex
                    
                    var showValidationError by remember { mutableStateOf<String?>(null) }
                    var duplicateAuftrag by remember { mutableStateOf<com.zabbel.diersapp.data.model.Betriebsauftrag?>(null) }

                    if (showValidationError != null) {
                        AlertDialog(
                            onDismissRequest = { showValidationError = null; auftragViewModel.clearCapturedImages(); navController.navigate("auftragsuebersicht") { popUpTo("crop_screen") { inclusive = true } } },
                            title = { Text("Fehler beim Scan") },
                            text = { Text(showValidationError ?: "") },
                            confirmButton = { Button(onClick = { showValidationError = null; auftragViewModel.clearCapturedImages(); navController.navigate("auftragsuebersicht") { popUpTo("crop_screen") { inclusive = true } } }) { Text("OK") } }
                        )
                    }

                    if (duplicateAuftrag != null) {
                        AlertDialog(
                            onDismissRequest = { duplicateAuftrag = null },
                            title = { Text("Auftrag existiert bereits") },
                            text = { Text("Der Auftrag ${duplicateAuftrag?.auftragsNummer} (Pos. ${duplicateAuftrag?.positionsNummer}) ist schon im System hinterlegt.\n\nMöchtest du zum bestehenden Auftrag springen oder den Scan verwerfen?") },
                            confirmButton = {
                                Button(onClick = {
                                    val id = duplicateAuftrag?.id
                                    duplicateAuftrag = null
                                    auftragViewModel.clearCapturedImages()
                                    navController.navigate("auftrag_detail/$id") {
                                        popUpTo("crop_screen") { inclusive = true }
                                    }
                                }) { Text("Zum Auftrag") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    duplicateAuftrag = null
                                    auftragViewModel.clearCapturedImages()
                                    navController.navigate("auftragsuebersicht") {
                                        popUpTo("crop_screen") { inclusive = true }
                                    }
                                }) { Text("Abbrechen / Verwerfen") }
                            }
                        )
                    }

                    if (currentIndex < uris.size) {
                        CropScreen(imageUri = uris[currentIndex], onConfirm = { croppedUri ->
                            auftragViewModel.croppedImageUris.add(croppedUri)
                            if (auftragViewModel.currentCropIndex + 1 < uris.size) {
                                auftragViewModel.currentCropIndex++
                            } else {
                                // Alle Seiten zugeschnitten → OCR & MERGE starten
                                scope.launch {
                                    val pages = auftragViewModel.croppedImageUris
                                    var baseAuftrag: com.zabbel.diersapp.data.model.Betriebsauftrag? = null
                                    var firstPageInfo: OrderParser.ValidationInfo? = null
                                    val combinedDescription = StringBuilder()

                                    for (i in pages.indices) {
                                        val text = OCRService.processImageAndCleanup(context, pages[i])
                                                ?: continue
                                        
                                        // Logge den Text jeder Seite
                                        android.util.Log.d("RS_OCR", "Verarbeite Seite ${i + 1}:\n$text")

                                        val currentPageInfo = OrderParser.getValidationInfo(text)

                                        if (firstPageInfo == null) {
                                            // Das ist unsere erste erfolgreich verarbeitete Seite → Stammdatenquelle
                                            baseAuftrag = OrderParser.parseRecognizedText(text)
                                            firstPageInfo = currentPageInfo
                                            combinedDescription.append(baseAuftrag.beschreibungLang)
                                        } else {
                                            // Folgeseiten: Gegen die erste Seite validieren & Leistung anhängen

                                            // Validierung: Auftrag oder Projekt muss übereinstimmen
                                            val isSameOrder = firstPageInfo.auftragsNummer.isNotBlank() && 
                                                             currentPageInfo.auftragsNummer == firstPageInfo.auftragsNummer
                                            
                                            val isSameProject = firstPageInfo.projektNr.isNotBlank() && 
                                                               currentPageInfo.projektNr == firstPageInfo.projektNr
                                            
                                            // Wenn eine Seite gar keine Nummern hat, nehmen wir sie im Zweifel mit (Heuristik für schlechten Scan)
                                            val hasAnyNumbers = currentPageInfo.auftragsNummer.isNotBlank() || currentPageInfo.projektNr.isNotBlank()

                                            if (hasAnyNumbers && !isSameOrder && !isSameProject) {
                                                showValidationError = "Seite ${i + 1} scheint nicht zum selben Auftrag zu gehören.\n\n" +
                                                        "Erwartet: ${firstPageInfo.auftragsNummer.ifBlank { firstPageInfo.projektNr }}\n" +
                                                        "Gefunden: ${currentPageInfo.auftragsNummer.ifBlank { currentPageInfo.projektNr.ifBlank { "Nichts" } }}"
                                                return@launch
                                            }

                                            OrderParser.extractLeistung(text, isFolgeSeite = true)?.let { extraText ->
                                                if (extraText.isNotBlank()) {
                                                    combinedDescription.append("\n\n").append(extraText)
                                                }
                                            }
                                        }
                                    }

                                    if (baseAuftrag != null) {
                                        // Dubletten-Check
                                        val existing = auftragViewModel.getExistingAuftrag(
                                            baseAuftrag.auftragsNummer,
                                            baseAuftrag.positionsNummer
                                        )

                                        if (existing != null) {
                                            duplicateAuftrag = existing
                                        } else {
                                            auftragViewModel.pendingAuftrag = baseAuftrag.copy(
                                                beschreibungLang = combinedDescription.toString().trim()
                                            )
                                            auftragViewModel.clearCapturedImages()
                                            navController.navigate("auftrag_edit") { popUpTo("crop_screen") { inclusive = true } }
                                        }
                                    } else {
                                        auftragViewModel.clearCapturedImages()
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }, onCancel = { auftragViewModel.clearCapturedImages(); navController.popBackStack() })
                    }
                }
                composable("auftrag_edit") {
                    auftragViewModel.pendingAuftrag?.let { auftrag ->
                        AuftragEditScreen(initialAuftrag = auftrag, onSave = { korrigierterAuftrag ->
                            auftragViewModel.saveExtractedAuftrag(korrigierterAuftrag)
                            auftragViewModel.clearPendingAuftrag()
                            navController.navigate("auftragsuebersicht") { popUpTo("auftrag_edit") { inclusive = true } }
                        }, onCancel = { auftragViewModel.clearPendingAuftrag(); navController.popBackStack() })
                    }
                }
                composable("auftrag_detail/{auftragId}", arguments = listOf(navArgument("auftragId") { type = NavType.LongType })) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("auftragId") ?: 0L
                    auftragViewModel.auftraege.collectAsStateWithLifecycle().value.find { it.id == id }?.let { auftrag ->
                        AuftragDetailScreen(
                            auftrag = auftrag, 
                            onBack = { navController.popBackStack() }, 
                            onEdit = {
                                auftragViewModel.pendingAuftrag = auftrag
                                navController.navigate("auftrag_edit")
                            }, 
                            onBestellung = {
                                navController.navigate("bestellung/${auftrag.id}")
                            },
                            viewModel = auftragViewModel
                        )
                    }
                }
                composable("weekly_report") { WochenberichtScreen(viewModel = auftragViewModel, navController = navController, onBack = { navController.popBackStack() }) }
                composable("statistik") { StatistikScreen(viewModel = auftragViewModel, onBack = { navController.popBackStack() }) }
                composable("montagebericht") {
                    MontageberichtScreen(
                        viewModel = auftragViewModel,
                        navController = navController,
                        onBack = { navController.popBackStack() })
                }
                composable(
                    "pdf_viewer/{uri}",
                    arguments = listOf(navArgument("uri") { type = NavType.StringType })
                ) { backStackEntry ->
                    val uri = backStackEntry.arguments?.getString("uri") ?: ""
                    PdfViewerScreen(
                        uriString = uri,
                        onBack = { navController.popBackStack() },
                        onShareReady = { appStateViewModel.setIntentionalBackground(true) }
                    )
                }
                composable("archiv") {
                    ArchivScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPdf = { encodedUri -> navController.navigate("pdf_viewer/$encodedUri") }
                    )
                }
                composable("bestellung/{auftragId}", arguments = listOf(navArgument("auftragId") { type = NavType.LongType })) { backStackEntry ->
                    val id = backStackEntry.arguments?.getLong("auftragId") ?: 0L
                    auftragViewModel.allAuftraege.collectAsStateWithLifecycle().value.find { it.id == id }?.let { auftrag ->
                        BestellungScreen(auftrag = auftrag, viewModel = auftragViewModel, onBack = { navController.popBackStack() })
                    }
                }
                composable("user_settings") {
                    SettingsScreen(
                        viewModel = auftragViewModel,
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate("auftragsuebersicht") {
                                    popUpTo("user_settings") { inclusive = true }
                                }
                            }
                        },
                        onNavigateToPinChange = { navController.navigate("pin_change") }
                    )
                }
                composable("pin_change") {
                    PinSetupScreen(isReadyToAnimate = true, onPinSetupComplete = {
                        navController.popBackStack()
                    })
                }
            }
        }
    }
}
