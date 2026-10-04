package com.zabbel.diersapp.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    uriString: String,
    onBack: () -> Unit,
    onShareReady: () -> Unit
) {
    val context = LocalContext.current
    val uri = Uri.parse(Uri.decode(uriString))
    
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    
    // Zoom & Pan States
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                if (fd != null) {
                    fileDescriptor = fd
                    val renderer = PdfRenderer(fd)
                    pdfRenderer = renderer
                    pageCount = renderer.pageCount
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Ansicht") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onShareReady() // Bypass aktivieren
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "PDF teilen"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Teilen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        },
        containerColor = Color.DarkGray
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val maxOffset = (scale - 1) * 1000f // Grobe Begrenzung
                        offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffset, maxOffset)
                        offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffset, maxOffset)
                        
                        // Wenn komplett rausgezoomt, Zentrierung erzwingen
                        if (scale == 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (pageCount == 0) {
                CircularProgressIndicator(color = Color.White)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(pageCount) { index ->
                        PdfPageImage(pdfRenderer = pdfRenderer, pageIndex = index)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageImage(pdfRenderer: PdfRenderer?, pageIndex: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(pdfRenderer, pageIndex) {
        pdfRenderer?.let { renderer ->
            withContext(Dispatchers.IO) {
                try {
                    val page = renderer.openPage(pageIndex)
                    // Hohe Auflösung für gutes Zooming (Skalierungsfaktor 2x)
                    val width = context.resources.displayMetrics.densityDpi / 72 * page.width * 2
                    val height = context.resources.displayMetrics.densityDpi / 72 * page.height * 2
                    
                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Weißen Hintergrund erzwingen, da PDFs transparent sein können
                    b.eraseColor(android.graphics.Color.WHITE)
                    
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap = b
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF Seite ${pageIndex + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f) // A4 Ratio fallback
                .background(Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
