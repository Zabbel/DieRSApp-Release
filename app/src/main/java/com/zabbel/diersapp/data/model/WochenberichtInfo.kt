package com.zabbel.diersapp.data.model

import androidx.room.Entity

@Entity(tableName = "wochenbericht_infos", primaryKeys = ["jahr", "kw"])
data class WochenberichtInfo(
    val jahr: Int,
    val kw: Int,
    val telefonMonat: String = "",
    val telefonEuro: Double = 0.0,
    val privatKm: Double = 0.0
)
