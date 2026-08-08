package com.oliver.zylka

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
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
import com.oliver.zylka.databinding.DialogUpdateProgressBinding
import com.oliver.zylka.kennzeichen.KennzeichenHomeActivity
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

        binding.cardKennzeichen.setOnClickListener {
            startActivity(KennzeichenHomeActivity.intent(this))
        }

        checkForUpdate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            authRepository.logout()
            goToLogin()
            return true
        }
        return super.onOptionsItemSelected(item)
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
        val progressBinding = DialogUpdateProgressBinding.inflate(layoutInflater)
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setView(progressBinding.root)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                updateManager.downloadAndInstall(update.apkUrl, update.versionName) { percent ->
                    progressBinding.progressBar.progress = percent
                    progressBinding.textProgressPercent.text =
                        getString(R.string.update_progress_percent, percent)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
            } finally {
                progressDialog.dismiss()
            }
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
