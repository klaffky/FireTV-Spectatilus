# FireTV Spectatilus

Ein Jellyfin-Client für den **Amazon Fire TV Stick**, aufgebaut wie die iOS/tvOS-App **Spectati**:
großes Titelbild oben, darunter Reihen wie „Weiter ansehen“, „Als Nächstes“ und „Neu in …“, Detailseiten mit Staffeln, Episoden und Besetzung, dazu ein Player, der Dolby Vision, HDR10, Dolby Digital+ und Atmos direkt durchreicht, wenn der Fire TV das kann.

Über **Tailscale** erreichst du deine Mediathek auch von unterwegs, zum Beispiel im Hotel, bei Freunden oder im Ferienhaus. Portfreigaben am Router brauchst du dafür nicht.

**Download-Link für die Downloader-App (immer die neueste Version):**

```
https://github.com/klaffky/spreeblick-website/releases/latest/download/FireTV-Spectatilus.apk
```

> Nachdem das Repository in `FireTV-Spectatilus` umbenannt wurde (siehe [unten](#repository-umbenennen)), lautet der Link
> `https://github.com/klaffky/FireTV-Spectatilus/releases/latest/download/FireTV-Spectatilus.apk`.
> Der alte Link funktioniert weiter, weil GitHub automatisch weiterleitet.

---

## Inhalt

1. [Funktionen](#funktionen)
2. [Voraussetzungen](#voraussetzungen)
3. [Schritt 1: Tailscale auf dem Jellyfin-Server](#schritt-1-tailscale-auf-dem-jellyfin-server)
4. [Schritt 2: Jellyfin für Tailscale vorbereiten](#schritt-2-jellyfin-für-tailscale-vorbereiten)
5. [Schritt 3: Fire TV für fremde Apps freischalten](#schritt-3-fire-tv-für-fremde-apps-freischalten)
6. [Schritt 4: App mit dem Downloader installieren](#schritt-4-app-mit-dem-downloader-installieren)
7. [Schritt 5: Tailscale auf dem Fire TV](#schritt-5-tailscale-auf-dem-fire-tv)
8. [Schritt 6: Spectatilus einrichten](#schritt-6-spectatilus-einrichten)
9. [Bedienung mit der Fernbedienung](#bedienung-mit-der-fernbedienung)
10. [Unterwegs: so funktioniert's im Alltag](#unterwegs-so-funktionierts-im-alltag)
11. [Updates](#updates)
12. [Fehlerbehebung](#fehlerbehebung)
13. [Repository umbenennen](#repository-umbenennen)
14. [Für Entwickler](#für-entwickler)

---

## Funktionen

| Bereich | Was die App kann |
|---|---|
| **Start** | Großes Titelbild (wechselt automatisch, mit ◀ ▶ blättern), „Weiter ansehen“, „Als Nächstes“, „Neu in Filme/Serien/…“, Bibliotheken |
| **Filme / Serien** | Poster-Raster mit Sortierung (A–Z, neu hinzugefügt, Jahr, Bewertung) und dem Filter „Nur ungesehen“ |
| **Suche** | Filme, Serien, Episoden und Sammlungen, auch per Spracheingabe über die Mikrofon-Taste |
| **Details** | Logo, Bewertung, Laufzeit, Qualitäts-Abzeichen (4K, Dolby Vision, HDR10, Atmos, 5.1 …), Staffeln, Episoden, Besetzung, ähnliche Titel, Gesehen/Favorit |
| **Player** | Direktwiedergabe oder Umwandlung durch den Server (HLS), Fortsetzen an der letzten Stelle, Audio- und Untertitelwahl, nächste Folge automatisch, Fortschritt wird mit Jellyfin synchronisiert |
| **Verbindung** | Heimnetz-Adresse wird zuerst probiert, sonst die Tailscale-Adresse. Die App zeigt an, worüber sie gerade verbunden ist |
| **Anmeldung** | Profil-Auswahl („Wer schaut?“), Passwort oder **Quick Connect**: Code am Handy bestätigen, ohne zu tippen |
| **Qualität** | Bitrate einstellbar (Original bis 1,5 Mbit/s), damit es auch über langsame Leitungen unterwegs flüssig läuft |

## Voraussetzungen

- **Fire TV mit Fire OS**: Fire TV Stick (ab 2. Generation), Fire TV Stick Lite, 4K, 4K Max, Fire TV Cube oder ein Fire-TV-Fernseher.
  ⚠️ Geräte mit **Vega OS** (z. B. *Fire TV Stick 4K Select*, ab 2025) können **keine** APK-Dateien installieren. Du findest das Betriebssystem unter *Einstellungen → Mein Fire TV → Info*.
- Ein laufender **Jellyfin-Server** (ab Version 10.8, empfohlen 10.9 oder neuer).
- Ein kostenloses **Tailscale-Konto** ([tailscale.com](https://tailscale.com)), z. B. per Google-, Microsoft- oder Apple-Anmeldung.

---

## Schritt 1: Tailscale auf dem Jellyfin-Server

Tailscale baut ein privates Netz zwischen deinen Geräten auf. Jedes Gerät bekommt eine feste Adresse der Form `100.x.y.z`.

**Windows / macOS**
1. [tailscale.com/download](https://tailscale.com/download) öffnen, installieren und mit deinem Konto anmelden.
2. Auf das Tailscale-Symbol in der Taskleiste bzw. Menüleiste klicken: Dort steht die IP-Adresse dieses Rechners (`100.…`).

**Linux (Server, Raspberry Pi)**
```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
tailscale ip -4        # zeigt die 100.x.y.z-Adresse
```

**Synology / QNAP / Unraid / TrueNAS**: Tailscale gibt es im jeweiligen Paket- bzw. App-Zentrum. Danach anmelden und die IP ablesen.

**Docker**: Läuft Jellyfin in Docker, installiere Tailscale am einfachsten **auf dem Host-Rechner**. Jellyfin ist dann unter `http://<Tailscale-IP-des-Hosts>:8096` erreichbar.

**Tipp: MagicDNS.** In der [Tailscale-Admin-Konsole](https://login.tailscale.com/admin/machines) bekommt jedes Gerät auch einen Namen, z. B. `jellyfin.tail1234.ts.net`. Den kannst du statt der IP eintragen.

> Prüfen: Öffne am Handy (mit aktivem Tailscale) im Browser `http://100.x.y.z:8096`. Erscheint Jellyfin, ist der Server bereit.

## Schritt 2: Jellyfin für Tailscale vorbereiten

Im Jellyfin-Web-Dashboard (*Dashboard → Netzwerk*):

1. **„Fernverbindungen zu diesem Server zulassen“** muss **an** sein (Standard).
2. Empfohlen: Unter **„LAN-Netzwerke“** zusätzlich `100.64.0.0/10` eintragen und speichern.
   Dann behandelt Jellyfin Tailscale-Geräte wie das Heimnetz, und die Bitrate-Begrenzung für „Internet“-Zugriffe greift nicht ungewollt.
3. Optional für die bequeme Anmeldung: *Dashboard → Allgemein → **Quick Connect aktivieren***.

## Schritt 3: Fire TV für fremde Apps freischalten

1. Auf dem Fire TV: **Einstellungen → Mein Fire TV → Info**.
2. Den Eintrag mit dem Gerätenamen (z. B. „Fire TV Stick 4K“) markieren und **7-mal schnell OK drücken**, bis „Du bist jetzt Entwickler“ erscheint.
3. Eine Ebene zurück: **Mein Fire TV → Entwickleroptionen**.
4. **„Apps unbekannter Herkunft installieren“** öffnen (auf älteren Geräten heißt es „Apps unbekannter Herkunft“ → *AN*).

Den Downloader schaltest du hier erst nach Schritt 4 frei, sobald er installiert ist.

## Schritt 4: App mit dem Downloader installieren

1. Startbildschirm → **Suche (Lupe)** → „**Downloader**“ eingeben → die orange App von *AFTVnews* → **Herunterladen**.
2. Zurück zu *Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft installieren* → **Downloader: AN**.
3. Downloader öffnen, die Berechtigungsabfrage (Dateizugriff) mit **Zulassen** bestätigen.
4. Links **Home** wählen, ins Adressfeld klicken und eingeben:
   ```
   https://github.com/klaffky/spreeblick-website/releases/latest/download/FireTV-Spectatilus.apk
   ```
   → **Go**.
5. Nach dem Download erscheint der Installationsdialog → **Installieren** → **Fertig**.
6. Downloader fragt, ob die APK-Datei gelöscht werden soll → **Löschen** → nochmal **Löschen** (spart Speicher).
7. Die App heißt **FireTV Spectatilus** und liegt unter *Apps & Kanäle* (Symbol mit drei Quadraten auf dem Startbildschirm). Tipp: In der App-Übersicht die App markieren, **Menü-Taste (☰) → Verschieben**, um sie nach vorne zu holen.

> **Kürzer tippen:** Auf [go.aftvnews.com](https://go.aftvnews.com) kannst du für den langen Link kostenlos einen Zahlencode anlegen. Den gibst du dann im Downloader statt der Adresse ein.

## Schritt 5: Tailscale auf dem Fire TV

1. Startbildschirm → **Suche** → „**Tailscale**“ → herunterladen (offiziell im Amazon Appstore).
2. Tailscale öffnen → **Log in**. Ein QR-Code und eine Adresse erscheinen. Scanne den QR-Code mit dem Handy und melde dich dort mit **demselben Konto** wie am Server an.
3. Die Frage nach der **VPN-Verbindung** mit **OK** bestätigen.
4. Der Schalter in Tailscale steht auf **Active** bzw. **Connected**, und der Server erscheint in der Geräteliste.

> Nach einem Neustart des Fire TV verbindet sich Tailscale meistens von selbst. Falls nicht: Tailscale kurz öffnen und einschalten. Spectatilus hat dafür einen Knopf **„Tailscale öffnen“**.

## Schritt 6: Spectatilus einrichten

1. **FireTV Spectatilus** öffnen. Rechts siehst du, ob Tailscale verbunden ist (grüner Punkt).
2. **Tailscale-Adresse** anklicken und die Server-Adresse eingeben, z. B. `100.101.102.103` oder `jellyfin.tail1234.ts.net`.
   Der Port `:8096` und `http://` werden automatisch ergänzt.
3. Optional: **Heimnetz-Adresse** eintragen, z. B. `192.168.178.20`. Zu Hause verbindet die App dann direkt (schneller), unterwegs automatisch über Tailscale.
4. **Verbinden** drücken.
5. **„Wer schaut?“**: Profil wählen und Passwort eingeben, oder
   **Quick Connect** wählen: Es erscheint ein 6-stelliger Code. Am Handy in der Jellyfin-App bzw. im Browser *Profil → Quick Connect* öffnen, den Code eingeben, fertig.
6. Unter **Einstellungen** in der App kannst du einstellen:
   - **Streaming-Qualität:** zu Hause „Original“. Unterwegs mit langsamem Internet **6–10 Mbit/s**, dann wandelt der Server passend um.
   - **Audiosprache** (Deutsch/Englisch/Original), **Untertitel**, **Nächste Folge automatisch**.

## Bedienung mit der Fernbedienung

| Taste | Im Menü | Im Player |
|---|---|---|
| **◀ ▶ ▲ ▼** | Navigieren | ◀ ▶ bei ausgeblendeter Leiste: 10 s zurück / 30 s vor; sonst Steuerleiste bedienen |
| **OK** | Auswählen | Steuerleiste einblenden, Pause/Wiedergabe |
| **⏯** | – | Pause / Weiter |
| **⏪ ⏩** | – | Spulen |
| **↩ Zurück** | Eine Ebene zurück, auf „Start“ wechseln, App schließen | Player beenden (Position wird gespeichert) |
| **🎤 Mikrofon** | In Eingabefeldern: Spracheingabe | – |

Audio- und Untertitelspur wählst du im Player über das **Zahnrad/Untertitel-Symbol** in der Steuerleiste. Auf dem Startbildschirm blätterst du im großen Titelbild mit ◀ (auf „Fortsetzen/Abspielen“) bzw. ▶ (auf „Mehr Infos“).

## Unterwegs: so funktioniert's im Alltag

1. Fire TV Stick ins Hotel/Ferien-WLAN bringen und verbinden.
2. **Tailscale** öffnen, Verbindung prüfen (meist schon aktiv).
3. **Spectatilus** öffnen. Oben rechts steht **„Tailscale“**, die Mediathek ist da.

Zu Hause steht dort **„Heimnetz“**, wenn du die Heimnetz-Adresse eingetragen hast.

## Updates

Jeder neue Stand im Repository erzeugt automatisch ein neues Release. Zum Aktualisieren den **gleichen Downloader-Link** erneut öffnen und **Installieren** wählen. Die neue Version überschreibt die alte, Anmeldung und Einstellungen bleiben erhalten.

## Fehlerbehebung

| Problem | Lösung |
|---|---|
| **„Server nicht erreichbar“** | Ist Tailscale auf dem Fire TV *und* auf dem Server aktiv? Im Tailscale-Fenster des Fire TV muss der Server als „online“ erscheinen. Adresse prüfen, Port `8096`. |
| **Downloader meldet „Error 404“** | Link genau abtippen (Groß/Klein: `FireTV-Spectatilus.apk`). Oder einen Kurzcode über go.aftvnews.com anlegen. |
| **„App nicht installiert“** | Freigabe für den Downloader in den Entwickleroptionen prüfen; genug freier Speicher (> 100 MB)? Bei einer vorher anders signierten Version die alte App zuerst deinstallieren. |
| **Video ruckelt / lädt ständig** | In den App-Einstellungen eine niedrigere Streaming-Qualität wählen (6 oder 3 Mbit/s). Upload-Geschwindigkeit des Heimanschlusses ist meist der Engpass. |
| **Ton fehlt (DTS/TrueHD)** | Der Fire TV kann DTS nicht selbst dekodieren. Die App lässt dann automatisch vom Server umwandeln. Hilft das nicht, Qualität auf 20 Mbit/s stellen (erzwingt Umwandlung). |
| **„Anmeldung abgelaufen“** | In der App *Einstellungen → Abmelden* und neu anmelden. |
| **TLS-Fehler** | Für die `100.x`-IP immer `http://` nutzen. `https://` nur mit einem `*.ts.net`-Namen und eingerichtetem `tailscale serve`/HTTPS. |
| **Tailscale nach Neustart aus** | Tailscale-App öffnen und einschalten, oder in Spectatilus *Einstellungen → Tailscale-App öffnen*. |

## Repository umbenennen

Das Repository heißt noch `spreeblick-website`. So benennst du es in **FireTV-Spectatilus** um (GitHub erlaubt keine Leerzeichen im Namen):

1. Auf GitHub das Repository öffnen → **Settings** → **General**.
2. Unter **Repository name** `FireTV-Spectatilus` eintragen → **Rename**.
3. Lokal (falls geklont): `git remote set-url origin https://github.com/klaffky/FireTV-Spectatilus.git`

Alle alten Links, auch der Downloader-Link, leiten automatisch weiter.

## Für Entwickler

- Kotlin, Jetpack Compose, Media3 ExoPlayer, Coil, OkHttp. minSdk 22 (Fire OS 5), targetSdk 34.
- Bauen: `./gradlew assembleRelease` (JDK 17 und Android SDK nötig). Die APK liegt unter `app/build/outputs/apk/release/`.
- CI: `.github/workflows/build-apk.yml` baut bei jedem Push und veröffentlicht ein GitHub-Release mit `FireTV-Spectatilus.apk`. Auf `main` wird zusätzlich eine kleine Download-Seite (`docs/`) auf GitHub Pages veröffentlicht.
- Signatur: `signing/spectatilus.jks` (Passwort `spectatilus`) liegt bewusst im Repo, damit jede CI-Version die vorherige per Update überschreiben kann. Für einen eigenen, geheimen Schlüssel die Umgebungsvariablen `SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` setzen.

Projektstruktur:

```
app/src/main/java/de/klaffky/spectatilus/
├── data/        Jellyfin-API, Modelle, Geräteprofil (Codecs), Session (Heimnetz ↔ Tailscale)
├── ui/          Compose-Oberfläche: Start, Filme, Serien, Suche, Details, Einstellungen, Einrichtung
└── player/      PlayerActivity (ExoPlayer, Fortschrittsmeldung, nächste Folge)
```
