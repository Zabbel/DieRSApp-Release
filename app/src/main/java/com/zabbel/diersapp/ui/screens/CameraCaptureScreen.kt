package com.zabbel.diersapp.ui.screens

import android.content.Context
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

@Composable
fun CameraCaptureScreen(
    onCaptureFinished: (List<Uri>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var capturedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val actionSound = remember {
        MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) }
    }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val mainExecutor = ContextCompat.getMainExecutor(context)

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Kamera-Vorschau
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { previewView ->
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    try {
                        cameraProvider.unbindAll()
                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        camera = boundCamera
                        camera?.cameraControl?.enableTorch(isTorchOn)
                    } catch (e: Exception) {
                        Log.e("Camera", "Binding failed", e)
                    }
                }, mainExecutor)
            },
            modifier = Modifier.fillMaxSize()
        )

        DocumentScannerOverlay()

        // Abbrechen Button (links)
        Button(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f))
        ) {
            Text("Abbrechen", color = Color.White)
        }

        // Licht Button (rechts)
        Button(
            onClick = {
                isTorchOn = !isTorchOn
                camera?.cameraControl?.enableTorch(isTorchOn)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f))
        ) {
            Text(if (isTorchOn) "Licht Aus" else "Licht Ein", color = Color.White)
        }

        // Fertig Button (erscheint nach dem ersten Foto)
        if (capturedUris.isNotEmpty()) {
            Button(
                onClick = { onCaptureFinished(capturedUris) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 68.dp, end = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("Fertig (${capturedUris.size})", color = Color.White)
            }
        }

        // Auslöser Button
        Button(
            onClick = {
                takePhoto(context, imageCapture, mainExecutor, actionSound) { uri ->
                    capturedUris = capturedUris + uri
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
                .size(80.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(65.dp)
                    .background(color = Color.Red, shape = CircleShape)
            )
        }
        
        Text(
            text = if (capturedUris.isEmpty()) "Seite 1 im Rahmen ausrichten" else "Nächste Seite ausrichten",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DocumentScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Berechne ein A4-Verhältnis (ca. 1:1.41)
        val rectWidth = width * 0.85f
        val rectHeight = rectWidth * 1.41f

        val left = (width - rectWidth) / 2
        val top = (height - rectHeight) / 2
        val rect = Rect(Offset(left, top), Size(rectWidth, rectHeight))

        // 1. Den äußeren Bereich abdunkeln
        val path = Path().apply {
            addRect(Rect(Offset.Zero, size))
            addRoundRect(RoundRect(rect, CornerRadius(12.dp.toPx())))
        }

        // Wir nutzen clipPath um das "Loch" in der Mitte auszuschneiden
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(color = Color.Black.copy(alpha = 0.6f))
        }

        // 2. Den Rahmen zeichnen (Weiße Ecken)
        val lineLength = 40.dp.toPx()
        val strokeWidth = 3.dp.toPx()
        val color = Color.White

        // Oben Links
        drawLine(color, Offset(rect.left, rect.top), Offset(rect.left + lineLength, rect.top), strokeWidth)
        drawLine(color, Offset(rect.left, rect.top), Offset(rect.left, rect.top + lineLength), strokeWidth)

        // Oben Rechts
        drawLine(color, Offset(rect.right, rect.top), Offset(rect.right - lineLength, rect.top), strokeWidth)
        drawLine(color, Offset(rect.right, rect.top), Offset(rect.right, rect.top + lineLength), strokeWidth)

        // Unten Links
        drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.left + lineLength, rect.bottom), strokeWidth)
        drawLine(color, Offset(rect.left, rect.bottom), Offset(rect.left, rect.bottom - lineLength), strokeWidth)

        // Unten Rechts
        drawLine(color, Offset(rect.right, rect.bottom), Offset(rect.right - lineLength, rect.bottom), strokeWidth)
        drawLine(color, Offset(rect.right, rect.bottom), Offset(rect.right, rect.bottom - lineLength), strokeWidth)
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    actionSound: MediaActionSound,
    onPhotoCaptured: (Uri) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.GERMANY)
        .format(System.currentTimeMillis())
    val photoFile = File(context.cacheDir, "$name.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // 1. SOUND: Sofort abspielen
                actionSound.play(MediaActionSound.SHUTTER_CLICK)

                // 2. VIBRATION: Separater Call
                triggerVibration(context)

                val savedUri = Uri.fromFile(photoFile)
                onPhotoCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("Camera", "Fehler: ${exception.message}")
            }
        }
    )
}

private fun triggerVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Prüfen, ob das Gerät überhaupt vibrieren kann
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(
            VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}