package com.oliver.zylka.hochzeit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.oliver.zylka.R
import com.oliver.zylka.data.hochzeit.Dienstleister
import com.oliver.zylka.data.hochzeit.DienstleisterRepository
import com.oliver.zylka.data.hochzeit.DienstleisterStatus
import com.oliver.zylka.data.hochzeit.Gast
import com.oliver.zylka.data.hochzeit.GastPrioritaet
import com.oliver.zylka.data.hochzeit.GastRepository
import com.oliver.zylka.data.hochzeit.TerminOption
import com.oliver.zylka.data.hochzeit.TerminRepository
import com.oliver.zylka.data.hochzeit.TerminStatus
import com.oliver.zylka.data.hochzeit.TerminTyp
import com.oliver.zylka.databinding.ActivityHochzeitHomeBinding
import com.oliver.zylka.databinding.ItemActionCardBinding
import com.oliver.zylka.util.applyStatusBarTopInset
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Hub des Hochzeitsplaners (Vorbild `KennzeichenHomeActivity`): Status-Karte oben (✓/?/✗ je
 * Termin-Typ, Gäste-/Dienstleister-Zahlen, Kosten-Summe der gebuchten Dienstleister), darunter
 * vier [item_action_card]-Kacheln zu den Unterbereichen.
 */
class HochzeitHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHochzeitHomeBinding
    private val terminRepository = TerminRepository()
    private val gastRepository = GastRepository()
    private val dienstleisterRepository = DienstleisterRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHochzeitHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.applyStatusBarTopInset()
        title = getString(R.string.feature_hochzeit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setUpActionCard(binding.cardTermine, R.drawable.ic_calendar, R.string.hochzeit_action_termine_title) {
            startActivity(TerminUebersichtActivity.intent(this))
        }
        setUpActionCard(binding.cardGaeste, R.drawable.ic_guests, R.string.hochzeit_action_gaeste_title) {
            startActivity(GastListeActivity.intent(this))
        }
        setUpActionCard(binding.cardDienstleister, R.drawable.ic_vendor, R.string.hochzeit_action_dienstleister_title) {
            startActivity(DienstleisterListeActivity.intent(this))
        }
        setUpActionCard(binding.cardGalerie, R.drawable.ic_gallery, R.string.hochzeit_action_galerie_title) {
            startActivity(GalerieActivity.intent(this))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    terminRepository.observeTermine(),
                    gastRepository.observeGaeste(),
                    dienstleisterRepository.observeDienstleister(),
                ) { termine, gaeste, dienstleister -> Triple(termine, gaeste, dienstleister) }
                    .collect { (termine, gaeste, dienstleister) -> updateStatus(termine, gaeste, dienstleister) }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun updateStatus(termine: List<TerminOption>, gaeste: List<Gast>, dienstleister: List<Dienstleister>) {
        updateTerminSymbol(binding.textStatusStandesamtSymbol, termine, TerminTyp.STANDESAMT)
        updateTerminSymbol(binding.textStatusKircheSymbol, termine, TerminTyp.KIRCHE)
        updateTerminSymbol(binding.textStatusLocationSymbol, termine, TerminTyp.LOCATION)

        binding.textStatusGaeste.text = getString(
            R.string.hochzeit_gast_summary,
            gaeste.size,
            gaeste.count { it.prioritaet == GastPrioritaet.SICHER },
            gaeste.count { it.prioritaet == GastPrioritaet.MITTEL },
            gaeste.count { it.prioritaet == GastPrioritaet.MAEH },
        )

        val gebucht = dienstleister.count { it.status == DienstleisterStatus.GEBUCHT }
        val offen = dienstleister.count {
            it.status != DienstleisterStatus.GEBUCHT && it.status != DienstleisterStatus.ABGESAGT
        }
        binding.textStatusDienstleister.text = getString(R.string.hochzeit_status_dienstleister_value, gebucht, offen)

        val summeGebucht = dienstleister.filter { it.status == DienstleisterStatus.GEBUCHT }.sumOf { it.kostenEuro ?: 0.0 }
        binding.textStatusKosten.text = String.format(Locale.GERMANY, "%.2f €", summeGebucht)
    }

    private fun updateTerminSymbol(view: TextView, termine: List<TerminOption>, typ: TerminTyp) {
        val fuerTyp = termine.filter { it.terminTyp == typ }
        val (symbol, color) = when {
            fuerTyp.any { it.status == TerminStatus.BESTAETIGT } -> "✓" to R.color.brand_primary
            fuerTyp.any { it.status == TerminStatus.VORGESCHLAGEN } -> "?" to R.color.brand_secondary
            else -> "✗" to R.color.brand_outline
        }
        view.text = symbol
        view.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun setUpActionCard(
        card: ItemActionCardBinding,
        @androidx.annotation.DrawableRes icon: Int,
        @androidx.annotation.StringRes title: Int,
        onClick: () -> Unit,
    ) {
        card.iconAction.setImageResource(icon)
        card.textActionTitle.setText(title)
        card.root.setOnClickListener { onClick() }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, HochzeitHomeActivity::class.java)
    }
}
