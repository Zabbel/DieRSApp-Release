package com.zabbel.diersapp.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.zabbel.diersapp.R
import com.zabbel.diersapp.data.model.Arbeitszeit
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.ui.screens.calculateDecimalHoursFromEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.graphics.withRotation

object MontageReportGenerator {

    fun erzeugeMontageberichtPdf(
        context: Context,
        kalenderWoche: Int,
        jahr: Int,
        daten: Map<Long, List<Arbeitszeit>>,
        selectedAuftrag: Betriebsauftrag,
        vorname: String,
        nachname: String,
        signatureBase64: String? = null,
        kundenSignatureBase64: String? = null,
        kundenName: String? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            isAntiAlias = true
        }

        val bitmapVorlage = BitmapFactory.decodeResource(context.resources, R.drawable.montagebericht)
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        if (bitmapVorlage != null) {
            canvas.drawBitmap(bitmapVorlage, null, Rect(0, 0, 842, 595), paint)
        }

        // Woche berechnen
        val calWeek = Calendar.getInstance(Locale.GERMANY).apply {
            clear()
            set(Calendar.YEAR, jahr)
            set(Calendar.WEEK_OF_YEAR, kalenderWoche)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        val startOfWeek = SimpleDateFormat("dd.MM.yy", Locale.GERMANY).format(calWeek.time)
        calWeek.add(Calendar.DAY_OF_WEEK, 6)
        val endOfWeek = SimpleDateFormat("dd.MM.yy", Locale.GERMANY).format(calWeek.time)

        val schiff = if (selectedAuftrag.kunde.contains("🚢")) selectedAuftrag.kunde.substringAfter("🚢 ").substringBefore(" |") else selectedAuftrag.kunde

        // --- KOPFDATEN ---
        paint.textSize = 10f
        canvas.drawText(schiff, 239f, 70.0f, paint)
        canvas.drawText(selectedAuftrag.liegeplatz ?: "", 239f, 88.7f, paint)
        
        val linkeNummer = if (!selectedAuftrag.ihNummer.isNullOrBlank() && selectedAuftrag.ihNummer != "Ohne IH-Nr") {
            selectedAuftrag.ihNummer
        } else {
            selectedAuftrag.kundenBestellText ?: ""
        }
        canvas.drawText(linkeNummer, 239f, 111.9f, paint)

        canvas.drawText(startOfWeek, 487.5f, 70.0f, paint)
        canvas.drawText(endOfWeek, 588f, 70.0f, paint)
        canvas.drawText("$vorname $nachname", 477f, 88.7f, paint)

        canvas.drawText(selectedAuftrag.auftragsNummer, 713f, 88.7f, paint)
        canvas.drawText(selectedAuftrag.positionsNummer, 713f, 110.7f, paint)

        // --- TABELLEN-SPALTEN ---
        val colDate = 54.2f
        val colWork = 56.4f
        val colDep = 335.8f
        val colRet = 372.2f
        val colTravel = 421f
        val colNormal = 457f
        val col25 = 492f
        val col50 = 525f
        val col150 = 558f
        val colTotal = 594.5f
        val colAllowance = 636f

        var currentDayY = 151.1f
        val rowHeight = 12.5f
        val dayStride = 51.0f
        val textYOffset = 13f

        var totalN = 0.0
        var total25 = 0.0
        var total50 = 0.0
        var total150 = 0.0
        var totalTravel = 0.0
        var totalAllowance = 0.0
        var totalT = 0.0

        val totalXEnd = 780f

        val calendar = Calendar.getInstance(Locale.GERMANY).apply {
            clear()
            set(Calendar.YEAR, jahr)
            set(Calendar.WEEK_OF_YEAR, kalenderWoche)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }

        repeat(7) {
            val timestamp = calendar.timeInMillis
            val entries = daten.filterKeys { ts -> 
                val c = Calendar.getInstance().apply { timeInMillis = ts }
                val cTarget = Calendar.getInstance().apply { timeInMillis = timestamp }
                c.get(Calendar.DAY_OF_YEAR) == cTarget.get(Calendar.DAY_OF_YEAR)
            }.values.flatten()

            val dateStr = SimpleDateFormat("dd.MM.", Locale.GERMANY).format(calendar.time)
            val wochentag = calendar.get(Calendar.DAY_OF_WEEK)
            var accumulatedDay = 0.0
            var usedLinesCount = 0

            if (entries.isEmpty()) {
                paint.strokeWidth = 3f
                paint.color = Color.BLACK 
                canvas.drawLine(colWork, currentDayY + dayStride - 5f, totalXEnd, currentDayY + 5f, paint)
                paint.strokeWidth = 1f
                
                paint.textSize = 7f
                canvas.withRotation(-90f, colDate - 17f, currentDayY + 38f) {
                    drawText(dateStr, colDate - 17f, currentDayY + 38f, paint)
                }
                paint.textSize = 9f
            } else {
                val primaryLiegeplatz = entries.mapNotNull { it.liegeplatzOverride ?: selectedAuftrag.liegeplatz }.firstOrNull { isAusloeseBerechtigt(it) } ?: ""
                val ausloeseTag = calculateDayAusloese(primaryLiegeplatz, timestamp, daten, listOf(selectedAuftrag))

                entries.forEach { zeit ->
                    if (usedLinesCount >= 4) return@forEach

                    val totalDuration = calculateDecimalHoursFromEntry(zeit)
                    val effectiveLP = zeit.liegeplatzOverride ?: selectedAuftrag.liegeplatz ?: ""
                    val isMontage = isAusloeseBerechtigt(effectiveLP)
                    
                    val reiseH1 = if (isMontage) calculateTravelHours(zeit.reiseVon, zeit.reiseBis) else 0.0
                    val reiseH2 = if (isMontage) calculateTravelHours(zeit.rueckreiseVon, zeit.rueckreiseBis) else 0.0
                    val travelDuration = if (zeit.fahrtstunden > 0) zeit.fahrtstunden else (reiseH1 + reiseH2)

                    // Überstunden auf Basis der GESAMTDAUER berechnen
                    val split = calculateOvertime(wochentag, isPublicHoliday(timestamp), isMay1(timestamp), accumulatedDay, totalDuration)
                    
                    // Fahrtzeit von den berechneten Buckets abziehen (priorisiert von Normalzeit)
                    var toSubtract = travelDuration
                    val nWork = maxOf(0.0, split.normal - toSubtract).also { toSubtract = maxOf(0.0, toSubtract - split.normal) }
                    val u25Work = maxOf(0.0, split.ue25 - toSubtract).also { toSubtract = maxOf(0.0, toSubtract - split.ue25) }
                    val u50Work = maxOf(0.0, split.ue50 - toSubtract).also { toSubtract = maxOf(0.0, toSubtract - split.ue50) }
                    val u150Work = maxOf(0.0, split.ue150 - toSubtract)

                    if (usedLinesCount == 0) {
                        paint.textSize = 7f
                        canvas.withRotation(-90f, colDate - 17f, currentDayY + 38f) {
                            drawText(dateStr, colDate - 17f, currentDayY + 38f, paint)
                        }
                        paint.textSize = 9f
                    }

                    val entryStartY = currentDayY + (usedLinesCount * rowHeight) + textYOffset
                    
                    paint.textAlign = Paint.Align.LEFT
                    if (isMontage) {
                        canvas.drawText(zeit.reiseVon ?: "", colDep, entryStartY, paint)
                        canvas.drawText(zeit.reiseBis ?: "", colRet, entryStartY, paint)
                    }
                    
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(f(travelDuration), colTravel, entryStartY, paint)
                    canvas.drawText(f(nWork), colNormal, entryStartY, paint)
                    canvas.drawText(f(u25Work), col25, entryStartY, paint)
                    canvas.drawText(f(u50Work), col50, entryStartY, paint)
                    canvas.drawText(f(u150Work), col150, entryStartY, paint)
                    
                    canvas.drawText(f(totalDuration), colTotal, entryStartY, paint)
                    
                    if (usedLinesCount == 0 && ausloeseTag > 0) {
                        canvas.drawText(f(ausloeseTag), colAllowance, entryStartY, paint)
                        totalAllowance += ausloeseTag
                    }

                    paint.textAlign = Paint.Align.LEFT
                    val wrappedLines = wrapText(zeit.beschreibung ?: "", paint)
                    wrappedLines.forEach { line ->
                        if (usedLinesCount < 4) {
                            val lineY = currentDayY + (usedLinesCount * rowHeight) + textYOffset
                            canvas.drawText(line, colWork, lineY, paint)
                            usedLinesCount++
                        }
                    }

                    if (isMontage && !zeit.rueckreiseVon.isNullOrBlank() && usedLinesCount < 4) {
                        val secondRowY = currentDayY + (usedLinesCount * rowHeight) + textYOffset
                        paint.textAlign = Paint.Align.LEFT
                        canvas.drawText(zeit.rueckreiseVon, colDep, secondRowY, paint)
                        canvas.drawText(zeit.rueckreiseBis ?: "", colRet, secondRowY, paint)
                        canvas.drawText("Rückreise", colWork, secondRowY, paint)
                        usedLinesCount++
                    }
                    
                    accumulatedDay += totalDuration
                    totalN += nWork
                    total25 += u25Work
                    total50 += u50Work
                    total150 += u150Work
                    totalTravel += travelDuration
                    totalT += totalDuration
                }
            }
            currentDayY += dayStride
            calendar.add(Calendar.DAY_OF_WEEK, 1)
        }

        val sumY = 535f 
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(f(totalTravel), colTravel, sumY, paint)
        canvas.drawText(f(totalN), colNormal, sumY, paint)
        canvas.drawText(f(total25), col25, sumY, paint)
        canvas.drawText(f(total50), col50, sumY, paint)
        canvas.drawText(f(total150), col150, sumY, paint)
        canvas.drawText(f(totalT), colTotal, sumY, paint)
        if (totalAllowance > 0) canvas.drawText(f(totalAllowance), colAllowance, sumY, paint)
        paint.isFakeBoldText = false

        if (!signatureBase64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(signatureBase64, Base64.DEFAULT)
                val sigBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (sigBitmap != null) {
                    canvas.drawBitmap(sigBitmap, null, Rect(145, 491, 295, 551), paint)
                }
            } catch (_: Exception) {}
        }

        if (!kundenSignatureBase64.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(kundenSignatureBase64, Base64.DEFAULT)
                val sigBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (sigBitmap != null) {
                    // Position der Kundenunterschrift (weiter links auf gleicher Höhe)
                    canvas.drawBitmap(sigBitmap, null, Rect(20, 491, 140, 551), paint)
                }
                if (!kundenName.isNullOrBlank()) {
                    paint.textSize = 9f
                    canvas.drawText(kundenName, 20f, 565f, paint)
                }
            } catch (_: Exception) {}
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "${vorname}_${nachname}_Montagebericht_${selectedAuftrag.auftragsNummer}_KW${kalenderWoche}_${jahr}.pdf")
        
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            return file
        } catch (_: Exception) {
            pdfDocument.close()
            return null
        }
    }

    private fun f(d: Double) = if (d > 1e-5) String.format(Locale.GERMANY, "%.2f", d) else ""

    private fun wrapText(text: String, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= 275f) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }

    data class OvertimeSplit(val normal: Double, val ue25: Double, val ue50: Double, val ue150: Double)

    private fun calculateOvertime(dayOfWeek: Int, isHoliday: Boolean, isSpecial150: Boolean, alreadyWorked: Double, duration: Double): OvertimeSplit {
        // Feiertage (inkl. 1. Mai) → 150%
        if (isSpecial150 || isHoliday) return OvertimeSplit(normal = 0.0, ue25 = 0.0, ue50 = 0.0, ue150 = duration)

        var n = 0.0; var u25 = 0.0; var u50 = 0.0
        
        // Definition der Grenzen nach Wochentag
        val (limitN, limit25) = when (dayOfWeek) {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY -> Pair(8.0, 10.0)
            Calendar.FRIDAY -> Pair(5.0, 7.0)
            Calendar.SATURDAY -> Pair(0.0, 2.0)
            Calendar.SUNDAY -> Pair(0.0, 0.0)
            else -> Pair(8.0, 10.0) // Fallback auf Standardwoche
        }

        val end = alreadyWorked + duration

        // 1. Normalzeit-Bereich
        if (alreadyWorked < limitN) {
            n = minOf(end, limitN) - alreadyWorked
        }

        // 2. 25% Überstunden-Bereich
        if (end > limitN) {
            val zoneStart = maxOf(alreadyWorked, limitN)
            if (zoneStart < limit25) {
                u25 = minOf(end, limit25) - zoneStart
            }
        }

        // 3. 50% Überstunden-Bereich
        if (end > limit25) {
            u50 = end - maxOf(alreadyWorked, limit25)
        }

        return OvertimeSplit(n, u25, u50, 0.0)
    }

    private fun isMay1(ts: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return (cal.get(Calendar.MONTH) + 1 == 5 && cal.get(Calendar.DAY_OF_MONTH) == 1)
    }

    private fun calculateDayAusloese(lp: String, ts: Long, allDaten: Map<Long, List<Arbeitszeit>>, auftraege: List<Betriebsauftrag>): Double {
        val lpNorm = lp.replace(" ", "").lowercase()
        // Wenn einer der lokalen Orte oder leer, dann gar keine Auslöse
        if (lpNorm == "njw" || lpNorm == "hannoverkai" || lpNorm == "werkstatt" || lpNorm == "mars" || lpNorm == "4.einfahrt" || lp.isBlank()) return 0.0
        
        fun normalizeDate(time: Long): Long {
            return Calendar.getInstance().apply {
                timeInMillis = time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val targetDate = normalizeDate(ts)
        val sortedDaysWithThisLP = allDaten.filter { entry ->
            entry.value.any { zeit -> 
                val effectiveLP = zeit.liegeplatzOverride ?: auftraege.find { a -> a.id == zeit.auftragId }?.liegeplatz ?: ""
                effectiveLP.trim().equals(lp.trim(), ignoreCase = true)
            }
        }.keys.map { normalizeDate(it) }.sorted()

        if (sortedDaysWithThisLP.isEmpty()) return 0.0
        
        // --- LOGIK-ENTSCHEIDUNG ---
        return if (sortedDaysWithThisLP.size > 1) {
            // Es ist eine mehrtägige Montage (Übernachtung) → 14/28 Regel
            val isFirstDay = targetDate == sortedDaysWithThisLP.first()
            val isLastDay = targetDate == sortedDaysWithThisLP.last()
            if (isFirstDay || isLastDay) 14.0 else 28.0
        } else 0.0
    }

    private fun isAusloeseBerechtigt(lp: String): Boolean {
        val lpNorm = lp.replace(" ", "").lowercase()
        return lp.isNotBlank() && lpNorm != "njw" && lpNorm != "hannoverkai" && lpNorm != "werkstatt" && lpNorm != "mars" && lpNorm != "4.einfahrt" && lpNorm != "wsa"
    }

    private fun isPublicHoliday(ts: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
        if ((m == 1 && d == 1) || (m == 5 && d == 1) || (m == 10 && d == 3) || (m == 10 && d == 31) || (m == 12 && d in 24..26) || (m == 12 && d == 31)) return true
        val easter = getEasterSunday(y)
        return listOf(offsetDays(easter, -2), offsetDays(easter, 1), offsetDays(easter, 39), offsetDays(easter, 50)).any { 
            val c = Calendar.getInstance().apply { timeInMillis = it }
            c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) + 1 == m && c.get(Calendar.DAY_OF_MONTH) == d
        }
    }

    private fun getEasterSunday(year: Int): Long {
        val a = year % 19; val b = year % 4; val c = year % 7; val k = year / 100; val p = (8 * k + 13) / 25; val q = k / 4
        val m = (15 + k - p - q) % 30; val n = (4 + k - q) % 7; val d = (19 * a + m) % 30; val e = (2 * b + 4 * c + 6 * d + n) % 7
        val cal = Calendar.getInstance(); cal.set(year, Calendar.MARCH, 22 + d + e, 0, 0, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun offsetDays(baseMs: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = baseMs }; cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.timeInMillis
    }

    private fun calculateTravelHours(von: String?, bis: String?): Double {
        if (von.isNullOrBlank() || bis.isNullOrBlank()) return 0.0
        return try {
            val startParts = von.split(":").map { it.toInt() }; val endParts = bis.split(":").map { it.toInt() }
            val diffMinutes = (endParts[0] * 60 + endParts[1]) - (startParts[0] * 60 + startParts[1])
            if (diffMinutes > 0) diffMinutes / 60.0 else 0.0
        } catch (_: Exception) { 0.0 }
    }
}
