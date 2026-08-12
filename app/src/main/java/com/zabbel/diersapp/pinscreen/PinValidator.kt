package com.zabbel.diersapp.pinscreen

object PinValidator {
    private val forbiddenPins = setOf(
        "0815", "4711", "1234", "2345", "3456", "4567", "5678", "6789"
    )

    fun isPinSecure(pin: String): Boolean {
        if (pin.length < 4) return true

        // 1. Check gegen die Blacklist
        if (forbiddenPins.contains(pin)) return false

        // 2. Check auf alle gleichen Ziffern (z.B. 1111, 9999)
        if (pin.all { it == pin[0] }) return false

        // 3. Check auf aufsteigende/absteigende Folgen
        // .all { it[1].code == it[0].code + 1 } prüft, ob das zweite Zeichen
        // im Fenster genau eins höher ist als das erste.
        val isSequence = pin.windowed(2).all { it[1].code == it[0].code + 1 } ||
                pin.windowed(2).all { it[1].code == it[0].code - 1 }

        return !isSequence
    }
}