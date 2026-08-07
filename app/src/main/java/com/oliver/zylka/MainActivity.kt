package com.oliver.zylka

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.oliver.zylka.auth.LoginActivity
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.UpdateInfo
import com.oliver.zylka.data.UpdateRepository
import com.oliver.zylka.databinding.ActivityMainBinding
import com.oliver.zylka.update.UpdateManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var updateManager: UpdateManager
    private val authRepository = AuthRepository()
    private val updateRepository = UpdateRepository()
    private var pendingUpdate: UpdateInfo? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val update = pendingUpdate ?: return@registerForActivityResult
        if (updateManager.canInstallUnknownApps()) {
            startDownload(update)
        } else {
            Toast.makeText(this, R.string.update_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        updateManager = UpdateManager(this)

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

        checkForUpdate()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            try {
                val latest = updateRepository.fetchLatestVersion() ?: return@launch
                if (updateManager.isNewerThanInstalled(latest.versionCode)) {
                    showUpdateDialog(latest)
                }
            } catch (e: Exception) {
                // Update-Prüfung ist nicht kritisch für die App-Nutzung -
                // im Fehlerfall (z. B. kein Netz) einfach ignorieren.
            }
        }
    }

    private fun showUpdateDialog(update: UpdateInfo) {
        pendingUpdate = update
        val message = buildString {
            append(getString(R.string.update_message, update.versionName))
            if (!update.notes.isNullOrBlank()) {
                append("\n\n")
                append(update.notes)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_update_now) { _, _ -> onUpdateAccepted(update) }
            .setNegativeButton(R.string.action_update_later, null)
            .show()
    }

    private fun onUpdateAccepted(update: UpdateInfo) {
        if (updateManager.canInstallUnknownApps()) {
            startDownload(update)
        } else {
            Toast.makeText(this, R.string.update_permission_hint, Toast.LENGTH_LONG).show()
            installPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun startDownload(update: UpdateInfo) {
        Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        updateManager.downloadAndInstall(update.apkUrl, update.versionName)
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
