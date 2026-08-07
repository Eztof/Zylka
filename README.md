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

## Projektstruktur

```
app/src/main/java/com/oliver/zylka/
├── SplashActivity.kt        # Prüft gespeicherte Sitzung, leitet weiter
├── MainActivity.kt          # Startbildschirm nach dem Login
├── auth/
│   ├── LoginActivity.kt
│   └── RegisterActivity.kt
└── data/
    └── AuthRepository.kt    # Kapselt FirebaseAuth + Firestore-Profil
```

## Nächste mögliche Schritte

- Gemeinsame Daten (z. B. für dich, deine Partnerin und später Freunde) in
  weiteren Firestore-Collections modellieren.
- Freigabe/Einladung weiterer Nutzer (z. B. Registrierung ggf. auf
  bestimmte E-Mail-Adressen beschränken, falls die App nicht offen für
  jeden sein soll).
- App-Icon und Farben (`app/src/main/res/values/colors.xml`,
  `mipmap-*/ic_launcher*`) anpassen.
