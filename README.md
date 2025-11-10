# NFC-Zugangskontrollsystem für Android

## 1. Projektübersicht

Dieses Projekt ist eine voll funktionsfähige Android-Anwendung, die ein modernes Zugangskontrollsystem unter Verwendung von NFC (Near Field Communication) demonstriert. Die App verwandelt zwei Smartphones in die Komponenten eines sicheren Systems: ein Telefon fungiert als **Mitarbeiterausweis (Schlüssel)** und das andere als **Türlesegerät (Schloss)**.

Das Hauptziel war es, ein System zu schaffen, das nicht nur technisch robust ist, sondern auch eine klare und intuitive Benutzererfahrung bietet, einschließlich einer Zwei-Wege-Bestätigung für erfolgreiche Scans.

## 2. Kernkonzept und Funktionen

### Rollenbasierte Architektur
Beim Start der App wählt der Benutzer eine von zwei Rollen:
- **Als Mitarbeiterausweis verwenden**: Das Telefon simuliert eine digitale ID-Karte mit den Daten eines Mitarbeiters.
- **Als Türleser verwenden**: Das Telefon agiert als Terminal, das auf eine Mitarbeiterkarte wartet, um den Zugang zu gewähren.

### Hauptfunktionen
- **Dynamische Rollenauswahl**: Benutzer können die Rolle ihres Geräts jederzeit neu bestimmen.
- **Kartensimulation (HCE)**: Nutzt **Host-based Card Emulation**, um eine NFC-Karte sicher zu emulieren, ohne auf spezielle Hardware angewiesen zu sein.
- **Lesemodus (Reader Mode)**: Implementiert einen effizienten Modus zum Scannen von emulierten Karten in der Nähe.
- **Sicheres Kommunikationsprotokoll**: Verwendet ein benutzerdefiniertes APDU-Befehlsprotokoll, um die Kommunikation zwischen den Geräten zu steuern.
- **Zwei-Wege-Bestätigung**: Nach einem erfolgreichen Scan erhalten **beide** Geräte eine visuelle Bestätigung:
    - Das **Lesegerät** zeigt an: „Zugang gewährt für [Mitarbeitername]“.
    - Das **Mitarbeiter-Handy** zeigt einen bildschirmfüllenden grünen Erfolgsbildschirm an: „✓ ANMELDUNG ERFOLGREICH ✓“.
- **Professionelles UI-Design**: Die Benutzeroberfläche ist sauber, modern und auf die jeweilige Rolle zugeschnitten, um die Benutzerfreundlichkeit zu maximieren.

## 3. Wie man die App benutzt

1.  **Installation**: Installieren Sie die generierte APK-Datei auf zwei NFC-fähigen Android-Smartphones.
2.  **NFC aktivieren**: Stellen Sie sicher, dass NFC in den Einstellungen beider Geräte aktiviert ist.
3.  **App starten**: Öffnen Sie die Anwendung auf beiden Telefonen.
4.  **Rollen zuweisen**:
    - Wählen Sie auf einem Telefon die Option **„Als Mitarbeiterausweis verwenden“**. Der Bildschirm zeigt nun an, dass die Karte zur Übertragung bereit ist.
    - Wählen Sie auf dem anderen Telefon **„Als Türleser verwenden“**. Der Bildschirm zeigt an, dass er auf das Scannen einer Karte wartet.
5.  **Scan durchführen**: Halten Sie die Rückseite des „Mitarbeiter“-Telefons an die Rückseite des „Leser“-Telefons. Die genaue Position der NFC-Antenne variiert je nach Gerätemodell.
6.  **Erfolg bestätigen**: Beobachten Sie die Bildschirme beider Geräte. Sie sollten gleichzeitig die jeweiligen Erfolgsbestätigungen anzeigen.

## 4. Technische Implementierung

Die Anwendung kombiniert mehrere Kerntechnologien von Android, um das System zu realisieren.

### Host-based Card Emulation (HCE)
Der `MyHostApduService.kt` ist ein `HostApduService`, der im Hintergrund läuft, wenn die App als Mitarbeiterausweis fungiert.
- Er wird durch die Deklaration im `AndroidManifest.xml` und die Konfigurationsdatei `res/xml/apduservice.xml` (die die eindeutige **AID** - Application ID - definiert) aktiviert.
- Dieser Dienst reagiert auf **APDU-Befehle** (Application Protocol Data Unit), die vom Lesegerät gesendet werden, und antwortet entsprechend – zum Beispiel, indem er die Benutzerdaten sendet.

### Reader Mode
Die `MainActivity.kt` verwendet `nfcAdapter.enableReaderMode()`, wenn sie als Türleser konfiguriert ist.
- Dieser Modus gibt der App die Priorität beim Verarbeiten von NFC-Tags und ermöglicht es ihr, direkt mit dem `IsoDep`-Protokoll zu kommunizieren, das für die APDU-Kommunikation erforderlich ist.
- Die Methode `onTagDiscovered()` wird aufgerufen, wenn ein Tag (in diesem Fall das andere Telefon) erkannt wird, und startet den Kommunikationsprozess.

### APDU-Kommunikationsprotokoll
Der Dialog zwischen den Geräten folgt einer klaren Abfolge von Befehlen:
1.  **SELECT AID**: Das Lesegerät sendet die vordefinierte AID, um sicherzustellen, dass es mit der richtigen Anwendung kommuniziert. Der Kartendienst antwortet mit einem Erfolgscode (`9000`).
2.  **GET_USER_DATA**: Das Lesegerät fordert die Mitarbeiterdaten an. Der Kartendienst sendet die serialisierten Daten (z. B. „ID:12345;Name:Jane Doe“) zurück, gefolgt von einem Erfolgscode.
3.  **TRANSACTION_OK**: Nachdem das Lesegerät die Daten erfolgreich verarbeitet hat, sendet es einen letzten Bestätigungsbefehl an die Karte.

### Zwei-Wege-Bestätigung mit BroadcastReceiver
Die Erfolgsbestätigung auf dem Mitarbeiter-Handy wird durch einen `BroadcastReceiver` realisiert:
- Wenn der `MyHostApduService` den `TRANSACTION_OK`-Befehl vom Lesegerät empfängt, sendet er einen lokalen `Intent Broadcast` mit einer benutzerdefinierten Aktion (`ACTION_SUCCESSFUL_SIGN_IN`).
- Die `MainActivity` registriert einen `BroadcastReceiver`, der auf diese Aktion wartet. Wenn der Broadcast empfangen wird, aktualisiert die Activity die Benutzeroberfläche und zeigt den grünen Erfolgsbildschirm an.

## 5. Projektstruktur

-   `/app/src/main/java/com/example/nfcauth/`
    -   `MainActivity.kt`: Haupt-Activity, die die UI verwaltet und als `ReaderCallback` fungiert.
    -   `MyHostApduService.kt`: Implementiert den HCE-Dienst zur Emulation der Karte.
    -   `Person.kt`: Eine einfache Datenklasse zur Speicherung der Mitarbeiterinformationen.
-   `/app/src/main/res/layout/activity_main.xml`: Definiert das gesamte Layout, einschließlich der verschiedenen Ansichten für jede Rolle und jeden Zustand.
-   `/app/src/main/res/xml/apduservice.xml`: Konfigurationsdatei für den HCE-Dienst, die die Anwendungs-ID (AID) festlegt.
-   `/app/src/main/res/drawable/`: Enthält Bildressourcen wie das Firmenlogo und Scan-Symbole.
-   `/app/src/main/AndroidManifest.xml`: Deklariert die erforderlichen NFC-Berechtigungen sowie die `MainActivity` und den `MyHostApduService`.
