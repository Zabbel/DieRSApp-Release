package com.zabbel.diersapp.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zabbel.diersapp.data.model.UserSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 0")
    fun getUserSettings(): Flow<UserSettings?>

    @Upsert
    suspend fun upsertSettings(settings: UserSettings)
}