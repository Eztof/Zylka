package com.oliver.zylka.hochzeit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.oliver.zylka.R
import com.oliver.zylka.data.hochzeit.HochzeitStorageClient
import com.oliver.zylka.databinding.ActivityBildAnsichtBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/** Erstes Bild-Vollbild-Fenster im Projekt: lädt die Download-URL per [HochzeitStorageClient] und
 * zeigt sie `fitCenter` an - kein Pinch-Zoom in dieser Version (mögliche spätere Ergänzung). */
class BildAnsichtActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBildAnsichtBinding
    private val storageClient by lazy { HochzeitStorageClient(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBildAnsichtBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = intent.getStringExtra(EXTRA_NAME).orEmpty()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val downloadUrl = intent.getStringExtra(EXTRA_DOWNLOAD_URL).orEmpty()
        lifecycleScope.launch {
            val bitmap = storageClient.loadBitmap(downloadUrl)
            binding.progressLoading.isVisible = false
            if (bitmap != null) {
                binding.imageFull.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this@BildAnsichtActivity, R.string.hochzeit_galerie_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val EXTRA_DOWNLOAD_URL = "download_url"
        private const val EXTRA_NAME = "name"

        fun intent(context: Context, downloadUrl: String, name: String): Intent =
            Intent(context, BildAnsichtActivity::class.java)
                .putExtra(EXTRA_DOWNLOAD_URL, downloadUrl)
                .putExtra(EXTRA_NAME, name)
    }
}
