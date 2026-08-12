# Walkthrough - Daten-Schutz & GitHub Updates

Ich habe die App für sichere Datenbank-Migrationen vorbereitet und ein automatisches Update-System via GitHub implementiert.

## Änderungen

### 1. Datenbank-Sicherung (Schutz vor Datenverlust)
- **Schema-Export**: In der `app/build.gradle.kts` wurde KSP so konfiguriert, dass Room die Datenbank-Schema-Historie im Ordner `app/schemas` speichert. Dies ist die Voraussetzung für automatische Migrationen.
- **Migrationen aktiviert**: In der `AppDatabase.kt` habe ich `exportSchema = true` gesetzt und die Funktion `fallbackToDestructiveMigration()` entfernt.
- **Was das bedeutet**: Ab jetzt werden deine Daten bei Code-Änderungen an der Datenbank nicht mehr gelöscht, sondern automatisch in das neue Format überführt.

### 2. In-App Update System
- **UpdateManager**: Eine neue Klasse `UpdateManager.kt` prüft beim Start der App, ob auf deinem GitHub-Repository eine neuere Version (höherer `versionCode`) vorliegt.
- **Berechtigungen**: Im Manifest wurden `INTERNET` und `REQUEST_INSTALL_PACKAGES` hinzugefügt, damit die App Updates laden und installieren kann.
- **Ablauf**: Wenn ein Update gefunden wird, erscheint ein Dialog. Bei Klick auf "Update" wird die APK geladen und der Android-Installer gestartet.

## Nächste Schritte für dich auf GitHub

Damit das Update funktioniert, musst du folgende Dateien in dein Repository [Zabbel/DieRSApp-Release](https://github.com/Zabbel/DieRSApp-Release) hochladen:

### 1. Die Datei `update.json`
Erstelle diese Datei mit folgendem Inhalt:
```json
{
  "versionCode": 8,
  "versionName": "1.6",
  "apkUrl": "https://raw.githubusercontent.com/Zabbel/DieRSApp-Release/main/app-debug.apk",
  "releaseNotes": "Datenbank-Schutz und Update-Funktion implementiert."
}
```

### 2. Die Datei `app-debug.apk`
Lade deine aktuelle (oder die nächste) APK unter genau diesem Namen hoch.

> [!TIP]
> Sobald du diese Dateien auf GitHub hast, wird die App bei jedem Start prüfen, ob der `versionCode` auf dem Server höher ist als der lokal installierte (aktuell ist lokal `7`). Wenn du also die `update.json` auf `8` stellst, wird der Dialog in der App erscheinen.

## Verifizierung
- Der Build war erfolgreich.
- Die Berechtigungen sind korrekt gesetzt.
- Die Datenbank-Sicherung ist aktiv.
