# Implementierungsplan - GitHub Update & Datenbank-Sicherung

Wir nutzen dein Repository [Zabbel/DieRSApp-Release](https://github.com/Zabbel/DieRSApp-Release) für automatisierte Updates und sichern die bestehenden Daten gegen Verlust bei zukünftigen Änderungen ab.

## 1. Datenbank-Sicherung (Migrationen vorbereiten)

Damit bei der nächsten Änderung am Datenmodell nicht alles gelöscht wird, stellen wir von "Alles Löschen" (`fallbackToDestructiveMigration`) auf "Automatisches Mitnehmen" (`autoMigrations`) um.

### [MODIFY] [build.gradle.kts (App)](file:///home/zabbel/AndroidStudioProjects/DieRSApp/app/build.gradle.kts)
- KSP-Konfiguration hinzufügen, um den Speicherort für das Datenbank-Schema festzulegen:
  ```kotlin
  ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
  }
  ```

### [MODIFY] [AppDatabase.kt](file:///home/zabbel/AndroidStudioProjects/DieRSApp/app/src/main/java/com/zabbel/diersapp/data/AppDatabase.kt)
- `exportSchema = true` setzen.
- `.fallbackToDestructiveMigration()` entfernen.

## 2. In-App Update Mechanismus

Die App wird beim Start prüfen, ob auf GitHub eine neuere Version bereitsteht.

### [NEW] [UpdateManager.kt](file:///home/zabbel/AndroidStudioProjects/DieRSApp/app/src/main/java/com/zabbel/diersapp/util/UpdateManager.kt)
- **Logik**: Lädt `https://raw.githubusercontent.com/Zabbel/DieRSApp-Release/main/update.json`.
- **Vergleich**: Wenn `versionCode` in der JSON > lokaler `versionCode`, wird ein Update-Dialog angezeigt.
- **Download**: Lädt die APK von GitHub herunter und speichert sie im Cache.
- **Installation**: Startet den Android Package Installer.

### [MODIFY] [MainActivity.kt](file:///home/zabbel/AndroidStudioProjects/DieRSApp/app/src/main/java/com/zabbel/diersapp/MainActivity.kt)
- Aufruf von `UpdateManager.checkForUpdates(this)` in `onCreate`.

## 3. Vorbereitung auf GitHub (Deine Aufgabe)

Sobald ich den Code fertig habe, musst du zwei Dateien in dein Repository hochladen:

1.  **`app-debug.apk`**: Deine aktuellste APK.
2.  **`update.json`**: Eine Textdatei mit folgendem Inhalt:
    ```json
    {
      "versionCode": 8,
      "versionName": "1.6",
      "apkUrl": "https://raw.githubusercontent.com/Zabbel/DieRSApp-Release/main/app-debug.apk",
      "releaseNotes": "Datenbank-Schutz und Update-Funktion implementiert."
    }
    ```

---

## Verifizierungsplan

### Datenbank
- Build durchführen und prüfen, ob der Ordner `app/schemas` erstellt wird (dort landet die "Gedächtnis-Datei" der Datenbank).

### Update-Check
- Wir setzen die lokale Version kurzzeitig auf `1.5` und die Datei auf GitHub auf `1.6`.
- Die App muss beim Start den Update-Dialog anzeigen.

## Wichtige Hinweise
> [!WARNING]
> Für die Installation von APKs außerhalb des Play Stores benötigt die App die Berechtigung `REQUEST_INSTALL_PACKAGES`. Diese werde ich im Manifest hinzufügen. Der Nutzer muss dies beim ersten Update einmalig in den Android-Einstellungen erlauben.
