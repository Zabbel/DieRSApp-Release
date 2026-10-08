package com.zabbel.diersapp.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zabbel.diersapp.data.model.KundenUnterschrift
import kotlinx.coroutines.flow.Flow

@Dao
interface UnterschriftDao {

    @Query("SELECT * FROM kunden_unterschriften WHERE auftragId = :auftragId AND jahr = :jahr AND kw = :kw LIMIT 1")
    fun getUnterschrift(auftragId: Long, jahr: Int, kw: Int): Flow<KundenUnterschrift?>

    @Query("SELECT * FROM kunden_unterschriften")
    fun getAllUnterschriften(): Flow<List<KundenUnterschrift>>

    @Upsert
    suspend fun upsertUnterschrift(unterschrift: KundenUnterschrift)
}
