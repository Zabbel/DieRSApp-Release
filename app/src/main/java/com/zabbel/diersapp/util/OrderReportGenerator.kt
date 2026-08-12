package com.zabbel.diersapp.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.zabbel.diersapp.R
import com.zabbel.diersapp.data.model.Bestellung
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object OrderReportGenerator {

    private const val MM_TO_PT = 2.8346f // 1mm = 2.8346 points (72 DPI)

    fun erzeugeBestellungPdf(context: Context, bestellung: Bestellung): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }

        // --- HINTERGRUND LADEN ---
        val options = BitmapFactory.Options().apply { inMutable = true }
        val backgroundBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bestellung, options)
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, null, Rect(0, 0, 595, 842), paint)
            backgroundBitmap.recycle()
        }

        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        paint.textSize = 12f

        // --- 1. TYP ---
        val typY = 137f - (11.25f * MM_TO_PT)
        val typXBase = 44f + (3.25f * MM_TO_PT)
        if (bestellung.typ == Bestellung.BestellTyp.ANFORDERUNG) {
            canvas.drawText("X", typXBase, typY, paint) 
        } else {
            canvas.drawText("X", typXBase + (71.0f * MM_TO_PT), typY, paint)
        }

        // --- 2. KATEGORIE ---
        val katYBase = 196f - (15.25f * MM_TO_PT)
        val katXBase = 44f + (3.25f * MM_TO_PT)
        val fremdY = 172f - (15.0f * MM_TO_PT)
        
        when (bestellung.kategorie) {
            Bestellung.BestellKategorie.FREMDLEISTUNG -> canvas.drawText("X", katXBase, fremdY, paint)
            Bestellung.BestellKategorie.MATERIAL -> canvas.drawText("X", katXBase, katYBase, paint)
            Bestellung.BestellKategorie.WERKZEUG -> canvas.drawText("X", katXBase + (24f * MM_TO_PT), katYBase, paint)
            Bestellung.BestellKategorie.GEFAHRSTOFF -> canvas.drawText("X", katXBase + (24f + 28.5f) * MM_TO_PT, katYBase, paint)
        }

        // --- STAMMDATEN ---
        canvas.drawText(bestellung.name, 400f - (20f * MM_TO_PT), 196f - (13.5f * MM_TO_PT), paint)
        val datumY = 244f - (13f * MM_TO_PT)
        canvas.drawText(sdf.format(Date(bestellung.datum)), 400f - (20f * MM_TO_PT), datumY, paint)
        canvas.drawText(bestellung.projekt, 145f, datumY, paint)
        val nrY = 280f - (10f * MM_TO_PT)
        canvas.drawText(bestellung.projektNr, 120f + (1f * MM_TO_PT), nrY, paint)
        canvas.drawText(bestellung.auftragsNr, 400f - (8f * MM_TO_PT), nrY, paint)
        val posY = 316f - (12f * MM_TO_PT)
        canvas.drawText(bestellung.ihNr, 100f - (2f * MM_TO_PT), posY, paint)
        canvas.drawText(bestellung.posNr, 360f - (1.5f * MM_TO_PT), posY, paint)

        // --- ANFORDERUNGSTEXT (Intelligenter Zeilenfluss) ---
        paint.textSize = 10f
        val rawText = bestellung.anforderungstext
        val processedText = if (rawText.contains("\n")) {
            rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        } else {
            rawText
        }

        val words = processedText.split(" ").filter { it.isNotEmpty() }
        val displayLines = mutableListOf<String>()
        val continuationLines = mutableListOf<String>()
        
        val line1Width = 124f * MM_TO_PT
        val standardWidth = 176f * MM_TO_PT
        var wordIdx = 0

        // Zeilen 1 bis 4 berechnen
        for (i in 0 until 4) {
            val maxWidth = if (i == 0) line1Width else standardWidth
            var currentLine = ""
            while (wordIdx < words.size) {
                val word = words[wordIdx]
                val test = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(test) <= maxWidth) {
                    currentLine = test
                    wordIdx++
                } else break
            }
            displayLines.add(currentLine)
        }

        // Zeile 5 und Überlauf-Check
        val suffix = " ... siehe unten"
        val suffixWidth = paint.measureText(suffix)
        
        if (wordIdx < words.size) {
            val remainingWords = words.subList(wordIdx, words.size)
            val testLine5Full = remainingWords.joinToString(" ")
            
            if (paint.measureText(testLine5Full) <= standardWidth) {
                // Es passt alles in Zeile 5
                displayLines.add(testLine5Full)
            } else {
                // Überlauf! Zeile 5 mit Suffix füllen
                var line5Visible = ""
                val reducedWidth5 = standardWidth - suffixWidth
                while (wordIdx < words.size) {
                    val word = words[wordIdx]
                    val test = if (line5Visible.isEmpty()) word else "$line5Visible $word"
                    if (paint.measureText(test) <= reducedWidth5) {
                        line5Visible = test
                        wordIdx++
                    } else break
                }
                displayLines.add(line5Visible + suffix)
                
                // Der Rest kommt in den Übertrag
                val continuationText = words.subList(wordIdx, words.size).joinToString(" ")
                if (continuationText.isNotEmpty()) {
                    continuationLines.addAll(layoutText(continuationText, listOf(standardWidth), paint))
                }
            }
        }

        // ZEICHNEN DER ARTIKELZEILEN
        displayLines.getOrNull(0)?.let { canvas.drawText(it, 45f + (53f * MM_TO_PT), 385f - (25f * MM_TO_PT), paint) }
        displayLines.getOrNull(1)?.let { canvas.drawText(it, 45f, 385f - (20f * MM_TO_PT), paint) }
        displayLines.getOrNull(2)?.let { canvas.drawText(it, 45f, 385f - (15f * MM_TO_PT), paint) }
        displayLines.getOrNull(3)?.let { canvas.drawText(it, 45f, 385f - (10f * MM_TO_PT), paint) }
        displayLines.getOrNull(4)?.let { canvas.drawText(it, 45f, 385f - (5f * MM_TO_PT), paint) }

        // --- LIEFERANT ---
        canvas.drawText(bestellung.lieferantenVorschlag, 175f + (4f * MM_TO_PT), 570f - (58.75f * MM_TO_PT), paint)

        // --- PRIORITÄT ---
        val prioY = 638f - (73.9f * MM_TO_PT)
        val prioXBase = 44f + (2.5f * MM_TO_PT)
        paint.textSize = 14f
        when (bestellung.prioritaet) {
            Bestellung.BestellPrio.EILIG -> canvas.drawText("X", prioXBase, prioY, paint)
            Bestellung.BestellPrio.SOFORT -> {
                canvas.drawText("X", prioXBase + (19.875f * MM_TO_PT), prioY + (0.125f * MM_TO_PT), paint)
                paint.textSize = 12f
                canvas.drawText(bestellung.sofortBisDatum, prioXBase + (46.5f * MM_TO_PT), prioY + (0.375f * MM_TO_PT), paint)
            }
            Bestellung.BestellPrio.NORMAL -> canvas.drawText("X", prioXBase + (125.7f * MM_TO_PT), prioY - (0.2f * MM_TO_PT), paint)
        }

        // --- ÜBERTRAG (20mm unter Priorität) ---
        if (continuationLines.isNotEmpty()) {
            paint.textSize = 10f
            var continuationY = prioY + (20f * MM_TO_PT)
            for (line in continuationLines) {
                canvas.drawText(line, 45f, continuationY, paint)
                continuationY += paint.textSize * 1.4f
            }
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "Bestellung_${bestellung.auftragsNr}.pdf")
        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun layoutText(text: String, widths: List<Float>, paint: Paint): List<String> {
        val words = text.split(" ").filter { it.isNotEmpty() }
        val result = mutableListOf<String>()
        var currentLine = ""
        var lineIdx = 0

        for (word in words) {
            val maxWidth = if (lineIdx < widths.size) widths[lineIdx] else widths.last()
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                result.add(currentLine)
                currentLine = word
                lineIdx++
            }
        }
        if (currentLine.isNotEmpty()) result.add(currentLine)
        return result
    }
}
