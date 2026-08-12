package com.zabbel.diersapp.util

import android.util.Log
import com.zabbel.diersapp.data.model.Betriebsauftrag

object OrderParser {

    data class ValidationInfo(
        val auftragsNummer: String,
        val projektNr: String,
        val positionsNummer: String
    )

    private data class ContactEntry(
        val name: String,
        val tel: String? = null,
        val email: String? = null
    )

    fun getValidationInfo(text: String): ValidationInfo {
        val cleaned = text.replace("O", "0").replace("I", "1").replace("i", "1")
        return ValidationInfo(
            auftragsNummer = findMatch(cleaned, """Au?f?t?rag\s*[:|]?\s*([0\d]{8})""") ?: "",
            projektNr = findMatch(cleaned, """#\s*([0\d]{5})""")
                ?: findMatch(cleaned, """Projekt\.?\s*[:|]?\s*([A-Z0-9.\-/]+)""")
                ?: "",
            positionsNummer = findMatch(cleaned, """Pos\.?[\s.,-]*Nr\.?\s*[:|]?\s*([0\d]+)""") ?: ""
        )
    }

    fun parseRecognizedText(text: String): Betriebsauftrag {
        Log.d("RS_OCR", "Full Recognized Text:\n$text")

        val cleanedText = text
            .replace(Regex("""(?<=\d)O|O(?=\d)"""), "0")
            .replace(Regex("""(?<=\d)I|I(?=\d)"""), "1")

        val lines = cleanedText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 1. Stammdaten
        val projektLine = lines.find { it.contains("Projekt", true) }
        val schiff = projektLine?.substringAfter("\"")?.substringBefore("\"") ?: ""

        val projektNr = (
                findMatch(cleanedText, """#\s*([0\dO]{5})""")
                    ?: findMatch(cleanedText, """Projekt\.?\s*[:|]?\s*([A-Z0-9.\-/]+)""")
                    ?: ""
                ).replace("O", "0")

        val auftragsNummer = (
                findMatch(cleanedText, """Au?f?t?rag\s*[:|]?\s*([0\dO]{8})""")
                    ?: "00000000"
                ).replace("O", "0")

        val positionsNummer = (
                findMatch(cleanedText, """Pos\.?[\s.,-]*Nr\.?\s*[:|]?\s*([0\dO]+)""")
                    ?: "10"
                ).replace("O", "0")

        // 2. Kunde & Titel
        val kundeIdx = lines.indexOfFirst { it.contains("Kunde", true) }
        val kundeRaw = if (kundeIdx != -1) {
            lines[kundeIdx]
                .replace(Regex("""^.*Kunde\s*[:|]\s*""", RegexOption.IGNORE_CASE), "")
                .substringBefore("|")
                .trim()
        } else {
            ""
        }

        val combinedTitel = extractTitel(lines)

        // 3. Kontakte
        val finalContacts = extractContacts(cleanedText)

        val rawTermin = findMatch(cleanedText, """(?:Termin|Durchführungszeitraum|Durchfüh?rung(?:\s+der\s+Arbeiten)?|Zeitraum|Ausführungszeitraum|Geplanter Zeitraum|Fertigstellungstermin)(?:\s+bis)?[\s\n:|]*((?:(?:Vom|ab|bis)[\s:]+)?(?:[0\dO]{1,2}\.[0\dO]{1,2}(?:\.[0\dO]{2,4})?\.?\s*(?:bis|[-–])\s*[0\dO]{1,2}\.[0\dO]{1,2}\.[0\dO]{2,4}\.?|[0\dO]{1,2}\.[0\dO]{1,2}\.[0\dO]{2,4}\.?))""")

        return Betriebsauftrag(
            auftragsNummer = auftragsNummer,
            positionsNummer = positionsNummer,
            kunde = cleanKunde(kundeRaw, schiff),
            projektNr = projektNr,
            titelKurz = combinedTitel.ifBlank { "Neuer Auftrag" },
            beschreibungLang = extractLeistung(cleanedText) ?: "",
            meldungsNummer = findMatch(cleanedText, """Meldungsnummer:\s*([0\dO]{12})""")?.replace("O", "0"),
            ihNummer = (findMatch(cleanedText, """A[rtH][\s.,-]*N[reE]\.?\s*[:|]?\s*([\dO]{10}\s*[\dO]{4})""")
                ?: findMatch(cleanedText, """([\dO]{10}\s*[\dO]{4})"""))?.replace("O", "0") ?: "Ohne IH-Nr",
            liegeplatz = findMatch(cleanedText, """Liegeplatz\s*[:|]\s*([^|\n]+)""")?.trim() ?: "",
            durchfuehrungsZeitraum = rawTermin?.replace("O", "0") ?: "",
            abrechnungsArt = if (cleanedText.contains("Aufwand", true)) "nach Aufwand" else "Festpreis",
            ansprechpartnerName = finalContacts.joinToString("\n") { it.name },
            ansprechpartnerTelefon = finalContacts.joinToString("\n") { it.tel ?: "" },
            ansprechpartnerEmail = finalContacts.joinToString("\n") { it.email ?: "" },
            guesi = findMatch(cleanedText, """Güsi\s*[:|]?\s*([^|\n\s]+)""")?.trim() ?: "",
            kundenBestellText = findMatch(cleanedText, """K-Bestelltext\s*[:|]?\s*([^|\n]+)""")
                ?: findMatch(cleanedText, """K-\s*[:|]?\s*([^\n|]+)""")
                ?: findMatch(cleanedText, """Bestelltext\s*[:|]?\s*([^|\n]+)""")
        )
    }

    private fun extractContacts(text: String): List<ContactEntry> {
        val rawBlock = if (text.contains("Ansprechpartner", ignoreCase = true)) {
            text.substringAfter("Ansprechpartner")
        } else {
            text
        }
            .substringBefore("Erledigt:")
            .substringBefore("Datum / Unterschrift")
            .trim()

        Log.d("RS_OCR", "CONTACT RAW BLOCK: $rawBlock")

        if (rawBlock.isBlank()) return emptyList()

        val contactZone = rawBlock
            .substringBefore("Technische Daten zum Equipment:", rawBlock)
            .substringBefore("Technische Daten:", rawBlock)
            .substringBefore("Materialnummer:", rawBlock)
            .substringBefore("Serialnummer:", rawBlock)
            .trim()

        val block = contactZone
            .replace(
                Regex("""\[\s*([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})\s*]\(mailto:[^)]+\)"""),
                "$1"
            )
            .replace(Regex("""(?i)\bMai[lI]\s*:\s*"""), " Mail: ")
            .replace(Regex("""(?i)\bMail\s*:\s*"""), " Mail: ")
            .replace(Regex("""(?i)\bTel\s*\.?\s*:\s*"""), " Tel: ")
            .replace(Regex("""(?i)\bApp\s*\.?\s*:?"""), " Tel: ")
            .replace(Regex("""[()]"""), " ")
            .replace("\n", " ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        Log.d("RS_OCR", "CONTACT NORMALIZED BLOCK: $block")

        val telRegex = Regex("""(?i)(?:Tel\.?\s*:\s*)?(\+?\d[\d\s/-]{6,}\d)""")
        val mailLabeledRegex = Regex("""(?i)\bMail\s*:\s*([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})""")
        val mailLooseRegex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

        val startPatterns = listOf(
            "poc" to Regex(
                """(?i)\bPOC(?:\s+[A-Za-zÄÖÜäöüß]+)*:\s*(?:Hauptmann|Hr\.|Fr\.)?\s*[A-ZÄÖÜ][A-Za-zÄÖÜäöüß-]+(?=\s*(?:Tel|Erreichbarkeit|Ansprechpart|$))"""
            ),
            "person" to Regex(
                """(?i)\b(?:Fr\.|Hr\.|TRAR|TRHS|StArb|Hauptmann)\s+[A-ZÄÖÜ][A-Za-zÄÖÜäöüß-]+"""
            ),
            "direct_name" to Regex(
                """(?i)^[:\s]*([A-ZÄÖÜ][A-Za-zÄÖÜäöüß-]+\s+[A-ZÄÖÜ][A-Za-zÄÖÜäöüß-]+)"""
            ),
            "reachability" to Regex(
                """(?i)\bErreichbarkeit(?:\s+[A-Za-z0-9ÄÖÜäöüß-]+)+"""
            ),
            "workshop" to Regex(
                """(?i)\b[A-Za-zÄÖÜäöüß-]*werkstatt\b"""
            )
        )

        data class RawHit(
            val type: String,
            val start: Int,
            val end: Int,
            val label: String
        )

        val rawHits = startPatterns
            .flatMap { (type, regex) ->
                regex.findAll(block).map {
                    RawHit(
                        type = type,
                        start = it.range.first,
                        end = it.range.last + 1,
                        label = it.value.trim()
                    )
                }.toList()
            }
            .sortedBy { it.start }

        val hits = rawHits.filter { hit ->
            !(hit.type == "person" && rawHits.any { other ->
                other.type == "poc" &&
                        hit.start >= other.start &&
                        hit.start < other.end
            })
        }

        Log.d("RS_OCR", "CONTACT STARTS: ${hits.map { "${it.type}:${it.label}" }}")

        if (hits.isEmpty()) return emptyList()

        fun cleanupName(raw: String): String {
            val cleaned = raw
                .replace(Regex("""(?i)^POC(?:\s+[A-Za-zÄÖÜäöüß0-9-]+)*:\s*"""), "")
                .replace(Regex("""(?i)^Ansprechpartner(?:\s+MArs(?:\s+Werkstatt)?)?:\s*"""), "")
                .replace(Regex("""(?i)^Werkstatt:\s*"""), "")
                .replace(Regex("""(?i)\b(TRAR|TRHS|StArb|Hr\.|Fr\.)\s+[A-ZÄÖÜ]{1,2}\s+([A-ZÄÖÜ][A-Za-zÄÖÜäöüß-]+)\b"""), "$1 $2")
                .replace(Regex("""\s{2,}"""), " ")
                .trim(' ', ',', ';', ':')

            val blocked = listOf(
                "Materialnummer",
                "Serialnummer",
                "Raum",
                "Liegeplatz",
                "Durchführungszeitraum"
            )

            return if (blocked.any { cleaned.startsWith(it, ignoreCase = true) }) "" else cleaned
        }

        fun cleanupReachability(chunk: String): String {
            return chunk
                .replace(Regex("""(?i)\s+Tel\s*[:.].*$"""), "")
                .replace(Regex("""(?i)\s+Mail\s*[:.].*$"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim(' ', ',', ';', ':')
        }

        fun extractTel(chunk: String): String? {
            return telRegex.find(chunk)
                ?.groupValues?.getOrNull(1)
                ?.replace(Regex("""[)\],;]+$"""), "")
                ?.replace(Regex("""\s{2,}"""), " ")
                ?.trim()
        }

        fun extractMail(chunk: String): String? {
            return mailLabeledRegex.find(chunk)
                ?.groupValues?.getOrNull(1)
                ?.trim()
                ?: mailLooseRegex.find(chunk)?.value?.trim()
        }

        val contacts = mutableListOf<ContactEntry>()

        for (i in hits.indices) {
            val hit = hits[i]
            val nextStart = if (i + 1 < hits.size) hits[i + 1].start else block.length
            val chunk = block.substring(hit.start, nextStart).trim()

            val name = when (hit.type) {
                "reachability" -> cleanupReachability(chunk)
                else -> cleanupName(hit.label)
            }
            val tel = extractTel(chunk)
            val email = extractMail(chunk)

            Log.d("RS_OCR", "CONTACT CHUNK[$i]: $chunk")
            Log.d("RS_OCR", "CONTACT PARSED[$i]: type=${hit.type} | name=$name | tel=$tel | email=$email")

            if (name.isNotBlank()) {
                contacts.add(ContactEntry(name = name, tel = tel, email = email))
            }
        }

        val normalized = contacts
            .map {
                it.copy(
                    name = it.name
                        .replace(Regex("""\s{2,}"""), " ")
                        .trim(' ', ',', ';', ':'),
                    tel = normalizePhoneNumber(it.tel),
                    email = it.email?.trim()
                )
            }
            .filter {
                it.name.isNotBlank() }

        val merged = mergeWorkshopPersonContacts(normalized)
        val filtered = merged.filter {
            it.name.isNotBlank() &&
                    (it.tel != null || it.email != null || it.name.startsWith("Erreichbarkeit", true))
        }

        val prioritized = filtered.sortedWith(
            compareBy<ContactEntry> {
                when {
                    it.name.startsWith("MArs,", true) -> 0
                    it.name.startsWith("Fr.", true) -> 1
                    it.name.startsWith("Hr.", true) -> 1
                    it.name.startsWith("Hauptmann", true) -> 1
                    it.name.startsWith("TRAR", true) -> 1
                    it.name.startsWith("TRHS", true) -> 1
                    it.name.startsWith("StArb", true) -> 1
                    it.name.startsWith("Erreichbarkeit", true) -> 2
                    it.name.contains("werkstatt", true) -> 3
                    else -> 4
                }
            }.thenBy { it.name.lowercase() }
        )

        val result = prioritized.distinctBy { "${it.name}|${it.tel}|${it.email}" }

        Log.d("RS_OCR", "CONTACTS FINAL: $result")

        return result
    }

    private fun findMatch(text: String, pattern: String): String? {
        return Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.let {
            if (it.groupValues.size > 1) it.groupValues[1].trim() else it.value.trim()
        }
    }

    private fun cleanKunde(kunde: String, schiff: String): String {
        val clean = kunde.replace(Regex("""^[:\s]*\d{4,5}\s+"""), "").trim()
        return if (schiff.isNotEmpty() && !clean.contains(schiff, true)) {
            "🚢 $schiff | $clean"
        } else {
            clean
        }
    }

    private fun normalizePhoneNumber(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        var value = raw.trim()

        value = when {
            value.startsWith("+49") -> "0" + value.removePrefix("+49")
            value.startsWith("0049") -> "0" + value.removePrefix("0049")
            value.startsWith("49") && value.length > 6 -> "0" + value.removePrefix("49")
            else -> value
        }

        value = value.replace(Regex("""\D"""), "")

        return value.ifBlank { null }
    }

    fun extractLeistung(text: String, isFolgeSeite: Boolean = false): String? {
        val lines = text.lines().map { it.trim() }
        var startIdx = lines.indexOfFirst { it.contains("Kostenstelle", true) }

        if (startIdx == -1 && isFolgeSeite) {
            val firstDivider = lines.indexOfFirst { it.contains("|") }
            startIdx = if (firstDivider != -1) firstDivider else 10.coerceAtMost(lines.size - 1)
        }

        if (startIdx == -1 || startIdx >= lines.size) return null

        val stopKeywords = listOf("Datum", "Seite", "Erledigt:")

        return lines
            .drop(startIdx + 1)
            .takeWhile { line -> stopKeywords.none { line.contains(it, true) } }
            .joinToString("\n") { if (it.contains("|")) it.substringAfterLast("|").trim() else it }
            .trim()
    }

    private fun extractTitel(lines: List<String>): String {
        val artBezRegex = Regex("""(?i)\bArt[\s.,-]*Bez\.?\b""")
        val mengeRegex = Regex("""(?i)\bMenge\b""")
        val stopRegex = Regex("""(?i)\b(Einheit|Art[\s.,-]*Nr|Zg[\s.,-]*Nr|Lieferanschrift|Bestelltext)\b""")

        fun cleanText(value: String): String {
            return value
                .replace(Regex("""^[:|\s]+"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()
        }

        fun normalizeArtifacts(value: String): String {
            return value
                .replace(Regex("""^[Xx]\s*\|\s*"""), "")   // "X | ..."
                .replace(Regex("""^\d+\s*\|\s*"""), "")     // "1 | ..."
                .replace(Regex("""^\d+\s+"""), "")          // "1 Sicherungsautomaten"
                .trim()
        }

        fun extractArtBezPart(line: String): String {
            val idx = artBezRegex.find(line)?.range?.last?.plus(1) ?: return ""
            return cleanText(line.substring(idx))
        }

        fun extractMengePart(line: String): String {
            val tail = line.substringAfter("Menge", line)
                .replace(Regex("""(?i)^\s*[:|]?\s*"""), "")
                .trim()

            val candidate = when {
                tail.contains("|") -> tail.substringAfterLast("|").trim()
                else -> tail
            }

            return normalizeArtifacts(cleanText(candidate))
        }

        val artBezIdx = lines.indexOfFirst { artBezRegex.containsMatchIn(it) }
        if (artBezIdx == -1) return ""

        val fragmente = mutableListOf<String>()

        val firstPart = extractArtBezPart(lines[artBezIdx])
        if (firstPart.isNotBlank()) {
            fragmente.add(firstPart)
        }

        for (i in (artBezIdx + 1)..minOf(artBezIdx + 3, lines.lastIndex)) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            if (stopRegex.containsMatchIn(line)) break

            val nextPart = when {
                mengeRegex.containsMatchIn(line) -> extractMengePart(line)

                !artBezRegex.containsMatchIn(line) &&
                        !stopRegex.containsMatchIn(line) &&
                        line.length in 3..80 &&
                        !line.contains("Kunde", true) &&
                        !line.contains("Auftrag", true) &&
                        !line.contains("Pos.-Nr", true) -> cleanText(line)

                else -> ""
            }

            if (nextPart.isNotBlank()) {
                fragmente.add(nextPart)
            }
        }

        return fragmente
            .map { it.trim(' ', '|', ':', ';', ',', '.') }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }
    private fun mergeWorkshopPersonContacts(contacts: List<ContactEntry>): List<ContactEntry> {
        if (contacts.isEmpty()) return contacts

        val result = mutableListOf<ContactEntry>()
        var i = 0

        while (i < contacts.size) {
            val current = contacts[i]
            val next = contacts.getOrNull(i + 1)

            val isPersonOnly =
                current.name.matches(Regex("""(?i)^(Fr\.|Hr\.)\s+.+""")) &&
                        current.tel.isNullOrBlank() &&
                        current.email.isNullOrBlank()

            val isWorkshopWithData =
                next != null &&
                        next.name.contains("werkstatt", true) &&
                        (!next.tel.isNullOrBlank() || !next.email.isNullOrBlank())

            if (isPersonOnly && isWorkshopWithData) {
                result.add(
                    ContactEntry(
                        name = "MArs, ${next.name}, ${current.name}"
                            .replace(Regex("""\s{2,}"""), " ")
                            .trim(' ', ',', ';', ':'),
                        tel = next.tel,
                        email = next.email
                    )
                )
                i += 2
            } else {
                result.add(current)
                i++
            }
        }

        return result
    }
}
