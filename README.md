# Matrix Camera

Android-App für eine ausdrücklich freigegebene Kameraquelle.

## Funktionen
- Ein Android-Handy kann seine Rückkamera als Live-MJPEG-Quelle bereitstellen.
- Ein zweites Handy im selben WLAN kann das Livebild anzeigen.
- Kamera und Viewer werden sichtbar in der App gestartet.
- Kamera-Berechtigung wird über Android abgefragt.

## Nutzung
1. Projekt mit Android Studio öffnen.
2. APK bauen: `./gradlew assembleDebug`.
3. App auf Handy A installieren und **KAMERA START** drücken.
4. Die angezeigte lokale IP-Adresse notieren.
5. Handy B ins gleiche WLAN bringen, IP-Adresse eintragen und **VIEWER START** drücken.

Der aktuelle Stream ist absichtlich für das lokale Netzwerk ausgelegt. Für Zugriff über das Internet sollte später eine abgesicherte Relay-/WebRTC-Verbindung mit Geräte-Kopplung ergänzt werden; kein heimlicher Kamerazugriff.
