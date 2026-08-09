package com.oliver.zylka.plants

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.oliver.zylka.R
import com.oliver.zylka.data.plants.PlantWaterCalculator

/**
 * Zeichnet die simulierte Vorratskurve eines Topfs als gefüllte Linie - der Zeitraum ergibt
 * sich direkt aus der Open-Meteo-Abfrage (`past_days=7&forecast_days=7`, siehe
 * [com.oliver.zylka.data.plants.WeatherRepository]): die linke Hälfte ist tatsächlicher
 * Verlauf, die rechte Hälfte (ab der "Jetzt"-Linie) die Prognose. Grundgerüst angelehnt an
 * `KennzeichenMapView`, hier aber bewusst ohne Pan/Zoom.
 */
class PotWaterLevelChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var points: List<Pair<Long, Double>> = emptyList()
    private var kapazitaetMm: Double = 0.0
    private var nowEpochMillis: Long = 0L
    private var wateringTimestamps: List<Long> = emptyList()

    private val density = context.resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        color = ContextCompat.getColor(context, R.color.brand_primary)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.brand_primary_container)
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = ContextCompat.getColor(context, R.color.brand_error)
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = ContextCompat.getColor(context, R.color.brand_outline)
    }
    private val wateringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.brand_secondary)
    }

    fun setData(
        points: List<Pair<Long, Double>>,
        kapazitaetMm: Double,
        nowEpochMillis: Long,
        wateringTimestamps: List<Long>,
    ) {
        this.points = points
        this.kapazitaetMm = kapazitaetMm
        this.nowEpochMillis = nowEpochMillis
        this.wateringTimestamps = wateringTimestamps
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2 || kapazitaetMm <= 0.0) return

        val paddingPx = 8f * density
        val left = paddingPx
        val right = width - paddingPx
        val top = paddingPx
        val bottom = height - paddingPx
        if (right <= left || bottom <= top) return

        val minTime = points.first().first
        val maxTime = points.last().first
        val timeSpan = (maxTime - minTime).coerceAtLeast(1)

        fun x(time: Long): Float = left + (time - minTime).toFloat() / timeSpan * (right - left)
        fun y(vorrat: Double): Float = bottom - (vorrat / kapazitaetMm).toFloat().coerceIn(0f, 1f) * (bottom - top)

        // Gießschwelle (50 % der Kapazität)
        val schwelleY = y(kapazitaetMm * PlantWaterCalculator.GIESSSCHWELLE_ANTEIL)
        canvas.drawLine(left, schwelleY, right, schwelleY, thresholdPaint)

        // Vorratskurve als gefüllte Fläche
        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { index, (time, vorrat) ->
            val px = x(time)
            val py = y(vorrat)
            if (index == 0) {
                linePath.moveTo(px, py)
                fillPath.moveTo(px, bottom)
                fillPath.lineTo(px, py)
            } else {
                linePath.lineTo(px, py)
                fillPath.lineTo(px, py)
            }
        }
        fillPath.lineTo(x(maxTime), bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        // "Jetzt"-Markierung
        if (nowEpochMillis in minTime..maxTime) {
            val nowX = x(nowEpochMillis)
            canvas.drawLine(nowX, top, nowX, bottom, nowPaint)
        }

        // Gieß-Ereignisse als Punkte am unteren Rand
        val radius = 5f * density
        for (wateredAt in wateringTimestamps) {
            if (wateredAt in minTime..maxTime) {
                canvas.drawCircle(x(wateredAt), bottom, radius, wateringPaint)
            }
        }
    }
}
