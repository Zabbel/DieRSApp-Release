package com.zabbel.diersapp.ui.components

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import androidx.core.graphics.createBitmap

@Composable
fun SignaturePad(
    initialSignatureBase64: String? = null,
    isLocked: Boolean = false,
    currentPaths: List<Path>,
    onPathsChanged: (List<Path>) -> Unit,
    onClear: () -> Unit,
) {
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var redrawTrigger by remember { mutableLongStateOf(0L) }
    
    val latestPaths by rememberUpdatedState(currentPaths)
    val latestOnPathsChanged by rememberUpdatedState(onPathsChanged)

    val initialBitmap = remember(initialSignatureBase64) {
        initialSignatureBase64?.let { base64ToBitmap(it) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Unterschrift", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color.White, shape = MaterialTheme.shapes.medium)
                .graphicsLayer(clip = true)
                .then(
                    if (!isLocked) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPath = Path().apply { moveTo(offset.x, offset.y) }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    redrawTrigger++
                                },
                                onDragEnd = {
                                    currentPath?.let { path ->
                                        latestOnPathsChanged(latestPaths + path)
                                    }
                                    currentPath = null
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                redrawTrigger.let { }
                
                // Vorschau anzeigen
                if ((currentPaths.isEmpty() && currentPath == null) && initialBitmap != null) {
                    val bitmapWidth = initialBitmap.width.toFloat()
                    val bitmapHeight = initialBitmap.height.toFloat()
                    
                    // Berechne Skalierung, um das Bild einzupassen (max 75% der Fläche)
                    val scale = (size.width / bitmapWidth).coerceAtMost(size.height / bitmapHeight) * 0.75f
                    val dstWidth = (bitmapWidth * scale).toInt()
                    val dstHeight = (bitmapHeight * scale).toInt()
                    val offsetX = ((size.width - dstWidth) / 2).toInt()
                    val offsetY = ((size.height - dstHeight) / 2).toInt()

                    drawImage(
                        image = initialBitmap.asImageBitmap(),
                        dstOffset = IntOffset(offsetX, offsetY),
                        dstSize = IntSize(dstWidth, dstHeight)
                    )
                }
                
                val stroke = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                
                currentPaths.forEach { path ->
                    drawPath(path, Color.Black, style = stroke)
                }
                currentPath?.let { path ->
                    drawPath(path, Color.Black, style = stroke)
                }
            }
            
            if (!isLocked && currentPaths.isNotEmpty()) {
                IconButton(
                    onClick = { onClear() },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Löschen", tint = Color.Gray)
                }
            }
        }
    }
}

fun createBitmapFromPaths(paths: List<Path>, width: Int, height: Int): Bitmap {
    // Falls Breite/Höhe 0 sind (dynamisch), berechnen wir die benötigte Größe
    val androidPaths = paths.map { it.asAndroidPath() }
    val bounds = android.graphics.RectF()
    var maxX = width.toFloat()
    var maxY = height.toFloat()
    
    androidPaths.forEach { path ->
        path.computeBounds(bounds, true)
        maxX = maxOf(maxX, bounds.right)
        maxY = maxOf(maxY, bounds.bottom)
    }

    // Puffer hinzufügen und abrunden
    val finalWidth = (maxX + 20f).toInt().coerceAtLeast(100)
    val finalHeight = (maxY + 20f).toInt().coerceAtLeast(100)

    val bitmap = createBitmap(finalWidth, finalHeight)
    val canvas = android.graphics.Canvas(bitmap)
    // Transparenter Hintergrund (drawColor WHITE entfernt)
    
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    
    paths.forEach { path ->
        canvas.drawPath(path.asAndroidPath(), paint)
    }
    
    return bitmap
}

fun bitmapToBase64(bitmap: Bitmap): String {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT).replace("\n", "")
}

fun base64ToBitmap(base64: String): Bitmap? {
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.e("RS_SignaturePad", "Error decoding base64 signature", e)
        null
    }
}
