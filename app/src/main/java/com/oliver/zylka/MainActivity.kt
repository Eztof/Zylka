package com.oliver.zylka

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.oliver.zylka.auth.LoginActivity
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = authRepository.currentUser
        if (user == null) {
            // Sicherheitsnetz: falls die Sitzung zwischenzeitlich ungültig wurde.
            goToLogin()
            return
        }
        binding.textWelcome.text = getString(R.string.welcome_message, user.email)

        binding.buttonLogout.setOnClickListener {
            authRepository.logout()
            goToLogin()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
