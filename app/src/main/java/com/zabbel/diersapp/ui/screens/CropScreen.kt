package com.zabbel.diersapp.ui.screens

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage
import com.zabbel.diersapp.util.ImageUtils

data class Quadrilateral(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomLeft: Offset,
    val bottomRight: Offset
)

// Definiert die Richtung, in die der Anker von der Ecke wegspringt
enum class AnchorDirection {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageUri: Uri,
    onConfirm: (Uri) -> Unit,
    onCancel: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var corners by remember { mutableStateOf<Quadrilateral?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ausschnitt wählen") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Abbrechen")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        corners?.let { currentCorners ->
                            val croppedUri = ImageUtils.cropAndWarp(
                                context = context,
                                sourceUri = imageUri,
                                corners = currentCorners,
                                containerSize = containerSize
                            )
                            if (croppedUri != null) {
                                onConfirm(croppedUri)
                            }
                        }
                    }) {
                        Text("Fertig", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { coordinates ->
                    if (containerSize == IntSize.Zero) {
                        containerSize = coordinates.size
                        val w = coordinates.size.width.toFloat()
                        val h = coordinates.size.height.toFloat()

                        val rectW = w * 0.7f
                        val rectH = rectW * 1.41f
                        val l = (w - rectW) / 2
                        val t = (h - rectH) / 2

                        corners = Quadrilateral(
                            topLeft = Offset(l, t),
                            topRight = Offset(l + rectW, t),
                            bottomLeft = Offset(l, t + rectH),
                            bottomRight = Offset(l + rectW, t + rectH)
                        )
                    }
                }
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            corners?.let { currentCorners ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = Path().apply {
                        moveTo(currentCorners.topLeft.x, currentCorners.topLeft.y)
                        lineTo(currentCorners.topRight.x, currentCorners.topRight.y)
                        lineTo(currentCorners.bottomRight.x, currentCorners.bottomRight.y)
                        lineTo(currentCorners.bottomLeft.x, currentCorners.bottomLeft.y)
                        close()
                    }
                    drawPath(path, Color.Blue, style = Stroke(width = 2.dp.toPx()))
                }

                // Die Anker werden nun mit ihrer Richtung platziert
                CornerAnchor(
                    pos = currentCorners.topLeft,
                    direction = AnchorDirection.TOP_LEFT,
                    onChanged = { newPos -> corners = corners?.copy(topLeft = newPos) }
                )
                CornerAnchor(
                    pos = currentCorners.topRight,
                    direction = AnchorDirection.TOP_RIGHT,
                    onChanged = { newPos -> corners = corners?.copy(topRight = newPos) }
                )
                CornerAnchor(
                    pos = currentCorners.bottomLeft,
                    direction = AnchorDirection.BOTTOM_LEFT,
                    onChanged = { newPos -> corners = corners?.copy(bottomLeft = newPos) }
                )
                CornerAnchor(
                    pos = currentCorners.bottomRight,
                    direction = AnchorDirection.BOTTOM_RIGHT,
                    onChanged = { newPos -> corners = corners?.copy(bottomRight = newPos) }
                )
            }
        }
    }
}

@Composable
fun CornerAnchor(
    pos: Offset,
    direction: AnchorDirection,
    onChanged: (Offset) -> Unit
) {
    val currentPos by rememberUpdatedState(pos)

    Box(
        modifier = Modifier
            // 1. Die BOX (Touch-Bereich) liegt IMMER exakt auf der echten Ecke (currentPos)
            // Wir ziehen 40dp (Hälfte von 80) ab, damit currentPos genau im Zentrum der Box liegt.
            .offset { IntOffset(currentPos.x.toInt() - 40.dp.roundToPx(), currentPos.y.toInt() - 40.dp.roundToPx()) }
            .size(80.dp)
            .pointerInput(direction) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Wir geben die Bewegung direkt an die echte Ecke weiter
                    onChanged(currentPos + dragAmount)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Das Zentrum der Box (80dp) entspricht der tatsächlichen Ecke des Rahmens
            val center = Offset(size.width / 2, size.height / 2)

            // 2. Hier definieren wir den VISUELLEN Versatz (diagonal nach außen)
            // 60f ist ein guter Wert, damit der Punkt ca. 2cm vom Finger weg ist
            val dist = 60f

            val visualPoint = when (direction) {
                // Oben Links: X minus, Y minus (Diagonal weg von der Mitte)
                AnchorDirection.TOP_LEFT -> Offset(center.x - dist, center.y - dist)
                // Oben Rechts: X plus, Y minus
                AnchorDirection.TOP_RIGHT -> Offset(center.x + dist, center.y - dist)
                // Unten Links: X minus, Y plus
                AnchorDirection.BOTTOM_LEFT -> Offset(center.x - dist, center.y + dist)
                // Unten Rechts: X plus, Y plus (Dein Idealwert)
                AnchorDirection.BOTTOM_RIGHT -> Offset(center.x + dist, center.y + dist)
            }

            // 3. Zeichnen des Greifpunktes an der versetzten Position (visualPoint)

            // Schatten für Kontrast
            drawCircle(
                color = Color.Black.copy(alpha = 0.3f),
                radius = 15.dp.toPx(),
                center = visualPoint
            )

            // Weißer Ring
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = visualPoint
            )

            // Cyan Kern
            drawCircle(
                color = Color.Blue,
                radius = 7.dp.toPx(),
                center = visualPoint
            )
        }
    }
}