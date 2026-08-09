# Zylka

Android-App mit Anmeldung (Firebase Authentication) und Firestore als Datenbank.

## Design

Durchgängiges Material-3-Theme (`Theme.Zylka` in `res/values/themes.xml`,
Markenfarben in `res/values/colors.xml`: Grün für Fortschritt/Sammeln, Amber
als Akzent) mit einer eigenen `MaterialToolbar` auf jedem Screen statt der
Standard-ActionBar. Wiederkehrende Bausteine wie Aktions-Kacheln
(`item_action_card.xml`) und das Kennzeichen-Mini-Badge (`view_plate_badge.xml`,
angelehnt an ein echtes Euro-Kennzeichen) sind als eigene, wiederverwendbare
Layouts angelegt.

## Funktionsumfang (Stand jetzt)

- Registrierung und Login per E-Mail/Passwort (`com.oliver.zylka.auth`)
- Passwort-vergessen-Funktion (sendet eine Reset-Mail über Firebase)
- **Gerät merken:** Firebase Auth speichert die Sitzung automatisch lokal auf
  dem Gerät. `SplashActivity` prüft beim Start, ob bereits ein Nutzer
  eingeloggt ist, und überspringt den Login-Bildschirm dann direkt. Man
  muss sich also nur einmal pro Gerät anmelden – bis man sich aktiv abmeldet.
- Beim Registrieren wird ein minimales Profil-Dokument in einer **neuen**
  Firestore-Collection `users/{uid}` angelegt. Die alten Daten aus dem
  Vorgängerprojekt in dieser Datenbank werden dabei nicht angerührt.
- **In-App-Updates:** Beim Start (nach dem Login) prüft die App in Firestore,
  ob eine neuere Version veröffentlicht wurde. Falls ja, wird ein Dialog
  angezeigt; bei Bestätigung läuft der Download komplett innerhalb der App
  mit Fortschrittsbalken (kein Verlassen der App, keine Benachrichtigung
  nötig). Nur der abschließende Installations-Dialog kommt vom
  Android-System selbst – das lässt sich aus Sicherheitsgründen nicht
  automatisieren (gilt für jede App außerhalb des Play Stores). Details
  siehe Abschnitt "Neue Version veröffentlichen" weiter unten.
- **Startbildschirm (`MainActivity`):** feste helle Darstellung (ignoriert
  den System-Dunkelmodus bewusst), "Abmelden" liegt im Menü oben rechts
  (⋮), darunter Funktions-Kacheln: "Kennzeichen" (Sammelspiel), "Abfallkalender",
  "Notenspiegel" und "Pflanzen" (alle vier siehe unten).

## Kennzeichen-Sammelspiel

Die Kachel **„Kennzeichen"** führt in `com.oliver.zylka.kennzeichen` (klassische
Activities + XML-Layouts + ViewBinding, wie der Rest der App):

- **Eintragen** (`KennzeichenEntryActivity`): Kürzel per Suche antippen und als
  gefunden markieren. Dabei wird - falls die Berechtigung erteilt wurde - der
  aktuelle Standort mitgespeichert (siehe Datenmodell unten).
- **Meine Sammlung** (`KennzeichenCollectionActivity`): alle Kürzel des gewählten
  Landes, entdeckt/nicht entdeckt, durchsuchbar, mit Fortschrittsbalken.
- **Karte** (`KennzeichenMapActivity`): echte geografische Karte
  (`KennzeichenMapView`, eine `Canvas`-basierte Custom View), zoom- und
  schwenkbar (Pinch-to-Zoom, Doppeltipp, Ein-Finger-Schwenk sobald gezoomt),
  entdeckte Regionen werden eingefärbt. Antippen einer Region zeigt einen
  Dialog mit allen ihren Kennzeichen-Kürzeln und - wo bereits gefunden - wer
  es wann entdeckt hat. Für Deutschland sind das die echten ~400
  Landkreis-/kreisfreie-Stadt-Grenzen (nicht nur Bundesländer).
- **Gemeinsame Sammlung**: dieselbe Listenansicht, aber mit den Funden *aller*
  Spieler zusammen (jeder sammelt weiter für sich selbst; das ist eine
  gemeinsame, read-only "was hat die Gruppe schon gefunden"-Sicht).
- **Verlauf** (`KennzeichenHistoryActivity`): Chronik aller Funde eines Landes -
  wer hat wann was (und, falls vorhanden, wo) entdeckt.

Auf `KennzeichenHomeActivity` lässt sich das Land wechseln (Chips: 🇩🇪 🇦🇹 🇨🇭 🇫🇷).

### Datenquellen & Umfang

| Land | Sammel-Einheit | Anzahl Codes | Karte |
|---|---|---|---|
| 🇩🇪 Deutschland | Unterscheidungszeichen (Landkreis/kreisfreie Stadt), inkl. seit 2012 wieder zugelassener historischer Kürzel | 714 | echte Grenzen aller 402 Landkreise/kreisfreien Städte |
| 🇦🇹 Österreich | Bezirkskennzeichen | 97 | echte Bezirksgrenzen (94 davon mit Geometrie) |
| 🇨🇭 Schweiz | Kantonskürzel | 26 | echte Kantonsgrenzen |
| 🇫🇷 Frankreich | Département-Nummer (inkl. Übersee-Départements) | 101 | echte Département-Grenzen (Übersee als Kachel-Liste) |

Quellen: [openpotato/kfz-kennzeichen](https://github.com/openpotato/kfz-kennzeichen) (DE/AT/CH-Kürzel↔Ort-Zuordnung),
[m-ad/geofeatures-ags-germany](https://github.com/m-ad/geofeatures-ags-germany) (DE-Landkreisumrisse, 402 Polygone),
[gregoiredavid/france-geojson](https://github.com/gregoiredavid/france-geojson) (FR-Umrisse),
[ginseng666/GeoJSON-TopoJSON-Austria](https://github.com/ginseng666/GeoJSON-TopoJSON-Austria) (AT-Bezirksumrisse),
[d-qn/swiss-maps](https://github.com/d-qn/swiss-maps) (CH-Kantonsumrisse). Alle Rohdaten liegen als
GeoJSON unter `app/src/main/assets/geo/` bzw. als JSON-Kataloge unter
`app/src/main/assets/catalog/` und lassen sich dort direkt korrigieren/erweitern.

**Deutschland-Kreiskarte:** `m-ad/geofeatures-ags-germany` liefert amtliche
Landkreisgrenzen, aber keine verlässliche Kennzeichen-Zuordnung (das
mitgelieferte `kfz`-Feld erwies sich stichprobenartig als falsch, z. B.
"Böblingen" → "BL" statt korrekt "BB" - wird daher ignoriert). Die Zuordnung
Landkreis↔Kennzeichen kommt stattdessen aus der `openpotato`-Liste: für jeden
der 402 Landkreise wurde automatisiert (Namensabgleich mit Normalisierung,
~98 % Trefferquote) plus einer manuell aufgelösten Restmenge (~15 Sonderfälle,
z. B. Städte, die seit den 1970ern/2011 in einen Landkreis eingemeindet
wurden, aber ihr Kennzeichen behalten haben, wie Mannheim→Rhein-Neckar-Kreis
oder Greifswald→Vorpommern-Greifswald) eine vollständige, gegen mehrere
unabhängige Quellen stichprobengeprüfte Zuordnung erstellt. Ein Landkreis kann
mehrere gültige Kennzeichen haben (z. B. München: M, MU); die Karte färbt ihn
voll ein, sobald *eines* davon gefunden wurde, und halb, wenn nur ein Teil der
möglichen Kürzel gefunden wurde.

Dabei fiel auch auf, dass 24 Kennzeichen (u. a. MA Mannheim, IN Ingolstadt, ER
Erlangen, KO Koblenz, EF Erfurt, C Chemnitz) in der `openpotato`-Quelle
fälschlich als "auslaufend" markiert waren, obwohl sie nachweislich weiterhin
aktiv vergeben werden ("auslaufend" bezieht sich dort nur auf die historische
Zulassungsbehörde, nicht auf das Kennzeichen selbst) - sie sind jetzt Teil des
Katalogs (daher 714 statt vorher 688 deutsche Codes).

Die österreichische Zuordnung ist über die offizielle CSV automatisch abgeglichen;
4 von 97 Bezirken (Rust, Braunau, Salzburg-Umgebung, Leoben) brauchten eine
manuelle Korrektur wegen abweichender Schreibweisen zwischen den beiden
Quelldatensätzen – bei Auffälligkeiten bitte prüfen.

### Datenmodell (Firestore, live synchronisiert)

Jeder Fund ist ein eigenes Dokument in der Collection `discoveries` (kein
lokaler/Offline-Zustand - alles läuft über Firestore-Snapshot-Listener, die
sich in Echtzeit aktualisieren, auch auf anderen Geräten):

```
discoveries/{autoId}
{
  country: "de",              // Länder-ID
  code: "M",                  // Kennzeichen-Kürzel
  regionName: "München",
  uid: "…",                   // wer
  userLabel: "Oliver",        // Anzeigename/E-Mail, denormalisiert fürs Anzeigen
  discoveredAt: <Timestamp>,  // wann (serverseitig gesetzt)
  latitude: 48.13,            // wo (optional, nur mit erteilter Standort-Berechtigung)
  longitude: 11.58
}
```

Persönliche Sammlung, gemeinsame Sammlung und Karten-Fortschritt werden alle
clientseitig aus einem einzigen Live-Listener je Land abgeleitet (`where
country == …`, absichtlich ohne serverseitige Sortierung, damit kein
zusätzlicher Composite-Index in der Firebase Console angelegt werden muss).
Der Verlauf zeigt genau diesen Log, neueste zuerst. Ein Fund wird nur einmal
pro Nutzer und Kürzel gezählt (erneutes Antippen eines schon gefundenen
Kennzeichens legt keinen zweiten Log-Eintrag an).

Der Standort wird best-effort über `LocationManager.getCurrentLocation(...)`
abgefragt (Berechtigung `ACCESS_COARSE_LOCATION`/`ACCESS_FINE_LOCATION`, wird
beim ersten Eintragen einmalig angefragt); ohne Berechtigung oder bei Timeout
wird der Fund trotzdem gespeichert, nur ohne Koordinaten.

## Abfallkalender

Die Kachel **„Abfallkalender"** führt in `com.oliver.zylka.waste`
(`WasteCalendarActivity`): eine chronologische Übersicht aller kommenden
Abfuhrtermine, farblich nach Tonne unterschieden (Restabfall, Biotonne,
Altpapier, Gelber Sack – angelehnt an die realen deutschen Tonnenfarben),
plus eine optionale Erinnerung.

### Daten

Die Termine sind aus den zwei offiziellen PDF-Kalendern für Bünde,
Langenkamp (2026 und 2027) abgetippt und liegen als statisches JSON-Asset
unter `app/src/main/assets/waste/buende_langenkamp.json` (130 Termine,
je Datum eine Liste von Abfallarten – Biotonne und Altpapier fallen immer
auf denselben Tag). Feiertagsbedingte Abweichungen aus dem Originalkalender
sind exakt als eigene Termine übernommen, nicht aus einer Wiederholungsregel
berechnet – die Kalender-PDFs weisen dafür zu viele Ausnahmen auf, um das
sicher automatisch herzuleiten.

Restabfall wird in beiden PDFs doppelt beschriftet ("4-wöchentlich" *und*
"2-wöchentlich" auf denselben Terminen) – welcher Rhythmus für den
tatsächlichen Haushalt gilt, lässt sich aus dem PDF allein nicht ablesen.
Es sind daher alle 26 im Kalender gedruckten Restabfall-Termine im Jahr
enthalten (die sichere Wahl: im schlimmsten Fall erinnert die App einen
Abholzyklus zu oft, verpasst aber nie einen echten Termin).

Neue Jahre lassen sich ergänzen, indem weitere Termine mit demselben Format
in die `events`-Liste der JSON-Datei eingetragen werden – kein Code muss
dafür angepasst werden.

### Erinnerung & Benachrichtigung (auch bei geschlossener App)

Die Erinnerung ist bewusst nicht an einen laufenden App-Prozess gebunden,
sondern nutzt `AlarmManager` + einen in der Manifest registrierten
`BroadcastReceiver` (`WasteAlarmReceiver`) – das funktioniert identisch zu
einem Wecker/Kalender und feuert auch, wenn die App seit Tagen nicht
geöffnet wurde:

- Es ist immer nur **ein** exakter Alarm gleichzeitig geplant (für den
  nächsten anstehenden Termin). Löst er aus, zeigt `WasteNotifier` die
  Benachrichtigung und plant im selben Schritt sofort den nächsten Alarm
  ("Ketten"-Prinzip, `WasteAlarmScheduler`) – das vermeidet unnötig viele
  gleichzeitig im System hinterlegte Alarme.
- Exakte Alarme werden vom System bei jedem Neustart des Geräts verworfen;
  `WasteBootReceiver` (reagiert auf `BOOT_COMPLETED`) baut die Kette danach
  automatisch wieder auf.
- Zeitpunkt ist wählbar (Standard 18:00 Uhr, am Vorabend des Abholtags –
  die Tonnen müssen laut Kalender bereits um 6:00 Uhr am Straßenrand
  stehen, ein Erinnerungszeitpunkt am Morgen selbst wäre zu spät).
- Benötigt die Berechtigungen `POST_NOTIFICATIONS` (Android 13+) und
  `SCHEDULE_EXACT_ALARM`; beide werden erst angefragt, wenn die Erinnerung
  in der App eingeschaltet wird, nicht schon beim App-Start.

## Notenspiegelrechner

Die Kachel **„Notenspiegel"** führt in `com.oliver.zylka.notenspiegel`
(`NotenspiegelActivity`): Gesamtpunktzahl eingeben, Notensystem wählen
(Sechs-Stufen 1–6 oder die 15-0-Punkteskala der gymnasialen Oberstufe) -
die App zeigt sofort, von welcher bis zu welcher Punktzahl jede Note reicht.

### Rechenweg

Grundlage sind Prozent-Schwellen je Note (Mindestprozent, das für die Note
nötig ist). Aus Gesamtpunktzahl × Schwelle ergibt sich je Note ein
Punkte-Bereich, gerundet auf halbe Punkte (`NotenspiegelCalculator`):
lückenlos und überschneidungsfrei, die beste Note reicht immer bis zur
vollen Gesamtpunktzahl.

### Voreingestellte Schlüssel & Einstellungen

NRW schreibt für Klassenarbeiten in der Sekundarstufe I bewusst **keinen**
landesweit einheitlichen Notenschlüssel vor - das legt die jeweilige
Fachkonferenz fest. Als Startwert ist der verbreitete Sechs-Stufen-Schlüssel
**92 / 81 / 67 / 50 / 30 / 0 %** hinterlegt (u. a. als IHK-Notenschlüssel
bekannt und an vielen Schulen gebräuchlich). Für die 15-Punkte-Skala ist die
bundesweit übliche Punkte-Prozent-Zuordnung **95 / 90 / 85 / 80 / 75 / 70 /
65 / 60 / 55 / 50 / 45 / 40 / 33 / 27 / 20 / 0 %** hinterlegt, wie sie u. a.
in den NRW-Vorgaben zur Notenbildung im Zentralabitur als Orientierung
dient. Beide Schlüssel lassen sich über das Menü **„Einstellungen"**
(`NotenspiegelSettingsActivity`) Note für Note frei anpassen (an das, was
die eigene Fachkonferenz tatsächlich beschlossen hat). Dort lässt sich auch
die **Punkte-Genauigkeit** umstellen - ganze Punkte oder halbe Punkte
(0,5) - falls halbe Punkte (Kommawerte) nicht gewünscht sind
(`PointsPrecision`, Standard: halbe Punkte). Alles wird pro Konto in
Firestore gespeichert (`notenspiegel_settings/{uid}`) - steht also auf
jedem Gerät desselben Kontos zur Verfügung.

## Pflanzen (Gießplaner)

Die Kachel **„Pflanzen"** führt in `com.oliver.zylka.plants`
(`PlantsHomeActivity`): pro **Topf** (nicht pro Pflanze - mehrere Pflanzen
können sich einen Kübel teilen und werden in einem Vorgang gegossen) eine
Vorhersage, wann wieder gegossen werden muss. Kein Bodenfeuchtesensor - die
Prognose entsteht aus einem Wasserbilanzmodell auf Basis der Verdunstung
(Wetterdaten) und kalibriert sich durch das tatsächliche Gießverhalten
selbst nach.

- **Startseite** (`PlantsHomeActivity`): alle Töpfe, sortiert nach
  Dringlichkeit, mit Fortschrittsbalken (Restvorrat in %), "Gießen in X
  Tagen" bzw. "Jetzt gießen" und einem Button "Gegossen". Danach ein kurzer,
  überspringbarer Dialog zum Feedback (überfällig / passend / war noch
  feucht) - siehe Selbstkalibrierung unten. Erinnerung (an/aus + Feuchte-
  Schwellenwert in %, Standard 50) direkt auf dem Screen: keine feste
  Uhrzeit, die Benachrichtigung feuert genau dann, wenn die Prognose eines
  Topfs den eingestellten Wert unterschreitet.
- **Topf anlegen/bearbeiten** (`PotEditActivity`): Name, Durchmesser ODER
  direkt das Volumen in Litern (beide Felder rechnen sich ineinander um,
  mit sofort berechneter Kapazitäts-Vorschau), Standort, optionale Position
  (sonst wird bei jeder Prognose der Gerätestandort verwendet), zugeordnete
  Pflanzen.
- **Pflanze anlegen/bearbeiten** (`PlantEditActivity`): Name, Kategorie,
  Größenfaktor, Anzahl (mehrere gleiche Pflanzen als ein Eintrag statt
  einzeln anzulegen).
- **Topf-Verlauf** (`PotDetailActivity`): alle Gieß-Vorgänge sowie die
  simulierte Vorratskurve als Canvas-Custom-View (`PotWaterLevelChartView`,
  Vorbild `KennzeichenMapView`).

### Datenmodell (Firestore, live synchronisiert)

Der Wasservorrat gehört zum **Topf**, nicht zur Pflanze. Es wird kein
laufender Füllstand gespeichert - der Zustand wird bei jeder Anzeige
zustandslos aus dem letzten Gießvorgang plus der stündlichen Wetterreihe neu
berechnet:

```
pots/{potId}
  uid (wer angelegt hat), name, durchmesserCm, volumenLiter (berechnet),
  standort: "innen" | "unterDach" | "frei",
  kapazitaetMm (kalibriert, Startwert berechnet),
  kapazitaetStartwertMm (eingefrorener Startwert, begrenzt die Kalibrierung),
  standortfaktor (Startwert aus dem Standort, danach frei nachjustierbar),
  latitude, longitude (optional, sonst Gerätestandort)

plants/{plantId}
  uid (wer angelegt hat), potId, name,
  kategorie: SUKKULENTE | MEDITERRAN | STANDARD | DURSTIG | GEMUESE,
  kcBasis, groessenfaktor (Default 1.0), anzahl (Default 1 - mehrere
  gleiche Pflanzen als ein Eintrag statt einzeln anzulegen)

waterings/{autoId}          // append-only Log, wie discoveries
  uid (wer gegossen hat), potId, wateredAt (Server-Timestamp),
  feedback: "UEBERFAELLIG" | "PASSEND" | "NOCH_FEUCHT" | null

weather_cache/{uid}         // ein Dokument je Nutzer, ein Eintrag je Standort
  locations: { "<lat,lon gerundet>": { fetchedAt, latitude, longitude, hourly } }
```

`pots`, `plants` und `waterings` sind zwischen allen eingeloggten Nutzern
geteilt (gemeinsamer Garten, z. B. für dich und deine Partnerin) - jeder
sieht und bearbeitet alle Töpfe und Pflanzen, unabhängig davon, wer sie
angelegt hat; das `uid`-Feld ist reine Herkunfts-Information. Nur der
Wetter-Cache bleibt strikt pro Konto (siehe Firestore-Regeln unten).

### Rechenweg (`PlantWaterCalculator`, ohne Android-Abhängigkeiten)

**Verdunstung.** Basis ist ET₀ (Referenzverdunstung, mm/h) aus der
Wetter-API. Pro Topf:

    Kc_topf = Σ (kcBasis × groessenfaktor) über alle Pflanzen im Topf
    ET_topf(t) = ET0(t) × standortfaktor × Kc_topf

Standortfaktor-Startwerte: frei = 1.0, unterDach = 0.5, innen = 0.25.

**Wasserbilanz.**

    vorrat(t) = clamp(vorrat(t-1) − ET_topf(t) + regen(t) × regenfaktor, 0, kapazitaetMm)

Regenfaktor: frei = 1.0, unterDach = 0.0, innen = 0.0. Nach jedem Gießen
startet der Vorrat wieder bei voller Kapazität; die Gießschwelle ist der in
`PlantsHomeActivity` eingestellte Feuchte-Schwellenwert (Standard 50 % der
Kapazität, per Schieberegler 20-80 % einstellbar).

**Startwert der Kapazität.** Topfvolumen als Kegelstumpf (obere Öffnung =
Durchmesser, untere Öffnung ≈ 70 % davon, Höhe ≈ 0.85 × Durchmesser), davon
28 % pflanzenverfügbares Wasser, umgerechnet auf mm über die
Topf-Grundfläche. Diese Umrechnung braucht die Grundfläche (nicht nur das
Volumen), weil sich die Verdunstung auf die Erdoberfläche bezieht - ein
flacher, breiter Topf trocknet bei gleichem Volumen schneller aus als ein
schmaler, tiefer. `PotEditActivity` bietet deshalb wahlweise Durchmesser
oder Volumen als Eingabe an; `durchmesserFuerVolumen()` rechnet ein
eingegebenes Volumen mit derselben Kegelstumpf-Annahme in einen
gleichwertigen Durchmesser um.

**Prognose.** Das Modell läuft mit der Wetterreihe (Vergangenheit + Prognose)
stündlich vorwärts, bis der Vorrat die Gießschwelle unterschreitet - das
ergibt das Fälligkeitsdatum. Wurde seit dem letzten Gießen länger nicht mehr
gegossen, als die Wetter-Rückschau zurückreicht, wird die Lücke einmalig mit
der mittleren ET0 der verfügbaren Woche überbrückt. Bei jedem Wetterabruf
wird neu gerechnet.

**Selbstkalibrierung.** Bei jedem neuen Gießvorgang:

    verhaeltnis = verbrauchtBisGiessen / gießschwelle
    kapazitaetMm *= (1 + 0.2 × (verhaeltnis − 1))

Das optionale Feedback beim Gießen verschiebt zusätzlich: „Überfällig" →
Kapazität −10 %, „War noch feucht" → +10 %. `kapazitaetMm` ist auf 20-500 %
des geometrischen Startwerts (`kapazitaetStartwertMm`) begrenzt.
`standortfaktor` kalibriert sich dabei **nicht** automatisch mit - er wird
beim Anlegen aus dem Standort vorbelegt und danach nur von Hand in
`PotEditActivity` nachjustiert.

### Datenquellen

Wetterdaten (ET0, Niederschlag) kommen kostenlos und ohne API-Key von
[Open-Meteo](https://open-meteo.com/en/docs) (`past_days=7&forecast_days=7`,
`timezone=Europe/Berlin`), abgerufen über dieselbe einfache HTTP-Mechanik
wie beim In-App-Update (`HttpURLConnection`, kein neues Gradle-Modul). Die
Antwort wird in Firestore zwischengespeichert (`weather_cache/{uid}`,
höchstens ein Abruf alle 3 Stunden je Standort); schlägt ein Abruf fehl,
rechnet die App mit dem letzten Cache-Stand weiter und weist in der
Oberfläche darauf hin ("Wetterdaten evtl. veraltet").

### Grenzen des Modells

- **Licht wird nicht erfasst.** Ein schattiger vs. sonniger Platz mit
  gleichem `standort`-Wert sieht für das Modell identisch aus - das lässt
  sich nur indirekt über einen von Hand nachjustierten `standortfaktor`
  ausgleichen.
- **Umtopfen und Wachstum brechen die Kalibrierung.** Ein neuer Durchmesser
  in `PotEditActivity` setzt `kapazitaetMm` bewusst auf den neu berechneten
  geometrischen Startwert zurück - die bisherige Selbstkalibrierung ist
  damit hinfällig und baut sich erst über die nächsten Gießvorgänge wieder
  auf. Deutliches Pflanzenwachstum (verändertes `groessenfaktor`) hat einen
  ähnlichen, aber unauffälligeren Effekt.
- **Selbstbewässerungstöpfe/Untersetzer mit Wasserreservoir passen nicht ins
  Modell** - sie folgen keiner reinen Verdunstungs-Bilanz (das Reservoir
  puffert unabhängig von der Topf-Kapazität), die Prognose wäre für solche
  Töpfe irreführend.
- Kein Bluetooth-/TP357-Sensor in diesem Schritt - `WeatherRepository` ist
  aber so gehalten, dass sich eine lokale Mikroklima-Messquelle später
  danebenstellen ließe. Keine Artendatenbank-Anbindung: die Kategorie
  bleibt vorerst eine manuelle Auswahl.

## Firebase einrichten (einmalig, in der Firebase Console)

Das Projekt nutzt die bestehende Firebase-Datenbank `kennzeichen-zyo`.

1. Firebase Console öffnen → Projekt `kennzeichen-zyo` auswählen.
2. **Android-App hinzufügen** (Projekteinstellungen → "App hinzufügen" →
   Android):
   - Package-Name genau: `com.oliver.zylka`
   - App-Spitzname (optional): "Zylka"
   - SHA-1 wird für Login/Registrierung per E-Mail/Passwort nicht benötigt,
     kann übersprungen werden.
3. Die generierte **`google-services.json`** herunterladen und nach
   `app/google-services.json` legen (also neben `app/build.gradle.kts`).
   Diese Datei ist bewusst in `.gitignore` und wird **nicht** ins Repo
   eingecheckt, weil sie projektspezifische Zugangsdaten enthält. Eine
   Vorlage zur Orientierung liegt unter `app/google-services.json.example`.
4. In der Firebase Console unter **Authentication → Sign-in method** den
   Anbieter **E-Mail/Passwort** aktivieren.
5. Unter **Firestore Database** sicherstellen, dass Firestore aktiviert ist
   (sollte durch das Vorgängerprojekt schon der Fall sein). Die neue
   Collection `users` wird automatisch beim ersten Registrieren angelegt.

Danach das Projekt in Android Studio öffnen (oder erneut synchronisieren)
und starten – Gradle lädt die restlichen Abhängigkeiten automatisch.

## Firestore-Regeln (empfohlen)

Damit nur eingeloggte Nutzer ihr eigenes Profil lesen/schreiben können,
empfiehlt sich mindestens folgende Regel für die neue `users`-Collection
(bestehende Regeln für die alten Daten bitte unverändert lassen):

```
match /users/{uid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

Für die Update-Prüfung zusätzlich (nur lesbar für eingeloggte Nutzer, schreibbar
nur über die Firebase Console / Admin, nie aus der App heraus):

```
match /app_config/{document} {
  allow read: if request.auth != null;
  allow write: if false;
}
```

Für das Kennzeichen-Sammelspiel (jeder eingeloggte Nutzer darf den Fund-Log
lesen; anlegen darf man nur Funde, die auf einen selbst eingetragen sind;
geändert oder gelöscht wird nie - der Log ist bewusst nur anhängbar) – siehe
auch `firestore.rules` im Repo-Root:

```
match /discoveries/{discoveryId} {
  allow read: if request.auth != null;
  allow create: if request.auth != null
    && request.resource.data.uid == request.auth.uid;
  allow update, delete: if false;
}
```

Für den Notenspiegelrechner (persönliche Schwellen-Einstellungen, nur vom
eigenen Konto lesbar/schreibbar):

```
match /notenspiegel_settings/{uid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

Für den Gießplaner (Töpfe und Pflanzen für alle eingeloggten Nutzer
gemeinsam les- und schreibbar - geteilter Garten; der Gieß-Log ist wie
`discoveries` nur anhängbar und für alle lesbar, angelegt werden dürfen
aber nur Einträge mit dem eigenen Konto als `uid`; der Wetter-Cache bleibt
reine Zwischenablage je Konto):

```
match /pots/{potId} {
  allow read, write: if request.auth != null;
}
match /plants/{plantId} {
  allow read, write: if request.auth != null;
}
match /waterings/{wateringId} {
  allow read: if request.auth != null;
  allow create: if request.auth != null && request.resource.data.uid == request.auth.uid;
  allow update, delete: if false;
}
match /weather_cache/{uid} {
  allow read, write: if request.auth != null && request.auth.uid == uid;
}
```

## Firebase Storage einrichten (für Updates)

1. Firebase Console → **Storage** aktivieren (falls noch nicht geschehen).
2. Unter **Rules** folgendes eintragen: alles standardmäßig gesperrt, nur
   der Ordner `releases/` ist öffentlich lesbar (damit der Download in der
   App ohne Firebase-Login funktioniert), aber niemals von außen
   beschreibbar:

   ```
   rules_version = '2';
   service firebase.storage {
     match /b/{bucket}/o {
       match /{allPaths=**} {
         allow read, write: if false;
       }
       match /releases/{fileName} {
         allow read: if true;
         allow write: if false;
       }
     }
   }
   ```

## Release-Signatur einrichten (einmalig)

Android lässt eine "Update"-Installation nur zu, wenn die neue APK mit
**demselben Schlüssel** signiert ist wie die bereits installierte. Deshalb
braucht ihr einen eigenen Release-Keystore, den ihr für alle zukünftigen
Versionen wiederverwendet.

1. In Android Studio: **Build → Generate Signed Bundle / APK…** → **APK** →
   **Create new…**, Keystore-Datei z. B. als `zylka-release.jks` **außerhalb**
   des Projektordners speichern (z. B. eine Ebene über `Zylka/`) und
   Passwörter/Alias vergeben.
   ⚠️ Diese `.jks`-Datei und die Passwörter gut aufbewahren (z. B. im
   Passwort-Manager) – geht sie verloren, können bestehende Installationen
   nie wieder aktualisiert werden, nur noch komplett neu installiert werden.
2. Im Projekt-Root (`Zylka/`, neben `build.gradle.kts`) die Datei
   `keystore.properties.example` kopieren nach `keystore.properties` und mit
   den echten Werten füllen (Pfad zur `.jks`-Datei, Passwörter, Alias). Diese
   Datei ist über `.gitignore` bewusst vom Repo ausgeschlossen.
3. Danach erzeugt **Build → Generate Signed Bundle / APK…** (oder
   `./gradlew assembleRelease`) automatisch signierte Release-APKs unter
   `app/build/outputs/apk/release/`.

## Neue Version veröffentlichen

1. In `app/build.gradle.kts` unter `defaultConfig`:
   - `versionCode` um 1 erhöhen (z. B. `1` → `2`)
   - `versionName` auf die neue Anzeigeversion setzen (z. B. `"1.1.0"`)
2. Signierte Release-APK bauen: **Build → Generate Signed Bundle / APK…** →
   **APK** → euren Release-Keystore auswählen → **release**.
3. Die entstandene `app-release.apk` in der Firebase Console unter
   **Storage** in den Ordner **`releases/`** hochladen (z. B. als
   `zylka-1.1.0.apk`).
4. Die Datei anklicken → Download-Link kopieren (Feld "Access token" /
   Download-URL in den Datei-Details).
5. In **Firestore** das Dokument **`app_config/version`** anlegen bzw.
   aktualisieren mit den Feldern:
   - `versionCode` (Zahl) – muss zum neuen `versionCode` aus Schritt 1 passen
   - `versionName` (Text) – z. B. `"1.1.0"`
   - `apkUrl` (Text) – der kopierte Download-Link
   - `notes` (Text, optional) – z. B. "Fehlerbehebungen"

Sobald das gespeichert ist, bekommen alle Nutzer beim nächsten App-Start den
Update-Hinweis angezeigt.

## Projektstruktur

```
app/src/main/java/com/oliver/zylka/
├── SplashActivity.kt          # Prüft gespeicherte Sitzung, leitet weiter
├── MainActivity.kt            # Startbildschirm nach dem Login, stößt Update-Check an
├── auth/
│   ├── LoginActivity.kt
│   └── RegisterActivity.kt
├── data/
│   ├── AuthRepository.kt      # Kapselt FirebaseAuth + Firestore-Profil
│   ├── UpdateInfo.kt          # Datenklasse für app_config/version
│   ├── UpdateRepository.kt    # Liest app_config/version aus Firestore
│   └── kennzeichen/           # Datenschicht des Kennzeichen-Sammelspiels
│       ├── Country.kt              # DE/AT/CH/FR
│       ├── PlateRegion.kt          # Ein Kennzeichen-Kürzel + Metadaten
│       ├── CatalogRepository.kt    # Lädt assets/catalog/*.json
│       ├── GeoShape.kt / GeoRepository.kt  # Lädt & parst assets/geo/*.geojson
│       ├── Discovery.kt            # Ein Fund-Dokument (wer/was/wann/wo)
│       ├── DiscoveryRepository.kt  # Firestore: Fund-Log je Land, live
│       └── LocationHelper.kt       # Best-effort Standortabfrage
│   └── waste/                 # Datenschicht des Abfallkalenders
│       ├── WasteType.kt              # Restabfall/Biotonne/Altpapier/Gelber Sack
│       ├── WasteEvent.kt             # Ein Abholtermin (Datum + Abfallarten)
│       ├── WasteCalendarRepository.kt  # Lädt assets/waste/*.json
│       └── WastePrefs.kt             # Erinnerung an/aus + Uhrzeit (SharedPreferences)
│   └── notenspiegel/           # Datenschicht des Notenspiegelrechners
│       ├── GradingSystem.kt          # Sechs-Stufen / 15-Punkte-Skala
│       ├── GradingPresets.kt         # Standard-Prozent-Schwellen je Skala
│       ├── NotenspiegelSettings.kt   # Persönliche (oder Standard-)Schwellen
│       ├── NotenspiegelSettingsRepository.kt  # Firestore: notenspiegel_settings/{uid}
│       └── NotenspiegelCalculator.kt # Prozent-Schwellen → Punkte-Bereiche je Note
│   └── plants/                 # Datenschicht des Gießplaners
│       ├── Standort.kt / PlantCategory.kt / WateringFeedback.kt  # Enums mit Startwerten
│       ├── Pot.kt / Plant.kt / Watering.kt   # Firestore-Datenklassen
│       ├── PotRepository.kt / PlantRepository.kt / WateringRepository.kt
│       ├── PlantWaterCalculator.kt   # Alle Formeln (Verdunstung, Bilanz, Kalibrierung)
│       ├── WeatherRepository.kt      # Open-Meteo-Abruf + weather_cache/{uid}
│       ├── PlantForecastRepository.kt  # Bündelt Pots+Plants+Waterings+Wetter zu Prognosen
│       └── PlantPrefs.kt             # Erinnerung an/aus + Feuchte-Schwellenwert (SharedPreferences)
├── kennzeichen/                # UI des Kennzeichen-Sammelspiels
│   ├── KennzeichenHomeActivity.kt
│   ├── KennzeichenEntryActivity.kt
│   ├── KennzeichenCollectionActivity.kt
│   ├── KennzeichenMapActivity.kt
│   ├── KennzeichenHistoryActivity.kt   # Verlauf: wer hat wann was entdeckt
│   ├── KennzeichenMapView.kt        # Canvas-Custom-View, zeichnet die Karte
│   ├── PlateRegionAdapter.kt        # RecyclerView-Adapter für die Listen
│   └── HistoryAdapter.kt            # RecyclerView-Adapter für den Verlauf
├── waste/                      # UI + Alarme des Abfallkalenders
│   ├── WasteCalendarActivity.kt     # Übersicht + Erinnerungs-Einstellung
│   ├── WasteEventAdapter.kt         # RecyclerView-Adapter für die Terminliste
│   ├── WasteAlarmScheduler.kt       # Plant den jeweils nächsten exakten Alarm
│   ├── WasteAlarmReceiver.kt        # Zeigt Benachrichtigung, plant Folgealarm
│   ├── WasteBootReceiver.kt         # Baut die Alarmkette nach Geräte-Neustart neu auf
│   └── WasteNotifier.kt             # Notification-Channel + Benachrichtigung
├── notenspiegel/                # UI des Notenspiegelrechners
│   ├── NotenspiegelActivity.kt       # Eingabe + Ergebnisliste
│   ├── NotenspiegelSettingsActivity.kt  # Schwellen bearbeiten + speichern
│   ├── GradeBandAdapter.kt          # RecyclerView-Adapter für die Ergebnisliste
│   └── GradeThresholdEditAdapter.kt # RecyclerView-Adapter für die Schwellen-Eingabe
├── plants/                      # UI + Alarme des Gießplaners
│   ├── PlantsHomeActivity.kt        # Töpfe nach Dringlichkeit, Gießen-Button, Erinnerung
│   ├── PotEditActivity.kt           # Topf anlegen/bearbeiten, Pflanzen zuordnen
│   ├── PlantEditActivity.kt         # Pflanze anlegen/bearbeiten
│   ├── PotDetailActivity.kt         # Gieß-Verlauf + Vorratskurve
│   ├── PotSummaryAdapter.kt / WateringHistoryAdapter.kt  # RecyclerView-Adapter
│   ├── PotWaterLevelChartView.kt    # Canvas-Custom-View, zeichnet die Vorratskurve
│   ├── PlantAlarmScheduler.kt       # Plant den Alarm für den dringlichsten Topf
│   ├── PlantAlarmReceiver.kt        # Zeigt Benachrichtigung, plant Folgealarm
│   ├── PlantBootReceiver.kt         # Baut die Alarmkette nach Geräte-Neustart neu auf
│   └── PlantNotifier.kt             # Notification-Channel + Benachrichtigung
└── update/
    └── UpdateManager.kt       # Lädt APK herunter, stößt Installation an
```

## Nächste mögliche Schritte

- Gemeinsame Daten (z. B. für dich, deine Partnerin und später Freunde) in
  weiteren Firestore-Collections modellieren.
- Freigabe/Einladung weiterer Nutzer (z. B. Registrierung ggf. auf
  bestimmte E-Mail-Adressen beschränken, falls die App nicht offen für
  jeden sein soll).
- App-Icon und Farben (`app/src/main/res/values/colors.xml`,
  `mipmap-*/ic_launcher*`) anpassen.
