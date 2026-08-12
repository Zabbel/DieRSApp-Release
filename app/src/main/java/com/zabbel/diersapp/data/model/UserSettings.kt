package com.zabbel.diersapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey val id: Int = 0, // Immer 0, damit es nur einen Eintrag gibt
    val vorname: String = "",
    val nachname: String = "",
    val personalnummer: String = "",
    val signatureBase64: String? = null,
    val wochenstunden: Double = 37.0
)
