# Zylka

Android-App mit Anmeldung (Firebase Authentication) und Firestore als Datenbank.

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
  angezeigt; bei Bestätigung wird die neue APK aus Firebase Storage
  heruntergeladen und die Installation angestoßen. Details siehe Abschnitt
  "Neue Version veröffentlichen" weiter unten.

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
│   └── UpdateRepository.kt    # Liest app_config/version aus Firestore
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
