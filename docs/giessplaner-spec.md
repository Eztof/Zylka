# Prompt für Claude Code — Modul "Gießplaner" für Zylka

> Alles ab hier in Claude Code einfügen.

---

Lies zuerst die `README.md` im Repo-Root komplett sowie `firestore.rules` und mindestens ein
bestehendes Feature-Modul (`com.oliver.zylka.waste` inkl. `data/waste/`), um die Konventionen
des Projekts zu übernehmen: klassische Activities + XML-Layouts + ViewBinding, Material-3-Theme
mit eigener `MaterialToolbar`, Firestore als einzige Persistenz mit Snapshot-Listenern,
Strings in `strings.xml` (deutsch), Datenschicht getrennt unter `data/<feature>/`.

Baue ein neues Feature-Modul `com.oliver.zylka.plants` mit der Kachel **"Pflanzen"** auf dem
Startbildschirm.

## Was das Modul tun soll

Einen Gießplaner auf Basis eines Wasserbilanz-Modells: Jedes Pflanzgefäß ist ein Wasserspeicher,
der sich durch Verdunstung leert und durch Gießen (und ggf. Regen) füllt. Die App sagt voraus,
wann wieder gegossen werden muss, und benachrichtigt rechtzeitig. Der Nutzer bestätigt das Gießen
per Button; daraus kalibriert sich das Modell pro Gefäß selbst nach.

Datenquellen für die Verdunstung:
1. **Open-Meteo** (Wetterprognose + Rückschau) — Pflichtquelle.
2. **ThermoPro TP357 Bluetooth-Thermo-Hygrometer** — optionale Mikroklima-Korrektur,
   drinnen wie draußen. Kommt in Phase 4, muss aber im Design ab Phase 1 vorgesehen sein.

## Zentrale Designentscheidung: Gefäß ≠ Pflanze

Die Recheneinheit ist das **Pflanzgefäß** (`Planter`), nicht die einzelne Pflanze. In einem großen
Kübel stehen mehrere Pflanzen, die sich einen Wasservorrat teilen. Pflanzen (`Plant`) hängen als
Liste am Gefäß und beeinflussen nur den Verbrauchsfaktor. Beetflächen werden als Gefäß mit
Bodentyp "Freiland" modelliert. Bitte diese Trennung konsequent durchziehen.

## Datenmodell (Firestore)

```
planters/{planterId}
  uid, name                       // "Kübel Terrasse links"
  environment                     // INDOOR | OUTDOOR_COVERED | OUTDOOR_OPEN
  diameterCm, heightCm            // Geometrie; oder shape-Preset
  shape                           // ROUND | RECT (bei RECT: lengthCm, widthCm)
  substrate                       // POTTING_SOIL | CACTUS_MIX | GARDEN_SOIL
  exposure                        // FULL_SUN | PARTIAL | SHADE
  density                         // SPARSE | NORMAL | DENSE
  capacityLitres                  // berechnet, durch Kalibrierung überschreibbar
  capacityCalibrated              // bool: wurde bereits nachgezogen
  sensorId                        // optional, Referenz auf TP357
  createdAt

plants/{plantId}
  uid, planterId, name, species, thirst   // SUCCULENT | LOW | NORMAL | HIGH
  photoUrl (optional), createdAt

waterings/{autoId}                // append-only Log, analog zu discoveries
  uid, planterId, wateredAt, amountLitres (optional),
  feedback                        // ON_TIME | TOO_LATE | TOO_EARLY | UNKNOWN
  note (optional)

sensor_readings/{autoId}          // ab Phase 4
  uid, sensorId, measuredAt, temperatureC, humidityPercent

weather_cache/{uid}               // ein Dokument pro Nutzer
  latitude, longitude, fetchedAt,
  hourly: [ { t, et0, precipitation, temperature, humidity } ]
```

Firestore-Regeln in `firestore.rules` ergänzen, im Stil der bestehenden: alles nur für den
eigenen `uid` les-/schreibbar, `waterings` append-only (kein update/delete).

## Rechenkern

Lege den kompletten Rechenkern als **reines Kotlin ohne Android-Abhängigkeiten** unter
`data/plants/model/` an, damit er per JVM-Unit-Test prüfbar ist. Keine Firestore- oder
Context-Referenzen in diesen Klassen.

### 1. Geometrie und Kapazität

```
Fläche A [m²]      = π · (d/2)²        bzw. l · b      (in Metern)
Volumen V [L]      = A · h · 1000      (h in Metern)
Kapazität C [L]    = V · nutzbarerAnteil
```

Nutzbarer Wasseranteil: `POTTING_SOIL` 0.20, `CACTUS_MIX` 0.12, `GARDEN_SOIL` 0.15.

### 2. Verdunstungsrate

```
Verlust [L/h] = ET [mm/h] · Kc_eff · A [m²]
```

`ET` kommt draußen aus `et0_fao_evapotranspiration` der API, drinnen aus dem VPD (siehe unten).

`Kc_eff = Kc_pflanzen · Dichte · Exposition`

- `Kc_pflanzen`: Maximum der Durstigkeit aller Pflanzen im Gefäß —
  `SUCCULENT` 0.3, `LOW` 0.8, `NORMAL` 1.5, `HIGH` 2.5.
  (Werte > 1 sind korrekt: die Blattfläche einer eingewachsenen Kübelpflanze übersteigt
  die Topfoberfläche deutlich.)
- `Dichte`: `SPARSE` 1.0, `NORMAL` 1.5, `DENSE` 2.5
- `Exposition`: `FULL_SUN` 1.0, `PARTIAL` 0.7, `SHADE` 0.5.
  Bei `OUTDOOR_COVERED` zusätzlich × 0.8 (weniger Strahlung und Wind unter dem Dach).

### 3. Innenraum-ET aus Temperatur und Luftfeuchte

Magnus-Formel, Ergebnis in kPa:

```
es(T) = 0.6108 · exp(17.27 · T / (T + 237.3))
VPD   = es(T) · (1 − RH/100)
ET_innen [mm/h] = C_VPD · VPD          mit C_VPD = 0.05 (Konstante, später kalibrierbar)
```

Ohne Sensordaten: Fallback auf 21 °C / 45 % rel. Feuchte.

### 4. Mikroklima-Korrektur (Phase 4, Schnittstelle ab Phase 1 vorsehen)

Wenn einem Gefäß ein TP357 zugeordnet ist und für die letzten 72 h mindestens 24 überlappende
Stundenwerte von Sensor und API vorliegen:

```
f = median( VPD_sensor(t) / VPD_api(t) )      über die überlappenden Stunden
f = clamp(f, 0.5, 2.0)
ET_lokal(t) = ET_api(t) · f
```

Der Faktor wird auch auf die Prognosestunden angewandt — der Sensor korrigiert also den
Wetterbericht auf den tatsächlichen Standort. Ohne ausreichende Datenlage ist `f = 1.0`.

Hinweis für die Implementierung: Der Expositionsfaktor aus 2. bleibt zusätzlich bestehen, weil
er Strahlung und Wind abbildet, die ein Temperatur-/Feuchtesensor nicht sieht. Eine leichte
Doppelgewichtung ist beabsichtigt und wird durch die Kalibrierung in 6. wieder eingefangen.

### 5. Wasserbilanz und Prognose

Kein laufender Füllstand in der Datenbank. Der Stand wird bei jedem Aufruf **zustandslos neu
gerechnet**, ausgehend vom letzten `wateredAt` über die stündliche ET-Reihe nach vorne:

```
level(t) = clamp( level(t−1) − Verlust(t) + Regen(t) · Regenfaktor , 0 , C )
```

Regenfaktor: `OUTDOOR_OPEN` 1.0, `OUTDOOR_COVERED` 0.0, `INDOOR` 0.0.
Regen in mm → Liter über dieselbe Fläche A.

Gießschwelle: 50 % Verbrauch, also Warnung sobald `level < 0.5 · C`.

**Prognose:** Dasselbe Modell mit der Forecast-Reihe weiterlaufen lassen, bis die Schwelle
unterschritten wird. Ergebnis: Zeitpunkt + Restdauer in Tagen. Wenn die Prognosereihe endet,
bevor die Schwelle erreicht ist, mit dem Mittelwert der letzten 24 Prognosestunden extrapolieren
und das Ergebnis als unsicher kennzeichnen.

### 6. Selbstkalibrierung

Bei jedem neuen `waterings`-Eintrag: berechne, wie viel Prozent des Vorrats zum Zeitpunkt des
Gießens tatsächlich verbraucht waren (`verbrauchtAnteil`), und ziehe die Kapazität nach:

```
C_neu = C_alt · (1 + α · (verbrauchtAnteil / 0.5 − 1))      mit α = 0.2
```

Bei explizitem Feedback zusätzlich korrigieren: `TOO_LATE` → Faktor 0.85 auf C,
`TOO_EARLY` → Faktor 1.15. `C` immer auf [0.25 · C_initial, 4 · C_initial] begrenzen,
damit ein einzelner Ausreißer das Modell nicht zerlegt. Kalibrierungen, die weniger als
12 h nach dem letzten Gießen erfolgen, ignorieren (Nachgießen, kein echter Zyklus).

Beim Bearbeiten der Gefäß-Stammdaten (Umtopfen, Pflanze ergänzt) `capacityCalibrated`
zurücksetzen und neu aus der Geometrie rechnen.

### 7. Plausibilitätstests (bitte als Unit-Tests umsetzen)

Diese Erwartungswerte sind bewusst als Bandbreiten formuliert und dienen als Leitplanke dafür,
dass die Startparameter im richtigen Bereich liegen:

- Kübel 40 cm Ø, 35 cm hoch, `OUTDOOR_COVERED`, `PARTIAL`, `DENSE`, Pflanzen `NORMAL`,
  Sommer-ET₀ 4 mm/Tag, kein Regen → Gießintervall **3 bis 7 Tage**.
- Zimmerpflanze 15 cm Ø, 14 cm hoch, `INDOOR`, `PARTIAL`, `SPARSE`, `NORMAL`,
  21 °C / 45 % → Gießintervall **7 bis 14 Tage**.
- Sukkulente 12 cm Ø, `CACTUS_MIX`, `SUCCULENT` → Intervall **> 25 Tage**.
- Freilandbeet mit Regen 20 mm bei `OUTDOOR_OPEN` → Vorrat wieder auf 100 %.

Zusätzlich Unit-Tests für: Magnus-Formel gegen Tabellenwerte, Geometrie/Volumen,
Clamping des Korrekturfaktors, Kalibrierungsgrenzen, Verhalten bei Datenlücken.

## Wetteranbindung (Open-Meteo)

Kostenlos, kein API-Key, Lizenz CC BY 4.0 (Attribution im Über-Dialog vermerken).

```
https://api.open-meteo.com/v1/forecast
  ?latitude={lat}&longitude={lon}
  &hourly=et0_fao_evapotranspiration,precipitation,temperature_2m,relative_humidity_2m
  &past_days=7&forecast_days=7
  &timezone=Europe%2FBerlin
```

- Standort einmalig über den bestehenden `LocationHelper` aus dem Kennzeichen-Modul ermitteln,
  mit manueller Eingabe als Rückfallebene (der Nutzer soll den Ort auch ohne
  Standortberechtigung setzen können).
- `past_days=7` ist wichtig: damit lässt sich die Bilanz auch dann korrekt nachrechnen, wenn die
  App tagelang nicht geöffnet wurde.
- Antwort in `weather_cache/{uid}` ablegen und höchstens alle 3 h neu abrufen. Bei fehlendem Netz
  mit dem Cache weiterrechnen und im UI kennzeichnen, wie alt die Daten sind.
- Nutze für den HTTP-Aufruf, was im Projekt bereits vorhanden ist. Falls noch keine
  HTTP-Bibliothek eingebunden ist, nimm `HttpURLConnection` + `org.json` statt eine neue
  Abhängigkeit einzuführen — und begründe die Entscheidung kurz im Commit.

## UI

- **`PlantsHomeActivity`** — Liste aller Gefäße als Karten, sortiert nach Dringlichkeit.
  Pro Karte: Name, Standort-Icon, Füllstand als Fortschrittsbalken (grün/amber/rot analog zur
  Markenfarbgebung), Klartextzeile "Gießen in 3 Tagen (Do)" bzw. "Überfällig seit gestern",
  Namen der Pflanzen darin, und ein prominenter **"Gegossen"**-Button direkt auf der Karte.
- **`PlanterEditActivity`** — Stammdaten anlegen/bearbeiten. Geometrie mit Live-Vorschau der
  errechneten Kapazität ("≈ 8 Liter nutzbarer Vorrat"), damit der Nutzer merkt, wenn er sich
  vertippt hat. Presets für gängige Topfgrößen anbieten, damit Durchmesser/Höhe nicht
  zwingend eingegeben werden müssen.
- **`PlanterDetailActivity`** — Verlauf des Füllstands als Kurve über 14 Tage inklusive
  Prognoseabschnitt (gestrichelt), Gieß-Historie, aktuelle Wetterdaten, aktive Faktoren
  transparent aufgelistet. Für die Kurve eine Canvas-basierte Custom View im Stil von
  `KennzeichenMapView` — keine Chart-Bibliothek als neue Abhängigkeit.
- **Gießen-Dialog** — Zeitpunkt (Standard: jetzt), optional Menge, und die drei
  Feedback-Optionen "war überfällig / passte / war noch feucht".

Alle Texte deutsch, alle Strings in `strings.xml`.

## Benachrichtigungen

Nach demselben Muster wie der Abfallkalender: `AlarmManager` + im Manifest registrierter
`BroadcastReceiver`, immer nur ein Alarm für das jeweils nächste fällige Gefäß, Kette wird beim
Auslösen neu gespannt, `BOOT_COMPLETED`-Receiver baut sie nach Neustart wieder auf. Uhrzeit
konfigurierbar, Standard 18:00. Mehrere gleichzeitig fällige Gefäße in einer Benachrichtigung
zusammenfassen. Berechtigungen `POST_NOTIFICATIONS` und `SCHEDULE_EXACT_ALARM` erst anfragen,
wenn die Erinnerung eingeschaltet wird.

Vor dem Neuplanen des Alarms die Wetterdaten aktualisieren, damit die Fälligkeit auf dem
aktuellen Forecast basiert.

## Phasen

Arbeite die Phasen einzeln ab. Nach jeder Phase anhalten, den Stand zusammenfassen und auf
Freigabe warten, bevor die nächste beginnt.

1. **Rechenkern + Tests.** Reines Kotlin, keine UI, keine Firestore-Anbindung.
   Alle Plausibilitätstests aus Abschnitt 7 grün.
2. **Datenschicht + CRUD-UI.** Gefäße und Pflanzen anlegen, Gießen protokollieren,
   Firestore-Regeln, Liste ohne Prognose.
3. **Wetteranbindung + Prognose + Kurve.** Ab hier ist das Modul praktisch nutzbar.
4. **Benachrichtigungen + Kalibrierung.**
5. **TP357-Anbindung.** BLE-GATT, Sensoren zuordnen, Mikroklima-Korrektur scharfschalten.
   Definiere in Phase 1 bereits das Interface (`SensorReadingSource`) und implementiere es
   zunächst als No-Op, damit der Rechenkern nicht angefasst werden muss.

## Randbedingungen

- Keine neuen Gradle-Abhängigkeiten ohne Rückfrage.
- Keine `localStorage`-artigen Zwischenspeicher: Zustand gehört nach Firestore, abgeleitete
  Werte werden gerechnet, nicht gespeichert. Einzige Ausnahme ist der Wetter-Cache.
- Das Modul muss vollständig funktionieren, auch wenn kein einziger Sensor eingerichtet ist.
- `README.md` um einen Abschnitt "Gießplaner" ergänzen, im Stil und Detailgrad der bestehenden
  Abschnitte — inklusive Datenmodell, Rechenweg, Quellen der Startwerte und der bekannten
  Grenzen des Modells.
- Bekannte Grenzen bitte auch im Code als Kommentar festhalten: Das Modell kennt weder Licht
  noch Wurzelmasse noch Wachstum, es geht von homogenem Substrat aus und bricht bei
  Selbstbewässerungstöpfen und Untersetzern mit stehendem Wasser. Die Kalibrierung fängt einen
  Teil davon auf, nicht alles.

Beginne damit, mir deinen Plan für Phase 1 vorzulegen — Klassenaufteilung, Signaturen,
Testfälle — bevor du Code schreibst.
