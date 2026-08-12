package com.zabbel.diersapp.data.repository

import android.content.Context
import com.zabbel.diersapp.data.AppDatabase
import com.zabbel.diersapp.data.model.Arbeitszeit
import com.zabbel.diersapp.data.model.Betriebsauftrag
import com.zabbel.diersapp.data.model.UserSettings
import com.zabbel.diersapp.data.model.WochenberichtInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuftragRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val databaseInstance = MutableStateFlow<AppDatabase?>(null)

    fun initDatabase(passphrase: ByteArray) {
        if (databaseInstance.value == null) {
            databaseInstance.value = AppDatabase.getDatabase(context, passphrase)
        }
    }

    fun changePassphrase(newPassphrase: ByteArray) {
        databaseInstance.value?.changePassphrase(newPassphrase)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allAuftraege: Flow<List<Betriebsauftrag>> = databaseInstance.flatMapLatest { db ->
        db?.auftragDao()?.getAllAuftraege() ?: emptyFlow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allAuftraegeIncludingInternal: Flow<List<Betriebsauftrag>> = databaseInstance.flatMapLatest { db ->
        db?.auftragDao()?.getAllAuftraegeIncludingInternal() ?: emptyFlow()
    }

    suspend fun findAuftragByNrAndPos(nr: String, pos: String): Betriebsauftrag? {
        return databaseInstance.value?.auftragDao()?.findAuftragByNrAndPos(nr, pos)
    }

    suspend fun addAuftrag(auftrag: Betriebsauftrag): Long {
        return databaseInstance.value?.auftragDao()?.insertAuftrag(auftrag) ?: -1L
    }

    suspend fun deleteAuftrag(auftrag: Betriebsauftrag) {
        databaseInstance.value?.auftragDao()?.deleteAuftrag(auftrag)
    }

    suspend fun upsertArbeitszeit(arbeitszeit: Arbeitszeit) {
        databaseInstance.value?.arbeitszeitDao()?.upsertArbeitszeit(arbeitszeit)
    }

    suspend fun deleteArbeitszeit(arbeitszeit: Arbeitszeit) {
        databaseInstance.value?.arbeitszeitDao()?.deleteArbeitszeit(arbeitszeit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getArbeitszeitenForAuftrag(auftragId: Long): Flow<List<Arbeitszeit>> {
        return databaseInstance.flatMapLatest { db ->
            db?.arbeitszeitDao()?.getArbeitszeitenForAuftrag(auftragId) ?: emptyFlow()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getArbeitszeitenInPeriod(start: Long, end: Long): Flow<List<Arbeitszeit>> {
        return databaseInstance.flatMapLatest { db ->
            db?.arbeitszeitDao()?.getArbeitszeitenInPeriod(start, end) ?: emptyFlow()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val userSettings: Flow<UserSettings?> = databaseInstance.flatMapLatest { db ->
        db?.settingsDao()?.getUserSettings() ?: emptyFlow()
    }

    suspend fun saveSettings(settings: UserSettings) {
        databaseInstance.value?.settingsDao()?.upsertSettings(settings)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getWochenInfo(jahr: Int, kw: Int): Flow<WochenberichtInfo?> {
        return databaseInstance.flatMapLatest { db ->
            db?.wochenInfoDao()?.getInfoForWeek(jahr, kw) ?: emptyFlow()
        }
    }

    suspend fun saveWochenInfo(info: WochenberichtInfo) {
        databaseInstance.value?.wochenInfoDao()?.upsertInfo(info)
    }
}
