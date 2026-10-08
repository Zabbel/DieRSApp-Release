package com.zabbel.diersapp.viewmodel

import android.net.Uri
import android.util.Base64
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zabbel.diersapp.data.model.*
import com.zabbel.diersapp.data.repository.AuftragRepository
import com.zabbel.diersapp.datastore.AppSettingsDataStore
import com.zabbel.diersapp.util.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import javax.crypto.Cipher

@HiltViewModel
class AuftragViewModel @Inject constructor(
    private val repository: AuftragRepository,
    private val appSettingsDataStore: AppSettingsDataStore
) : ViewModel() {

    val useBiometrics: StateFlow<Boolean> = appSettingsDataStore.useBiometrics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setUseBiometrics(enabled: Boolean) {
        viewModelScope.launch {
            appSettingsDataStore.setUseBiometrics(enabled)
            if (!enabled) {
                appSettingsDataStore.saveEncryptedPin("")
            }
        }
    }

    fun getEncryptCipher(): Cipher? {
        return try {
            CryptoManager.getEncryptCipher()
        } catch (e: Exception) {
            android.util.Log.e("RS_Bio", "Fehler beim Erstellen des Ciphers", e)
            null
        }
    }

    fun setupBiometricSecret(result: BiometricPrompt.AuthenticationResult) {
        viewModelScope.launch {
            try {
                val cipher = result.cryptoObject?.cipher ?: return@launch
                val storedCombinedHash = appSettingsDataStore.pinHash.first() ?: return@launch
                val parts = storedCombinedHash.split(":")
                if (parts.size != 2) return@launch
                val hashBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                
                val (encrypted, iv) = CryptoManager.encrypt(hashBytes, cipher)
                val encryptedString = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
                
                appSettingsDataStore.saveEncryptedPin("$encryptedString|$ivString")
                appSettingsDataStore.setUseBiometrics(true)
            } catch (e: Exception) {
                android.util.Log.e("RS_Bio", "Setup Biometric Fehler", e)
            }
        }
    }

    val auftraege: StateFlow<List<Betriebsauftrag>> = repository.allAuftraege
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuftraege: StateFlow<List<Betriebsauftrag>> = repository.allAuftraegeIncludingInternal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var pendingAuftrag by mutableStateOf<Betriebsauftrag?>(null)
    var capturedImageUris by mutableStateOf<List<Uri>>(emptyList())
    var croppedImageUris = mutableListOf<Uri>()
    var currentCropIndex by mutableIntStateOf(0)

    fun addCapturedImage(uri: Uri) {
        capturedImageUris = capturedImageUris + uri
    }

    fun clearCapturedImages() {
        capturedImageUris = emptyList()
        croppedImageUris.clear()
        currentCropIndex = 0
    }

    suspend fun getExistingAuftrag(nr: String, pos: String): Betriebsauftrag? {
        return repository.findAuftragByNrAndPos(nr, pos)
    }

    fun saveExtractedAuftrag(auftrag: Betriebsauftrag) {
        viewModelScope.launch { repository.addAuftrag(auftrag) }
    }

    fun clearPendingAuftrag() { pendingAuftrag = null }

    fun deleteAuftrag(auftrag: Betriebsauftrag) {
        viewModelScope.launch { repository.deleteAuftrag(auftrag) }
    }

    fun saveArbeitszeit(
        auftragId: Long,
        datum: Long,
        von: String,
        bis: String,
        pause: Int,
        beschreibung: String? = null,
        fahrtstunden: Double = 0.0,
        liegeplatzOverride: String? = null,
        reiseVon: String? = null,
        reiseBis: String? = null,
        rueckreiseVon: String? = null,
        rueckreiseBis: String? = null,
        id: Long = 0
    ) {
        viewModelScope.launch {
            val normalizedDatum = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = datum
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val zeit = Arbeitszeit(
                id = id,
                auftragId = auftragId,
                datum = normalizedDatum,
                vonUhrzeit = von,
                bisUhrzeit = bis,
                pauseMinuten = pause,
                beschreibung = if (beschreibung.isNullOrBlank()) null else beschreibung,
                fahrtstunden = fahrtstunden,
                liegeplatzOverride = liegeplatzOverride,
                reiseVon = reiseVon,
                reiseBis = reiseBis,
                rueckreiseVon = rueckreiseVon,
                rueckreiseBis = rueckreiseBis
            )
            repository.upsertArbeitszeit(zeit)
        }
    }

    fun deleteArbeitszeit(arbeitszeit: Arbeitszeit) {
        viewModelScope.launch { repository.deleteArbeitszeit(arbeitszeit) }
    }

    fun saveQuickArbeitszeit(
        currentAuftragId: Long,
        datum: Long,
        von: String,
        bis: String,
        pause: Int,
        beschreibung: String,
        fahrtstunden: Double = 0.0,
        liegeplatzOverride: String? = null,
        reiseVon: String? = null,
        reiseBis: String? = null,
        rueckreiseVon: String? = null,
        rueckreiseBis: String? = null,
        quickTask: QuickTask?,
        id: Long = 0
    ) {
        viewModelScope.launch {
            var targetId = currentAuftragId

            if (quickTask != null) {
                val existing = if (quickTask.isSystemState) {
                    repository.findSystemAuftrag(quickTask.label)
                } else {
                    repository.findAuftragByNrAndPos(quickTask.auftragsNummer ?: "", quickTask.positionsNummer ?: "")
                }

                targetId = if (existing != null) {
                    existing.id
                } else {
                    val newSystemAuftrag = Betriebsauftrag(
                        auftragsNummer = if (quickTask.isSystemState) "" else (quickTask.auftragsNummer ?: ""),
                        positionsNummer = if (quickTask.isSystemState) "" else (quickTask.positionsNummer ?: ""),
                        kunde = "INTERN",
                        titelKurz = quickTask.label,
                        beschreibungLang = quickTask.description,
                        abrechnungsArt = "Aufwand"
                    )
                    repository.addAuftrag(newSystemAuftrag)
                }
            }

            saveArbeitszeit(
                auftragId = targetId,
                datum = datum,
                von = von,
                bis = bis,
                pause = pause,
                beschreibung = beschreibung,
                fahrtstunden = fahrtstunden,
                liegeplatzOverride = liegeplatzOverride,
                reiseVon = reiseVon,
                reiseBis = reiseBis,
                rueckreiseVon = rueckreiseVon,
                rueckreiseBis = rueckreiseBis,
                id = id
            )
        }
    }

    fun getWorkHoursForWeek(startTimestamp: Long, endTimestamp: Long): Flow<Map<Long, List<Arbeitszeit>>> {
        return repository.getArbeitszeitenInPeriod(startTimestamp, endTimestamp)
            .map { list -> 
                list.groupBy { 
                    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                        timeInMillis = it.datum
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                } 
            }
    }

    fun getArbeitszeitenInPeriodRaw(startTimestamp: Long, endTimestamp: Long): Flow<List<Arbeitszeit>> {
        return repository.getArbeitszeitenInPeriod(startTimestamp, endTimestamp)
    }

    fun getArbeitszeitenForAuftrag(auftragId: Long): Flow<List<Arbeitszeit>> {
        return repository.getArbeitszeitenForAuftrag(auftragId)
    }

    fun getTotalHoursForAuftrag(auftragId: Long): Flow<Double> {
        return repository.getArbeitszeitenForAuftrag(auftragId).map { liste ->
            liste.sumOf { zeit ->
                val von = zeit.vonUhrzeit.split(":")
                val bis = zeit.bisUhrzeit.split(":")
                if (von.size == 2 && bis.size == 2) {
                    calculateDecimalHours(
                        von[0].toInt(), von[1].toInt(),
                        bis[0].toInt(), bis[1].toInt(),
                        zeit.pauseMinuten
                    )
                } else 0.0
            }
        }
    }

    fun getWeekRange(calendar: Calendar): Pair<Long, Long> {
        val cal = calendar.clone() as Calendar
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return Pair(start, end)
    }

    val userSettings: StateFlow<UserSettings?> = repository.userSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveSettings(vorname: String, nachname: String, persNr: String, signatureBase64: String? = null, wochenstunden: Double = 37.0) {
        viewModelScope.launch {
            repository.saveSettings(
                UserSettings(
                    vorname = vorname,
                    nachname = nachname,
                    personalnummer = persNr,
                    signatureBase64 = signatureBase64,
                    wochenstunden = wochenstunden
                )
            )
        }
    }

    fun getWochenInfo(calendar: Calendar): Flow<WochenberichtInfo?> {
        return repository.getWochenInfo(calendar.get(ATA_YEAR_FALLBACK), calendar.get(Calendar.WEEK_OF_YEAR))
    }

    fun saveWochenInfo(jahr: Int, kw: Int, telefonMonat: String, telefonEuro: Double, privatKm: Double) {
        viewModelScope.launch {
            repository.saveWochenInfo(WochenberichtInfo(jahr, kw, telefonMonat, telefonEuro, privatKm))
        }
    }

    fun getYearlyStats(year: Int): Flow<StatistikDaten> {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfYear = cal.timeInMillis

        cal.set(Calendar.YEAR, year + 1)
        val endOfYear = cal.timeInMillis - 1

        return repository.getArbeitszeitenInPeriod(startOfYear, endOfYear).map { entries ->
            // Alle Aufträge holen, um zu wissen, was Urlaub und was Krank ist
            val allOrders = repository.allAuftraegeIncludingInternal.first()
            
            val urlaubIds = allOrders.filter { it.kunde == "INTERN" && it.titelKurz == "Urlaub" }.map { it.id }.toSet()
            val krankIds = allOrders.filter { it.kunde == "INTERN" && it.titelKurz == "Krank" }.map { it.id }.toSet()

            // Eindeutige Tage (Datum) zählen, an denen gebucht wurde
            val urlaubstage = entries.filter { it.auftragId in urlaubIds }.map { it.datum }.toSet().size
            val krankheitstage = entries.filter { it.auftragId in krankIds }.map { it.datum }.toSet().size

            StatistikDaten(
                krankheitstage = krankheitstage,
                urlaubstage = urlaubstage,
                jahr = year
            )
        }
    }

    fun copyDayEntries(fromTimestamp: Long) {
        viewModelScope.launch {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = fromTimestamp
            }
            
            // 1. Wochenbereich und Ziel-Stunden ermitteln
            val weekRange = getWeekRange(cal)
            val settings = userSettings.first()
            val targetHours = settings?.wochenstunden ?: 37.0
            
            // 2. Alle Einträge der Woche holen, um das aktuelle Total zu berechnen
            val allWeekEntries = repository.getArbeitszeitenInPeriod(weekRange.first, weekRange.second).first()
            
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val targetDay = cal.timeInMillis
            
            // Wir berechnen das Total OHNE den Ziel-Tag (falls da schon was wäre, was durch UI-Sperre aber nicht sein sollte)
            val currentTotal = allWeekEntries
                .filter { it.datum != targetDay }
                .sumOf { calculateDecimalHoursFromEntry(it) }
            
            var remainingBudget = maxOf(0.0, targetHours - currentTotal)
            if (remainingBudget <= 0.0) return@launch

            // 3. Einträge des Quell-Tages holen
            val sourceEntries = allWeekEntries
                .filter { it.datum == fromTimestamp }
                .sortedBy { it.vonUhrzeit }

            sourceEntries.forEach { entry ->
                if (remainingBudget <= 0.0) return@forEach
                
                val entryHours = calculateDecimalHoursFromEntry(entry)
                val hoursToCopy = minOf(entryHours, remainingBudget)
                
                if (hoursToCopy < entryHours) {
                    // Eintrag muss gekürzt werden
                    val newBis = calculateEndTime(entry.vonUhrzeit, hoursToCopy)
                    val newPause = calculatePauseForPeriod(
                        entry.vonUhrzeit.split(":")[0].toInt(),
                        entry.vonUhrzeit.split(":")[1].toInt(),
                        newBis.split(":")[0].toInt(),
                        newBis.split(":")[1].toInt()
                    )
                    repository.upsertArbeitszeit(entry.copy(
                        id = 0, 
                        datum = targetDay, 
                        bisUhrzeit = newBis, 
                        pauseMinuten = newPause
                    ))
                } else {
                    // Voller Eintrag passt noch ins Budget
                    repository.upsertArbeitszeit(entry.copy(id = 0, datum = targetDay))
                }
                
                remainingBudget -= hoursToCopy
            }
        }
    }

    fun getKundenUnterschrift(auftragId: Long, jahr: Int, kw: Int): Flow<KundenUnterschrift?> {
        return repository.getKundenUnterschrift(auftragId, jahr, kw)
    }

    val allKundenUnterschriften: StateFlow<List<KundenUnterschrift>> = repository.getAllKundenUnterschriften()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveKundenUnterschrift(auftragId: Long, jahr: Int, kw: Int, base64: String) {
        viewModelScope.launch {
            repository.saveKundenUnterschrift(KundenUnterschrift(auftragId, jahr, kw, base64))
        }
    }

    private fun calculateEndTime(startTimeStr: String, targetHours: Double): String {
        val parts = startTimeStr.split(":")
        val startMin = parts[0].toInt() * 60 + parts[1].toInt()
        val targetMin = (targetHours * 60).toInt()
        
        var currentEndMin = startMin + targetMin
        
        // Iterative Annäherung, falls Pausen dazwischen liegen
        repeat(3) {
            val pause = calculatePauseForPeriod(startMin / 60, startMin % 60, currentEndMin / 60, currentEndMin % 60)
            currentEndMin = startMin + targetMin + pause
        }
        
        return String.format(Locale.GERMANY, "%02d:%02d", currentEndMin / 60, currentEndMin % 60)
    }

    private fun calculatePauseForPeriod(startH: Int, startM: Int, endH: Int, endM: Int): Int {
        val startTotal = startH * 60 + startM
        val endTotal = endH * 60 + endM
        var totalPause = 0
        if (startTotal < 9 * 60 && endTotal > 8 * 60 + 45) {
            totalPause += 15
        }
        if (startTotal < 13 * 60 && endTotal > 12 * 60 + 30) {
            totalPause += 30
        }
        return totalPause
    }

    private fun calculateDecimalHoursFromEntry(zeit: Arbeitszeit): Double {
        val von = zeit.vonUhrzeit.split(":")
        val bis = zeit.bisUhrzeit.split(":")
        return if (von.size == 2 && bis.size == 2) {
            calculateDecimalHours(von[0].toInt(), von[1].toInt(), bis[0].toInt(), bis[1].toInt(), zeit.pauseMinuten)
        } else 0.0
    }

    companion object {
        private const val ATA_YEAR_FALLBACK = Calendar.YEAR
    }
}

fun calculateDecimalHours(startHour: Int, startMin: Int, endHour: Int, endMin: Int, breakMin: Int): Double {
    val durationMinutes = (endHour * 60 + endMin) - (startHour * 60 + startMin) - breakMin
    return if (durationMinutes > 0) durationMinutes / 60.0 else 0.0
}
