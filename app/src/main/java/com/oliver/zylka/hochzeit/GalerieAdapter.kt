package com.oliver.zylka.hochzeit

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oliver.zylka.data.hochzeit.Dokument
import com.oliver.zylka.data.hochzeit.HochzeitStorageClient
import com.oliver.zylka.databinding.ItemDokumentBildBinding
import com.oliver.zylka.databinding.ItemDokumentPdfBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Bilder und PDFs im selben Grid (siehe [GalerieActivity]) - zwei ViewHolder-Typen über
 * [getItemViewType], PDF-Zeilen sind volle Grid-Zeilen ([isFullSpan]). Bild-Thumbnails werden
 * einzeln nachgeladen ([HochzeitStorageClient.loadBitmap]) und in [bitmapCache] gehalten, damit
 * beim Scrollen nicht erneut heruntergeladen wird - [scope] bindet die Ladevorgänge an die
 * Lebenszeit der Activity. */
class GalerieAdapter(
    private val storageClient: HochzeitStorageClient,
    private val scope: CoroutineScope,
    private val onClickBild: (Dokument) -> Unit,
    private val onClickPdf: (Dokument) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var dokumente: List<Dokument> = emptyList()
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun submitList(newDokumente: List<Dokument>) {
        dokumente = newDokumente
        notifyDataSetChanged()
    }

    fun isFullSpan(position: Int): Boolean = dokumente[position].istPdf

    override fun getItemCount(): Int = dokumente.size

    override fun getItemViewType(position: Int): Int = if (dokumente[position].istBild) VIEW_TYPE_BILD else VIEW_TYPE_PDF

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_BILD) {
            BildViewHolder(ItemDokumentBildBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            PdfViewHolder(ItemDokumentPdfBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val dokument = dokumente[position]
        when (holder) {
            is BildViewHolder -> holder.bind(dokument)
            is PdfViewHolder -> holder.bind(dokument)
        }
    }

    inner class BildViewHolder(private val binding: ItemDokumentBildBinding) : RecyclerView.ViewHolder(binding.root) {

        private var loadJob: Job? = null

        fun bind(dokument: Dokument) {
            loadJob?.cancel()
            val cached = bitmapCache[dokument.downloadUrl]
            if (cached != null) {
                binding.imageThumbnail.setImageBitmap(cached)
            } else {
                binding.imageThumbnail.setImageDrawable(null)
                loadJob = scope.launch {
                    val bitmap = storageClient.loadBitmap(dokument.downloadUrl) ?: return@launch
                    bitmapCache[dokument.downloadUrl] = bitmap
                    binding.imageThumbnail.setImageBitmap(bitmap)
                }
            }
            binding.root.setOnClickListener { onClickBild(dokument) }
        }
    }

    inner class PdfViewHolder(private val binding: ItemDokumentPdfBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(dokument: Dokument) {
            binding.textDokumentName.text = dokument.name
            binding.root.setOnClickListener { onClickPdf(dokument) }
        }
    }

    companion object {
        private const val VIEW_TYPE_BILD = 0
        private const val VIEW_TYPE_PDF = 1
    }
}
