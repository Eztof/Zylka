import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// Release-Signatur: wird lokal aus keystore.properties gelesen, falls
// vorhanden (siehe README.md, Abschnitt "Release-Signatur einrichten").
// Diese Datei ist bewusst nicht im Repo - ohne sie entsteht beim
// Release-Build einfach eine unsignierte APK.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasReleaseSigning) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.oliver.zylka"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.oliver.zylka"
        minSdk = 36
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // Ab AGP 9 ist Kotlin-Unterstützung eingebaut (kein separates
    // "org.jetbrains.kotlin.android"-Plugin mehr nötig). Das JVM-Target für
    // Kotlin wird automatisch von compileOptions oben übernommen.
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.play.services)

    // Firebase (BoM verwaltet die Versionen aller Firebase-Bibliotheken)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
