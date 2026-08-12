package com.zabbel.diersapp.data.model


data class Bestellung(
    val typ: BestellTyp = BestellTyp.ANFORDERUNG,
    val kategorie: BestellKategorie = BestellKategorie.MATERIAL,
    val name: String = "",
    val datum: Long = System.currentTimeMillis(),
    val projekt: String = "",
    val projektNr: String = "",
    val auftragsNr: String = "",
    val ihNr: String = "",
    val posNr: String = "",
    val anforderungstext: String = "",
    val lieferantenVorschlag: String = "",
    val prioritaet: BestellPrio = BestellPrio.EILIG,
    val sofortBisDatum: String = ""
) {
    enum class BestellTyp { ANFORDERUNG, ANFRAGE }
    enum class BestellKategorie { FREMDLEISTUNG, MATERIAL, WERKZEUG, GEFAHRSTOFF }
    enum class BestellPrio { EILIG, SOFORT, NORMAL }
}
