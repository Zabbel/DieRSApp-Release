package com.zabbel.diersapp.data.model

import androidx.room.Entity

@Entity(tableName = "kunden_unterschriften", primaryKeys = ["auftragId", "jahr", "kw"])
data class KundenUnterschrift(
    val auftragId: Long,
    val jahr: Int,
    val kw: Int,
    val signatureBase64: String
)
