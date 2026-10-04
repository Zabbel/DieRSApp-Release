package com.zabbel.diersapp.data.dao

import androidx.room.*
import com.zabbel.diersapp.data.model.Betriebsauftrag
import kotlinx.coroutines.flow.Flow

@Dao
interface AuftragDao {
    @Query("SELECT * FROM betriebsauftraege WHERE kunde != 'INTERN' ORDER BY id DESC")
    fun getAllAuftraege(): Flow<List<Betriebsauftrag>>

    @Query("SELECT * FROM betriebsauftraege ORDER BY id DESC")
    fun getAllAuftraegeIncludingInternal(): Flow<List<Betriebsauftrag>>

    @Upsert
    suspend fun insertAuftrag(auftrag: Betriebsauftrag): Long

    @Delete
    suspend fun deleteAuftrag(auftrag: Betriebsauftrag)

    @Query("SELECT * FROM betriebsauftraege WHERE auftragsNummer = :nr AND positionsNummer = :pos LIMIT 1")
    suspend fun findAuftragByNrAndPos(nr: String, pos: String): Betriebsauftrag?

    // NEU: Wird benötigt, um System-Aufträge (die keine Nummer haben) sauber zu trennen
    @Query("SELECT * FROM betriebsauftraege WHERE kunde = 'INTERN' AND titelKurz = :titel LIMIT 1")
    suspend fun findSystemAuftrag(titel: String): Betriebsauftrag?
}