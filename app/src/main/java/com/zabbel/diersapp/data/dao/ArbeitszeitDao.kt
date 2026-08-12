package com.zabbel.diersapp.data.dao

import androidx.room.*
import com.zabbel.diersapp.data.model.Arbeitszeit
import kotlinx.coroutines.flow.Flow

@Dao
interface ArbeitszeitDao {
    @Upsert
    suspend fun upsertArbeitszeit(arbeitszeit: Arbeitszeit)

    @Query("SELECT * FROM arbeitszeiten WHERE auftragId = :auftragId ORDER BY datum DESC")
    fun getArbeitszeitenForAuftrag(auftragId: Long): Flow<List<Arbeitszeit>>

    @Query("SELECT * FROM arbeitszeiten WHERE datum >= :start AND datum <= :end ORDER BY datum ASC")
    fun getArbeitszeitenInPeriod(start: Long, end: Long): Flow<List<Arbeitszeit>>

    @Delete
    suspend fun deleteArbeitszeit(arbeitszeit: Arbeitszeit)
}
