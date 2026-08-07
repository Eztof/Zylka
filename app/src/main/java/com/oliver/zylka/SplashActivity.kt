package com.oliver.zylka

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.oliver.zylka.auth.LoginActivity
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.databinding.ActivitySplashBinding

/**
 * Startbildschirm der App. Prüft, ob bereits eine Firebase-Sitzung auf dem
 * Gerät gespeichert ist. Falls ja, wird direkt zu [MainActivity] gesprungen -
 * so muss man sich nicht bei jedem App-Start neu anmelden. Falls nein, geht
 * es zu [LoginActivity].
 */
class SplashActivity : AppCompatActivity() {

    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val destination = if (authRepository.currentUser != null) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }
}
