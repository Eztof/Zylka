package com.oliver.zylka.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.oliver.zylka.MainActivity
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.databinding.ActivityRegisterBinding
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRegister.setOnClickListener { attemptRegister() }
        binding.textLoginLink.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        binding.inputLayoutEmail.error = null
        binding.inputLayoutPassword.error = null
        binding.inputLayoutPasswordConfirm.error = null
        binding.textError.text = ""

        val email = binding.inputEmail.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        val passwordConfirm = binding.inputPasswordConfirm.text?.toString().orEmpty()

        if (email.isEmpty()) {
            binding.inputLayoutEmail.error = getString(R.string.error_email_required)
            return
        }
        if (password.length < 6) {
            binding.inputLayoutPassword.error = getString(R.string.error_password_too_short)
            return
        }
        if (password != passwordConfirm) {
            binding.inputLayoutPasswordConfirm.error = getString(R.string.error_passwords_not_matching)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                authRepository.register(email, password)
                goToMain()
            } catch (e: FirebaseAuthUserCollisionException) {
                binding.textError.text = getString(R.string.error_email_in_use)
            } catch (e: FirebaseAuthWeakPasswordException) {
                binding.textError.text = getString(R.string.error_password_too_short)
            } catch (e: Exception) {
                binding.textError.text = e.localizedMessage ?: getString(R.string.error_email_in_use)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonRegister.isEnabled = !loading
        binding.progressRegister.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
