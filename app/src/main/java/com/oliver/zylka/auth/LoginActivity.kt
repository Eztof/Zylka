package com.oliver.zylka.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.oliver.zylka.MainActivity
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonLogin.setOnClickListener { attemptLogin() }
        binding.textForgotPassword.setOnClickListener { attemptPasswordReset() }
        binding.textRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        binding.inputLayoutEmail.error = null
        binding.inputLayoutPassword.error = null
        binding.textError.text = ""

        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()

        if (email.isEmpty()) {
            binding.inputLayoutEmail.error = getString(R.string.error_email_required)
            return
        }
        if (password.isEmpty()) {
            binding.inputLayoutPassword.error = getString(R.string.error_password_required)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                authRepository.login(email, password)
                goToMain()
            } catch (e: FirebaseAuthInvalidUserException) {
                binding.textError.text = getString(R.string.error_login_invalid_user)
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                binding.textError.text = getString(R.string.error_login_invalid_credentials)
            } catch (e: Exception) {
                binding.textError.text = e.localizedMessage ?: getString(R.string.error_login_invalid_credentials)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun attemptPasswordReset() {
        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        if (email.isEmpty()) {
            binding.inputLayoutEmail.error = getString(R.string.error_email_required)
            return
        }
        lifecycleScope.launch {
            try {
                authRepository.sendPasswordReset(email)
                binding.textError.text = getString(R.string.info_password_reset_sent)
            } catch (e: Exception) {
                binding.textError.text = e.localizedMessage.orEmpty()
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonLogin.isEnabled = !loading
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
