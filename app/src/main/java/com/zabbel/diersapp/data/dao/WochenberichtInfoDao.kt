package com.zabbel.diersapp.data.dao

import androidx.room.*
import com.zabbel.diersapp.data.model.WochenberichtInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface WochenberichtInfoDao {
    @Query("SELECT * FROM wochenbericht_infos WHERE jahr = :jahr AND kw = :kw LIMIT 1")
    fun getInfoForWeek(jahr: Int, kw: Int): Flow<WochenberichtInfo?>

    @Upsert
    suspend fun upsertInfo(info: WochenberichtInfo)
}
