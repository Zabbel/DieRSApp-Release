package com.zabbel.diersapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.zabbel.diersapp.ui.screens.Quadrilateral
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.createBitmap

object ImageUtils {

    fun cropAndWarp(
        context: Context,
        sourceUri: Uri,
        corners: Quadrilateral,
        containerSize: IntSize
    ): Uri? {
        return try {
            // 1. Originalbild laden
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            val imgW = originalBitmap.width.toFloat()
            val imgH = originalBitmap.height.toFloat()
            val viewW = containerSize.width.toFloat()
            val viewH = containerSize.height.toFloat()

            // 2. Skalierung berechnen (ContentScale.Fit Logik)
            val scale = minOf(viewW / imgW, viewH / imgH)
            val xOffset = (viewW - imgW * scale) / 2f
            val yOffset = (viewH - imgH * scale) / 2f

            // Hilfsfunktion zur Umrechnung: View-Pixel → Bitmap-Pixel
            fun toBitmapPx(offset: Offset): FloatArray {
                return floatArrayOf(
                    (offset.x - xOffset) / scale,
                    (offset.y - yOffset) / scale
                )
            }

            // Quell-Punkte auf der echten Bitmap
            val srcPoints = floatArrayOf(
                toBitmapPx(corners.topLeft)[0], toBitmapPx(corners.topLeft)[1],
                toBitmapPx(corners.topRight)[0], toBitmapPx(corners.topRight)[1],
                toBitmapPx(corners.bottomRight)[0], toBitmapPx(corners.bottomRight)[1],
                toBitmapPx(corners.bottomLeft)[0], toBitmapPx(corners.bottomLeft)[1]
            )

            // 3. Ziel-Größe festlegen (Dynamisch basierend auf der Auswahl)
            // Wir berechnen die Breite/Höhe des gewählten Bereichs für ein scharfes Ergebnis
            val targetW = 1240f // A4 Breite bei ca. 150dpi
            val targetH = 1754f // A4 Höhe

            val dstPoints = floatArrayOf(
                0f, 0f,
                targetW, 0f,
                targetW, targetH,
                0f, targetH
            )

            // 4. Matrix-Transformation (Warp Perspective)
            val matrix = Matrix()
            // setPolyToPoly ist das Android-Äquivalent zu OpenCV's getPerspectiveTransform
            matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

            // 5. Neues Bild erstellen und transformieren
            val outputBitmap = createBitmap(targetW.toInt(), targetH.toInt())
            val canvas = Canvas(outputBitmap)
            canvas.drawBitmap(originalBitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

            // 6. Speichern der neuen Datei
            val croppedFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
            FileOutputStream(croppedFile).use { out ->
                outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Cleanup
            originalBitmap.recycle()
            outputBitmap.recycle()

            Uri.fromFile(croppedFile)
        } catch (e: Exception) {
            Log.e("ImageUtils", "Fehler beim Zuschneiden: ${e.message}")
            null
        }
    }
}