# Café & Restaurant Spreeblick – Website

Neue Website für das Café & Restaurant Spreeblick im Berliner Nikolaiviertel:
zweisprachig (DE/EN), mobil zuerst gedacht, mit Online-Reservierung.

**Stack:** [Astro 7](https://astro.build) (rein statisch) · Tailwind CSS 4 · selbst gehostete Schriften
(Fraunces, Figtree). Veröffentlicht über **GitHub Pages**: https://klaffky.github.io/spreeblick-website/

**Formulare ohne eigenen Server:** Reservierung und Kontakt werden im Browser geprüft (gleiche Regeln wie in
`src/lib/reservation.ts`) und dann verschickt:

- **Mit Web3Forms-Schlüssel** (empfohlen): Die Anfrage geht direkt als E-Mail mit Vorgangsnummer
  (z. B. `SB-1030-K7QX`) ans Restaurant, „Antworten“ geht an den Gast.
- **Ohne Schlüssel:** Das E-Mail-Programm des Gastes öffnet sich mit einer fertig ausgefüllten Nachricht an
  spreeblick@t-online.de.

## Lokal starten

```bash
npm install
npm run dev      # http://localhost:4321/spreeblick-website/
npm test         # Reservierungsregeln (node:test)
npm run check    # Typprüfung
npm run build    # Ergebnis in dist/
```

## Veröffentlichen (GitHub Pages)

1. Im Repository unter **Settings → Pages → Build and deployment → Source** „**GitHub Actions**“ wählen
   (einmalig).
2. Jeder Push auf `main` baut und veröffentlicht die Seite automatisch (`.github/workflows/deploy.yml`).
3. Direkten Formularversand aktivieren: Auf [web3forms.com](https://web3forms.com) mit
   spreeblick@t-online.de einen kostenlosen Access Key erstellen und unter
   **Settings → Secrets and variables → Actions → Variables** als `PUBLIC_WEB3FORMS_KEY` eintragen.
   Danach den Workflow neu starten (Actions → „Website veröffentlichen“ → Run workflow).

**Eigene Domain später:** In Pages die Domain eintragen und im Workflow beim Build
`SITE_URL=https://www.spreeblick-nikolaiviertel.de` und `BASE_PATH=/` setzen.

## Wo was gepflegt wird

| Was | Datei |
| --- | --- |
| Adresse, Telefon, Impressumsdaten, Hoster, Formular-Dienst | `src/config/site.ts` |
| Öffnungszeiten, Ruhetag, Reservierungsregeln (Uhrzeiten, Vorlauf, max. Personen, Schließtage) | `src/config/site.ts` |
| Speisekarte (DE/EN, Preise) | `src/data/menu.json` |
| Alle Texte der Oberfläche (DE/EN) | `src/i18n/ui.ts` |
| Datenschutzerklärung | `src/components/legal/PrivacyDe.astro`, `PrivacyEn.astro` |
| Fotos | `src/assets/img/` |

Größenvarianten in der Karte („Apfelschorle 0,3 l“ / „0,5 l“) werden automatisch zu einer Zeile
mit mehreren Preisen zusammengefasst.

## Bitte vor dem Livegang prüfen

- **Späteste Reservierungszeit:** Die alte Seite nennt nur „ab 12 Uhr“. Online reservierbar sind
  derzeit 12:00–21:00 Uhr im 30-Minuten-Takt, am selben Tag mit 2 Stunden Vorlauf, bis 10 Personen
  und bis 90 Tage im Voraus. Anpassen in `reservationRules`.
- **Datenschutzerklärung:** Eingetragen sind GitHub Pages als Hoster und Web3Forms als Formular-Dienst.
  Bitte juristisch gegenlesen lassen. Für die gewerbliche Nutzung ist ein Hoster mit
  Auftragsverarbeitungsvertrag (z. B. eine eigene Domain bei einem deutschen Anbieter) die sauberere Lösung.
- **Fotos:** Verwendet werden die drei Bilder der bisherigen Website. Das Hero-Hintergrundbild
  (Braten mit Möhren) wirkt wie ein Stockfoto. Bitte durch ein eigenes Foto ersetzen und gern
  hochauflösende Aufnahmen von Gastraum, Wintergarten und Spreeblick ergänzen (gleicher Dateiname
  in `src/assets/img/` genügt).
- **Impressum:** „§ 5 TMG“ wurde durch „§ 5 DDG“ ersetzt (das TMG gilt seit Mai 2024 nicht mehr).

## Barrierefreiheit & Bewegung

- Scroll-Animationen (Einblenden, Hero-Parallax, gezeichnete Linien) laufen nur mit JavaScript und
  nur, wenn das System **keine reduzierte Bewegung** verlangt. Ohne JS ist alles sofort sichtbar.
- Die Speisekarte blendet kurz und mit kleinem Weg ein. Beim Sprung über die Kategorie-Navigation
  wird alles bis zum Ziel sofort angezeigt. Formularfelder werden nie animiert.
- Mit JavaScript gibt es Live-Validierung, eine Fehlerübersicht mit Sprunglinks und Fokusführung. Ohne
  JavaScript senden die Formulare direkt an Web3Forms (mit Weiterleitung auf eine Danke-Seite) bzw. per
  `mailto:`.
