package com.zabbel.diersapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object OCRService {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val BLACKLIST = listOf(
        "Zeit:", "Datum /", "Unterschrift", "Dalum", "Unterschrit",
        "Unterschrif"
    )

    suspend fun processImageAndCleanup(context: Context, imageUri: Uri): String? =
        withContext(Dispatchers.Default) {
            var processedUri: Uri? = null

            try {
                val originalBitmap = loadBitmap(context, imageUri) ?: run {
                    Log.e("RS_OCR", "Bitmap konnte nicht geladen werden")
                    return@withContext null
                }

                val processedBitmap = preprocessBitmap(originalBitmap)
                processedUri = saveTempBitmap(context, processedBitmap)

                originalBitmap.recycle()
                processedBitmap.recycle()

                val image = InputImage.fromFilePath(context, processedUri)
                val visionText = recognizer.process(image).await()

                structureText(visionText)
            } catch (e: Exception) {
                Log.e("RS_OCR", "Fehler bei OCR", e)
                null
            } finally {
                cleanup(imageUri)
                processedUri?.let { cleanup(it) }
            }
        }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.setTargetSampleSize(1)
                }
            } else {
                @Suppress("DEPRECATION")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e("RS_OCR", "Fehler beim Laden des Bitmaps", e)
            null
        }
    }

    private fun saveTempBitmap(context: Context, bitmap: Bitmap): Uri {
        val file = File(context.cacheDir, "processed_ocr_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return Uri.fromFile(file)
    }

    /**
     * Optimiert das Bild für OCR bei Bildschirm-/Monitorfotos:
     * - Graustufen
     * - leichter Kontrastboost
     * - leichtes Nachschärfen über Zeichnung auf neues Bitmap
     */
    private fun preprocessBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()
        cm.setSaturation(0f)

        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(ColorMatrix(contrastMatrix))

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)

        return bitmap
    }

    private fun structureText(visionText: com.google.mlkit.vision.text.Text): String {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return ""

        val sortedElements = allLines.sortedBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()

        if (sortedElements.isNotEmpty()) {
            var currentRow = mutableListOf(sortedElements[0])
            rows.add(currentRow)

            for (i in 1 until sortedElements.size) {
                val prev = currentRow.last()
                val current = sortedElements[i]

                val prevBox = prev.boundingBox
                val currBox = current.boundingBox

                if (prevBox != null && currBox != null) {
                    val overlap = maxOf(
                        0,
                        minOf(prevBox.bottom, currBox.bottom) - maxOf(prevBox.top, currBox.top)
                    )
                    val minHeight = minOf(prevBox.height(), currBox.height())

                    if (overlap > minHeight * 0.4f) {
                        currentRow.add(current)
                    } else {
                        currentRow = mutableListOf(current)
                        rows.add(currentRow)
                    }
                } else {
                    currentRow = mutableListOf(current)
                    rows.add(currentRow)
                }
            }
        }

        return rows.mapNotNull { row ->
            val sortedRow = row.sortedBy { it.boundingBox?.left ?: 0 }
            val sb = StringBuilder()

            for (i in sortedRow.indices) {
                val current = sortedRow[i]
                val next = sortedRow.getOrNull(i + 1)

                sb.append(current.text)

                if (next != null) {
                    val gap = (next.boundingBox?.left ?: 0) - (current.boundingBox?.right ?: 0)
                    if (gap > 50) sb.append(" | ") else sb.append(" ")
                }
            }

            val lineText = sb.toString().trim()
            if (shouldKeepLine(lineText)) lineText else null
        }.joinToString("\n")
    }

    private fun shouldKeepLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return false

        if (
            trimmed.contains("Projekt", true) ||
            trimmed.contains("Auftrag", true) ||
            trimmed.contains("Kunde", true) ||
            trimmed.contains("Meldungsnummer", true) ||
            trimmed.contains(Regex("""2[4-9]\d{8}"""))
        ) return true

        val isBlacklisted = BLACKLIST.any { forbidden ->
            val pattern = Regex("""\b${Regex.escape(forbidden)}\b""", RegexOption.IGNORE_CASE)
            trimmed.contains(pattern)
        }

        if (isBlacklisted) {
            val cleaned = trimmed.replace(Regex("""[:|]"""), "").trim()
            if (BLACKLIST.any { it.equals(cleaned, ignoreCase = true) }) return false
        }

        return true
    }

    private fun cleanup(uri: Uri) {
        try {
            val path = uri.path ?: return
            val file = File(path)
            if (file.exists()) {
                val deleted = file.delete()
                Log.d("RS_OCR", "Cleanup ${if (deleted) "ok" else "fehlgeschlagen"}: $path")
            }
        } catch (e: Exception) {
            Log.e("RS_OCR", "Cleanup failed", e)
        }
    }
}