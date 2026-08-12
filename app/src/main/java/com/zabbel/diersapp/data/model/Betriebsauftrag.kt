package com.zabbel.diersapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "betriebsauftraege")
data class Betriebsauftrag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val auftragsNummer: String,
    val positionsNummer: String,
    val kunde: String,
    val projektNr: String? = null,
    val titelKurz: String,
    val beschreibungLang: String,
    val meldungsNummer: String? = null,
    val ihNummer: String? = null,
    val liegeplatz: String? = null,
    val durchfuehrungsZeitraum: String? = null,
    val abrechnungsArt: String, // "Aufwand" oder "Angebot"
    val ansprechpartnerName: String? = null,
    val ansprechpartnerTelefon: String? = null,
    val ansprechpartnerEmail: String? = null,
    val guesi: String? = null,
    val kundenBestellText: String? = null
    // Später erweiterbar um Zeiterfassung, Material etc.
)
