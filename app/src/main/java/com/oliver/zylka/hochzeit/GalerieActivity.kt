package com.oliver.zylka.hochzeit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Menu
import android.view.MenuItem
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.oliver.zylka.R
import com.oliver.zylka.data.AuthRepository
import com.oliver.zylka.data.hochzeit.Dokument
import com.oliver.zylka.data.hochzeit.DokumentRepository
import com.oliver.zylka.data.hochzeit.HochzeitStorageClient
import com.oliver.zylka.databinding.ActivityGalerieBinding
import com.oliver.zylka.databinding.DialogUpdateProgressBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.launch

/**
 * Bilder und PDFs (siehe [Dokument]/[HochzeitStorageClient]) in einem Grid (Bilder als
 * Thumbnails, PDFs als eigene volle Zeilen - [GalerieAdapter]). "+" lädt eine neue Datei hoch,
 * Fortschritt über dasselbe Dialog-Layout wie beim App-Update (`dialog_update_progress.xml`).
 * Tippen auf ein Bild öffnet [BildAnsichtActivity], auf ein PDF eine externe PDF-fähige App.
 */
class GalerieActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalerieBinding
    private val authRepository = AuthRepository()
    private val dokumentRepository by lazy { DokumentRepository(applicationContext) }
    private val storageClient by lazy { HochzeitStorageClient(applicationContext) }
    private val adapter by lazy {
        GalerieAdapter(
            storageClient = storageClient,
            scope = lifecycleScope,
            onClickBild = { dokument ->
                startActivity(BildAnsichtActivity.intent(this, dokument.downloadUrl, dokument.name))
            },
            onClickPdf = { dokument -> openPdf(dokument) },
        )
    }

    private val pickDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) uploadDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalerieBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = getString(R.string.hochzeit_galerie_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val gridLayoutManager = GridLayoutManager(this, SPAN_COUNT)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (adapter.isFullSpan(position)) SPAN_COUNT else 1
        }
        binding.recyclerGalerie.layoutManager = gridLayoutManager
        binding.recyclerGalerie.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dokumentRepository.observeDokumente().collect { dokumente ->
                    val sortiert = dokumente.sortedByDescending { it.hochgeladenAm?.time ?: 0L }
                    adapter.submitList(sortiert)
                    binding.textEmpty.isVisible = sortiert.isEmpty()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_galerie, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_add_dokument) {
            pickDocument.launch(arrayOf("image/*", "application/pdf"))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun uploadDocument(uri: Uri) {
        val uid = authRepository.currentUser?.uid ?: return
        val mimeType = contentResolver.getType(uri)
        if (mimeType == null || !(mimeType.startsWith("image/") || mimeType == "application/pdf")) {
            Toast.makeText(this, R.string.hochzeit_galerie_upload_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val displayName = queryDisplayName(uri) ?: getString(R.string.hochzeit_galerie_unnamed_file)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "bin"

        val progressBinding = DialogUpdateProgressBinding.inflate(layoutInflater)
        progressBinding.textProgressLabel.text = getString(R.string.hochzeit_galerie_uploading)
        val progressDialog = AlertDialog.Builder(this)
            .setView(progressBinding.root)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val id = dokumentRepository.newDocumentId()
                val storagePfad = "hochzeitsplaner/$id.$extension"
                val downloadUrl = storageClient.upload(uri, storagePfad, mimeType) { percent ->
                    progressBinding.progressBar.progress = percent
                    progressBinding.textProgressPercent.text = getString(R.string.update_progress_percent, percent)
                }
                dokumentRepository.save(
                    Dokument(
                        id = id,
                        uid = uid,
                        name = displayName,
                        mimeTyp = mimeType,
                        storagePfad = storagePfad,
                        downloadUrl = downloadUrl,
                    ),
                )
            } catch (e: Exception) {
                Toast.makeText(this@GalerieActivity, R.string.hochzeit_galerie_upload_failed, Toast.LENGTH_LONG).show()
            } finally {
                progressDialog.dismiss()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }

    private fun openPdf(dokument: Dokument) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(dokument.downloadUrl), "application/pdf")
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.hochzeit_galerie_no_pdf_app, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val SPAN_COUNT = 3

        fun intent(context: Context): Intent = Intent(context, GalerieActivity::class.java)
    }
}
