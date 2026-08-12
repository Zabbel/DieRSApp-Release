package com.zabbel.diersapp.util

import android.content.Context
import android.graphics.Bitmap
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

object ReportGenerator {

    private const val MAX_ARBEIT_WIDTH = 240f 

    fun erzeugeWochenberichtPdf(
        context: Context,
        kalenderWoche: Int,
        jahr: Int,
        daten: Map<Long, List<Arbeitszeit>>,
        auftraege: List<Betriebsauftrag>,
        vorname: String,
        nachname: String,
        persNr: String,
        signatureBase64: String? = null,
        wochenInfo: com.zabbel.diersapp.data.model.WochenberichtInfo? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            isAntiAlias = true
        }

        val bitmapVorderseite = BitmapFactory.decodeResource(context.resources, R.drawable.wochenstunden_seite1)
        val bitmapRueckseite = BitmapFactory.decodeResource(context.resources, R.drawable.wochenstunden_seite2)

        // Wochensummen initialisieren
        var wSumN = 0.0
        var wSum25 = 0.0
        var wSum50 = 0.0
        var wSum125 = 0.0
        var wSumGesamt = 0.0
        var wSumAusloese = 0.0
        var wSumFahrt = 0.0

        // Berechnung der Wochensummen vorab
        daten.forEach { (ts, eintraege) ->
            val cal = Calendar.getInstance(Locale.GERMANY).apply { timeInMillis = ts }
            val wochentag = cal.get(Calendar.DAY_OF_WEEK)
            var accumulated = 0.0
            
            // Auslöse-Berechnung für den Tag
            val qualifyingEntries = eintraege.filter { zeit ->
                val lp = zeit.liegeplatzOverride ?: auftraege.find { a -> a.id == zeit.auftragId }?.liegeplatz ?: ""
                isAusloeseBerechtigt(lp)
            }
            val totalQualifyingHours = qualifyingEntries.sumOf { calculateDecimalHoursFromEntry(it) }
            val primaryLiegeplatz = qualifyingEntries.firstNotNullOfOrNull { zeit ->
                zeit.liegeplatzOverride
                    ?: auftraege.find { a -> a.id == zeit.auftragId }?.liegeplatz
            } ?: ""

            wSumAusloese += calculateDayAusloese(primaryLiegeplatz, totalQualifyingHours, ts, daten, auftraege)

            // Überstunden und Fahrtstunden pro Eintrag
            eintraege.sortedBy { it.vonUhrzeit }.forEach { zeit ->
                val totalDuration = calculateDecimalHoursFromEntry(zeit)
                val travelDuration = zeit.fahrtstunden
                
                val auftrag = auftraege.find { it.id == zeit.auftragId }
                val isSystemTask = auftrag?.kunde == "INTERN" && 
                    (auftrag.titelKurz == "Urlaub" || auftrag.titelKurz == "Krank" || auftrag.titelKurz == "Arztbesuch" || auftrag.titelKurz == "FA Abbau" || auftrag.titelKurz == "Feiertag")

                val split = if (isDec24(ts)) {
                    val (hBefore, hAfter) = splitHoursAt1400(zeit)
                    val s1 = calculateOvertime(wochentag, isHoliday = false, isSpecial150 = false, isSystemTask, accumulated, hBefore)
                    val s2 = OvertimeSplit(hAfter, 0.0, 0.0, 0.0, hAfter)
                    combineSplits(s1, s2)
                } else {
                    val is150 = isMay1(ts)
                    val is125 = isHoliday(ts) && !is150
                    calculateOvertime(wochentag, is125, is150, isSystemTask, accumulated, totalDuration)
                }
                
                val nWork = split.normal
                val u25Work = split.ue25
                val u50Work = split.ue50
                val u125Work = split.ue125

                wSumN += nWork
                wSum25 += u25Work
                wSum50 += u50Work
                wSum125 += u125Work
                wSumFahrt += travelDuration
                wSumGesamt += totalDuration
                accumulated += totalDuration
            }
        }

        // Seite 1: Montag bis Donnerstag
        val moBisDo = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)
        erstelleSeite(pdfDocument, 117.02f, bitmapVorderseite, paint, 1, kalenderWoche, jahr, daten, auftraege, moBisDo, vorname, nachname, persNr, signatureBase64, wochenInfo, wSumN, wSum25, wSum50, wSum125, wSumAusloese, wSumFahrt)

        // Seite 2: Freitag bis Sonntag
        val frBisSo = listOf(Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        erstelleSeite(pdfDocument, 60f, bitmapRueckseite, paint, 2, kalenderWoche, jahr, daten, auftraege, frBisSo, vorname, nachname, persNr, signatureBase64, wochenInfo, wSumN, wSum25, wSum50, wSum125, wSumAusloese, wSumFahrt)

        val dateiName = "${vorname}_${nachname}_Wochenbericht_KW${kalenderWoche}_$jahr.pdf"
        val file = File(context.cacheDir, dateiName)

        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            bitmapVorderseite.recycle()
            bitmapRueckseite.recycle()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    private fun erstelleSeite(
        pdfDocument: PdfDocument,
        startY: Float,
        bitmap: Bitmap,
        paint: Paint,
        seitenNummer: Int,
        kw: Int,
        jahr: Int,
        allDaten: Map<Long, List<Arbeitszeit>>,
        auftraege: List<Betriebsauftrag>,
        erlaubteTage: List<Int>,
        vorname: String,
        nachname: String,
        persNr: String,
        signatureBase64: String?,
        wochenInfo: com.zabbel.diersapp.data.model.WochenberichtInfo?,
        wN: Double, w25: Double, w50: Double, w125: Double, wAus: Double, wSumFahrt: Double
    ) {
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, seitenNummer).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawBitmap(bitmap, null, Rect(0, 0, 842, 595), paint)

        // Kopfdaten (nur Seite 1)
        if (seitenNummer == 1) {
            paint.textSize = 12f 
            canvas.drawText("$vorname $nachname", 265f, 44.5f, paint)
            canvas.drawText(persNr, 265f, 74.5f, paint)
            canvas.drawText(kw.toString(), 507.5f, 44.5f, paint)

            val cal = Calendar.getInstance(Locale.GERMANY).apply {
                clear()
                set(Calendar.YEAR, jahr)
                set(Calendar.WEEK_OF_YEAR, kw)
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            }
            canvas.drawText(SimpleDateFormat("dd.MM.yy", Locale.GERMANY).format(cal.time), 468.0f, 74.5f, paint)
            cal.add(Calendar.DAY_OF_WEEK, 6)
            canvas.drawText(SimpleDateFormat("dd.MM.yy", Locale.GERMANY).format(cal.time), 588.5f, 74.5f, paint)
            paint.textSize = 9f
        }

        // Spalten-Definitionen
        val colDatum = 40f
        val colAuftrag = 106.2f 
        val colPos = if (seitenNummer == 1) 203.2f else 205.2f 
        val colArbeit = if (seitenNummer == 1) 252.5f else 251.67f 
        val colNormal = if (seitenNummer == 1) 526f else 527.25f
        
        val col25 = colNormal + 45f 
        val col50 = colNormal + 77.16f 
        val col125 = colNormal + 115f 
        val colFahrt = colNormal + 162f 
        val colAusloese = colNormal + 268.57f 
        val colLiege = if (seitenNummer == 1) 724.76f else 725.35f

        var currentDayY = startY
        val dayStride = 99.9f // Korrektur: 35mm Abstand (vorher 101.34f)
        val zeilenAbstand = 11.97f // Korrektur: 4mm Abstand (vorher 12.67f)
        val ersteZeileOffset = 0.65f // Korrektur: 0.5mm nach unten für die erste Zeile

        erlaubteTage.forEach { wochentag ->
            val tagesEintraege = allDaten.filter { entry ->
                val c = Calendar.getInstance(Locale.GERMANY).apply { timeInMillis = entry.key }
                c.get(Calendar.DAY_OF_WEEK) == wochentag
            }.values.flatten().sortedBy { it.vonUhrzeit }

            val dayTimestamp = allDaten.keys.find { ts ->
                val c = Calendar.getInstance(Locale.GERMANY).apply { timeInMillis = ts }
                c.get(Calendar.DAY_OF_WEEK) == wochentag
            }
            val datumString = if (dayTimestamp != null) SimpleDateFormat("dd.MM.", Locale.GERMANY).format(Date(dayTimestamp)) else ""

            var tSumN = 0.0
            var tSum25 = 0.0
            var tSum50 = 0.0
            var tSum125 = 0.0
            var tSumFahrt = 0.0
            var accumulated = 0.0
            var zeilenCounter = 0

            // Auslöse für den Tag
            val qualifyingEntries = tagesEintraege.filter { zeit ->
                val lp = zeit.liegeplatzOverride ?: auftraege.find { a -> a.id == zeit.auftragId }?.liegeplatz ?: ""
                isAusloeseBerechtigt(lp)
            }
            val totalQualifyingHours = qualifyingEntries.sumOf { calculateDecimalHoursFromEntry(it) }
            val primaryLiegeplatz = qualifyingEntries.firstNotNullOfOrNull { zeit ->
                zeit.liegeplatzOverride
                    ?: auftraege.find { a -> a.id == zeit.auftragId }?.liegeplatz
            } ?: ""

            val ausloese = calculateDayAusloese(primaryLiegeplatz, totalQualifyingHours, dayTimestamp, allDaten, auftraege)

            tagesEintraege.forEach { zeit ->
                if (zeilenCounter >= 7) return@forEach

                val auftrag = auftraege.find { it.id == zeit.auftragId }
                val effectiveLiegeplatz = zeit.liegeplatzOverride ?: auftrag?.liegeplatz ?: ""
                val totalDuration = calculateDecimalHoursFromEntry(zeit)
                val travelDuration = zeit.fahrtstunden
                val currentY = currentDayY + (zeilenCounter * zeilenAbstand) + ersteZeileOffset
                
                val isSystemTask = auftrag?.kunde == "INTERN" && 
                    (auftrag.titelKurz == "Urlaub" || auftrag.titelKurz == "Krank" || auftrag.titelKurz == "Arztbesuch" || auftrag.titelKurz == "FA Abbau" || auftrag.titelKurz == "Feiertag")

                val split = if (isDec24(dayTimestamp)) {
                    val (hBefore, hAfter) = splitHoursAt1400(zeit)
                    val s1 = calculateOvertime(wochentag, isHoliday = false, isSpecial150 = false, isSystemTask, accumulated, hBefore)
                    val s2 = OvertimeSplit(hAfter, 0.0, 0.0, 0.0, hAfter)
                    combineSplits(s1, s2)
                } else {
                    val is150 = isMay1(dayTimestamp)
                    val is125 = isHoliday(dayTimestamp) && !is150
                    calculateOvertime(wochentag, is125, is150, isSystemTask, accumulated, totalDuration)
                }
                
                val nWork = split.normal
                val u25Work = split.ue25
                val u50Work = split.ue50
                val u125Work = split.ue125

                tSumN += nWork
                tSum25 += u25Work
                tSum50 += u50Work
                tSum125 += u125Work
                tSumFahrt += travelDuration
                accumulated += totalDuration

                val isUrlaubKrank = auftrag?.kunde == "INTERN" && 
                                 (auftrag.titelKurz == "Urlaub" || auftrag.titelKurz == "Krank" || auftrag.titelKurz == "FA Abbau")

                if (zeilenCounter == 0) {
                    canvas.drawText(datumString, colDatum, currentY + 2.8f, paint)
                    if (ausloese > 0) canvas.drawText(String.format(Locale.GERMANY, "%.0f ,- €", ausloese), colAusloese, currentY + 29.76f, paint)
                }
                
                if (!isUrlaubKrank) {
                    canvas.drawText(auftrag?.auftragsNummer ?: "", colAuftrag, currentY, paint)
                    canvas.drawText(auftrag?.positionsNummer ?: "", colPos, currentY, paint)
                    canvas.drawText(effectiveLiegeplatz, colLiege, currentY, paint)
                }
                
                if (nWork > 0) canvas.drawText(f(nWork), colNormal, currentY, paint)
                if (u25Work > 0) canvas.drawText(f(u25Work), col25, currentY, paint)
                if (u50Work > 0) canvas.drawText(f(u50Work), col50, currentY, paint)
                if (u125Work > 0) canvas.drawText(f(u125Work), col125, currentY, paint)
                
                // Fahrtstunden werden immer gedruckt, wenn > 0
                if (zeit.fahrtstunden > 0) canvas.drawText(f(zeit.fahrtstunden), colFahrt, currentY, paint)

                val schiff = if (auftrag?.kunde?.contains("🚢") == true) auftrag.kunde.substringAfter("🚢 ").substringBefore(" |") else ""
                val vollstBeschr = buildString {
                    if (schiff.isNotBlank()) append("$schiff -> ")
                    val isShipLikeIntern = auftrag?.kunde == "INTERN" && (auftrag.titelKurz == "Schulung" || auftrag.titelKurz == "Werkstatt" || auftrag.titelKurz == "Besichtigung")
                    if (isShipLikeIntern) append("${auftrag.titelKurz} -> ")
                    if (auftrag != null && auftrag.kunde != "INTERN") append("${auftrag.titelKurz} -> ")
                    append(zeit.beschreibung ?: "")
                    if (split.ue150 > 0) append(" (!!! 150% FEIERTAG !!!)")
                }

                val lines = wrapText(vollstBeschr, paint)
                var textY = currentY
                lines.forEach { line ->
                    if (zeilenCounter < 7) {
                        canvas.drawText(line, colArbeit, textY, paint)
                        textY += zeilenAbstand
                        if (lines.size > 1) zeilenCounter++ // Nur erhöhen wenn wirklich mehrzeilig
                    }
                }
                if (lines.size <= 1) zeilenCounter++
            }

            // Tagessummen (Abstand zum Tagesanfang beibehalten, da dieser "passte")
            val sumY = currentDayY + 87.275f
            paint.isFakeBoldText = true
            if (tSumN > 0) canvas.drawText(f(tSumN), colNormal, sumY, paint)
            if (tSum25 > 0) canvas.drawText(f(tSum25), col25, sumY, paint)
            if (tSum50 > 0) canvas.drawText(f(tSum50), col50, sumY, paint)
            if (tSum125 > 0) canvas.drawText(f(tSum125), col125, sumY, paint)
            if (tSumFahrt > 0) canvas.drawText(f(tSumFahrt), colFahrt, sumY, paint)
            paint.isFakeBoldText = false
            
            currentDayY += dayStride
        }

        // Wochensummen auf Seite 2
        if (seitenNummer == 2) {
            val footerY = 390f // war 410.65f
            paint.isFakeBoldText = true
            if (wN > 0) canvas.drawText(f(wN), colNormal, footerY, paint)
            if (w25 > 0) canvas.drawText(f(w25), col25, footerY, paint)
            if (w50 > 0) canvas.drawText(f(w50), col50, footerY, paint)
            if (w125 > 0) canvas.drawText(f(w125), col125, footerY, paint)
            // Wochensumme Fahrtstunden neben 125%
            if (wSumFahrt > 0) canvas.drawText(f(wSumFahrt), colFahrt, footerY, paint)
            // Wochensumme Auslöse
            if (wAus > 0) canvas.drawText(String.format(Locale.GERMANY, "%.0f ,- €", wAus), colAusloese - 5f, footerY, paint)
            paint.isFakeBoldText = false
            
            if (!signatureBase64.isNullOrBlank()) {
                try {
                    val bytes = Base64.decode(signatureBase64, Base64.DEFAULT)
                    val sigBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (sigBitmap != null) {
                        val rect = Rect(12, 329, 162, 389)
                        canvas.drawBitmap(sigBitmap, null, rect, paint)
                        sigBitmap.recycle()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (wochenInfo != null) {
                paint.textSize = 12f
                val extraY = 460f // war 491.3f
                canvas.drawText(wochenInfo.telefonMonat, 224f, extraY, paint)
                if (wochenInfo.telefonEuro > 0) canvas.drawText(f(wochenInfo.telefonEuro), 763f, extraY, paint)
                val kmY = 490f // war 522.585f
                if (wochenInfo.privatKm > 0) {
                    canvas.drawText(String.format(Locale.GERMANY, "%.1f", wochenInfo.privatKm), 250.6f, kmY, paint)
                    canvas.drawText(f(wochenInfo.privatKm * 0.30), 763f, kmY, paint)
                }
                paint.textSize = 9f
            }
        }
        pdfDocument.finishPage(page)
    }

    private fun f(d: Double) = String.format(Locale.GERMANY, "%.2f", d)

    data class OvertimeSplit(val normal: Double, val ue25: Double, val ue50: Double, val ue125: Double, val ue150: Double)

    private fun calculateOvertime(dayOfWeek: Int, isHoliday: Boolean, isSpecial150: Boolean, isSystemTask: Boolean, alreadyWorked: Double, duration: Double): OvertimeSplit {
        if (isSystemTask) return OvertimeSplit(normal = duration, ue25 = 0.0, ue50 = 0.0, ue125 = 0.0, ue150 = 0.0)
        if (isSpecial150) return OvertimeSplit(normal = duration, ue25 = 0.0, ue50 = 0.0, ue125 = 0.0, ue150 = duration)
        if (isHoliday) return OvertimeSplit(normal = 0.0, ue25 = 0.0, ue50 = 0.0, ue125 = duration, ue150 = 0.0)

        var n = 0.0; var u25 = 0.0; var u50 = 0.0; var u125 = 0.0
        
        val (limitN, limit25, limit50) = when (dayOfWeek) {
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY -> Triple(8.0, 10.0, Double.MAX_VALUE)
            Calendar.FRIDAY -> Triple(5.0, 7.0, Double.MAX_VALUE)
            Calendar.SATURDAY -> Triple(0.0, 2.0, Double.MAX_VALUE)
            Calendar.SUNDAY -> Triple(0.0, 0.0, Double.MAX_VALUE)
            else -> Triple(8.0, 10.0, Double.MAX_VALUE)
        }

        fun distribute(start: Double, end: Double) {
            if (start >= end) return
            val nStart = maxOf(start, 0.0); val nEnd = minOf(end, limitN)
            if (nEnd > nStart) n += (nEnd - nStart)
            val u25Start = maxOf(start, limitN); val u25End = minOf(end, limit25)
            if (u25End > u25Start) u25 += (u25End - u25Start)
            val u50Start = maxOf(start, limit25); val u50End = minOf(end, limit50)
            if (u50End > u50Start) u50 += (u50End - u50Start)
            val u125Start = maxOf(start, limit50)
            if (end > u125Start) u125 += (end - u125Start)
        }

        distribute(alreadyWorked, alreadyWorked + duration)
        return OvertimeSplit(n, u25, u50, u125, 0.0)
    }

    private fun calculateDayAusloese(lp: String, hours: Double, ts: Long?, allDaten: Map<Long, List<Arbeitszeit>>, auftraege: List<Betriebsauftrag>): Double {
        val lpNorm = lp.trim().lowercase().replace(Regex("\\s+"), "")
        
        // Wenn einer der lokalen Orte oder leer, dann gar keine Auslöse
        // WSA ist AUSLÖSEBERECHTIGT, daher hier nicht in der Liste
        val baseLocations = listOf("njw", "hannoverkai", "werkstatt", "schulung", "büro")
        if (lpNorm.isBlank() || baseLocations.any { lpNorm.contains(it) }) return 0.0
        
        // Sonderlogik für Orte, die IMMER nur Nahauslöse (6€) geben, egal wie viele Tage
        val isAlwaysNearby = lpNorm.contains("mars") || lpNorm.contains("4.einfahrt")

        if (isAlwaysNearby) {
            // Nahauslöse gibt es erst AB 6 Stunden Abwesenheit von der Basis
            return if (hours >= 6.0) 6.0 else 0.0
        }

        // Da es kein lokaler Ort ist, ist es automatisch "Montage" (14€/28€ Logik)
        if (ts == null) return 0.0
        
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
        } else {
            // Es ist nur ein einzelner Tag (Nahauslöse) → 6€ ab 6 Stunden
            if (hours >= 6.0) 6.0 else 0.0
        }
    }

    private fun isAusloeseBerechtigt(lp: String): Boolean {
        val lpNorm = lp.trim().lowercase().replace(Regex("\\s+"), "")
        // WSA ist auslöseberechtigt
        val baseLocations = listOf("njw", "hannoverkai", "werkstatt", "schulung", "büro")
        return lpNorm.isNotBlank() && baseLocations.none { lpNorm.contains(it) }
    }

    private fun isHoliday(ts: Long?): Boolean {
        if (ts == null) return false
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)

        if (m == 1 && d == 1) return true
        if (m == 5 && d == 1) return true
        if (m == 10 && d == 3) return true
        if (m == 10 && d == 31) return true
        if (m == 12 && d == 24) return true
        if (m == 12 && d == 25) return true
        if (m == 12 && d == 26) return true
        if (m == 12 && d == 31) return true

        val easter = getEasterSunday(y)
        val movableHolidays = listOf(
            offsetDays(easter, -2),
            offsetDays(easter, 1),
            offsetDays(easter, 39),
            offsetDays(easter, 50)
        )

        val currentDay = String.format(Locale.GERMANY, "%04d-%02d-%02d", y, m, d)
        return movableHolidays.any { 
            val c = Calendar.getInstance().apply { timeInMillis = it }
            String.format(Locale.GERMANY, "%04d-%02d-%02d", 
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
            ) == currentDay 
        }
    }

    private fun getEasterSunday(year: Int): Long {
        val a = year % 19
        val b = year % 4
        val c = year % 7
        val k = year / 100
        val p = (8 * k + 13) / 25
        val q = k / 4
        val m = (15 + k - p - q) % 30
        val n = (4 + k - q) % 7
        val d = (19 * a + m) % 30
        val e = (2 * b + 4 * c + 6 * d + n) % 7
        val day = 22 + d + e
        val month = Calendar.MARCH

        val cal = Calendar.getInstance()
        cal.set(year, month, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun offsetDays(baseMs: Long, days: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = baseMs }
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.timeInMillis
    }

    private fun isMay1(ts: Long?): Boolean {
        if (ts == null) return false
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return (cal.get(Calendar.MONTH) + 1 == 5 && cal.get(Calendar.DAY_OF_MONTH) == 1)
    }

    private fun isDec24(ts: Long?): Boolean {
        if (ts == null) return false
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return (cal.get(Calendar.MONTH) + 1 == 12 && cal.get(Calendar.DAY_OF_MONTH) == 24)
    }

    private fun splitHoursAt1400(zeit: Arbeitszeit): Pair<Double, Double> {
        val startTotal = timeToMin(zeit.vonUhrzeit)
        val endTotal = timeToMin(zeit.bisUhrzeit)
        val limit = 14 * 60
        val totalNetto = calculateDecimalHoursFromEntry(zeit)
        
        if (endTotal <= limit) return Pair(totalNetto, 0.0)
        if (startTotal >= limit) return Pair(0.0, totalNetto)
        
        val ratioBefore = (limit - startTotal).toDouble() / (endTotal - startTotal).toDouble()
        return Pair(totalNetto * ratioBefore, totalNetto * (1.0 - ratioBefore))
    }

    private fun timeToMin(t: String): Int {
        val p = t.split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }

    private fun combineSplits(s1: OvertimeSplit, s2: OvertimeSplit) = OvertimeSplit(
        s1.normal + s2.normal, s1.ue25 + s2.ue25, s1.ue50 + s2.ue50, s1.ue125 + s2.ue125, s1.ue150 + s2.ue150
    )

    private fun wrapText(text: String, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= MAX_ARBEIT_WIDTH) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines
    }
}
