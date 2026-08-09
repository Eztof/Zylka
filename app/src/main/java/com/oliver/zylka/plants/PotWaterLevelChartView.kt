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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Zeichnet die simulierte Vorratskurve eines Topfs als gefüllte Linie mit Achsenbeschriftung
 * (0/Schwelle/100 % links, Start-/End-/Jetzt-Datum unten) - der Zeitraum ergibt sich direkt
 * aus der Open-Meteo-Abfrage (`past_days=7&forecast_days=7`, siehe
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
    private var schwelleAnteil: Double = PlantWaterCalculator.GIESSSCHWELLE_ANTEIL
    private var nowEpochMillis: Long = 0L
    private var wateringTimestamps: List<Long> = emptyList()

    private val density = context.resources.displayMetrics.density

    private fun dp(value: Float): Float = value * density
    private val dateFormat = SimpleDateFormat("d.M.", Locale.GERMANY)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        color = ContextCompat.getColor(context, R.color.brand_primary)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.brand_primary_container)
    }
    private val thresholdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = ContextCompat.getColor(context, R.color.brand_error)
        pathEffect = DashPathEffect(floatArrayOf(dp(10f), dp(8f)), 0f)
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.brand_outline)
    }
    private val wateringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.brand_secondary)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brand_on_surface_variant)
        textSize = dp(14f)
    }
    private val thresholdLabelPaint = Paint(labelPaint).apply {
        color = ContextCompat.getColor(context, R.color.brand_error)
    }

    fun setData(
        points: List<Pair<Long, Double>>,
        kapazitaetMm: Double,
        schwelleAnteil: Double,
        nowEpochMillis: Long,
        wateringTimestamps: List<Long>,
    ) {
        this.points = points
        this.kapazitaetMm = kapazitaetMm
        this.schwelleAnteil = schwelleAnteil
        this.nowEpochMillis = nowEpochMillis
        this.wateringTimestamps = wateringTimestamps
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2 || kapazitaetMm <= 0.0) return

        val leftLabelWidth = labelPaint.measureText("100 %")
        val left = leftLabelWidth + dp(10f)
        val right = width - dp(6f)
        // "top" ist zugleich die Gitterlinie für 100 % Vorrat - darüber bleibt genau eine
        // Zeile Platz für deren Beschriftung, ohne am oberen Rand der View abgeschnitten zu
        // werden.
        val top = labelPaint.textSize + dp(4f)
        val bottom = height - (labelPaint.textSize + dp(10f))
        if (right <= left || bottom <= top) return

        val minTime = points.first().first
        val maxTime = points.last().first
        val timeSpan = (maxTime - minTime).coerceAtLeast(1)

        fun x(time: Long): Float = left + (time - minTime).toFloat() / timeSpan * (right - left)
        fun y(vorrat: Double): Float = bottom - (vorrat / kapazitaetMm).toFloat().coerceIn(0f, 1f) * (bottom - top)

        drawYAxis(canvas, left, right, top, bottom, y(0.0), y(kapazitaetMm * schwelleAnteil))
        drawCurve(canvas, left, bottom, ::x, ::y)
        drawNowMarker(canvas, top, bottom, ::x)
        drawWaterings(canvas, minTime, maxTime, bottom, ::x)
        drawXAxis(canvas, left, right, bottom, minTime, maxTime)
    }

    private fun drawYAxis(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        yZero: Float,
        ySchwelle: Float,
    ) {
        labelPaint.textAlign = Paint.Align.RIGHT
        val labelX = left - dp(8f)
        canvas.drawText("0 %", labelX, yZero - dp(3f), labelPaint)
        canvas.drawText("100 %", labelX, labelPaint.textSize, labelPaint)

        canvas.drawLine(left, ySchwelle, right, ySchwelle, thresholdPaint)

        // Schwelle-Beschriftung nur zeichnen, wenn sie genug Abstand zu den beiden anderen
        // Marken hat - sonst würden sich die Texte bei einer Schwelle nahe 0 % oder 100 %
        // überlappen.
        val minAbstand = labelPaint.textSize * 1.4f
        if (abs(ySchwelle - yZero) > minAbstand && abs(ySchwelle - top) > minAbstand) {
            thresholdLabelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                "${(schwelleAnteil * 100).toInt()} %",
                labelX,
                ySchwelle + thresholdLabelPaint.textSize / 3f,
                thresholdLabelPaint,
            )
        }
    }

    private fun drawCurve(canvas: Canvas, left: Float, bottom: Float, x: (Long) -> Float, y: (Double) -> Float) {
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
        fillPath.lineTo(x(points.last().first), bottom)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }

    private fun drawNowMarker(canvas: Canvas, top: Float, bottom: Float, x: (Long) -> Float) {
        val minTime = points.first().first
        val maxTime = points.last().first
        if (nowEpochMillis in minTime..maxTime) {
            canvas.drawLine(x(nowEpochMillis), top, x(nowEpochMillis), bottom, nowPaint)
        }
    }

    private fun drawWaterings(canvas: Canvas, minTime: Long, maxTime: Long, bottom: Float, x: (Long) -> Float) {
        val radius = dp(6f)
        for (wateredAt in wateringTimestamps) {
            if (wateredAt in minTime..maxTime) {
                canvas.drawCircle(x(wateredAt), bottom, radius, wateringPaint)
            }
        }
    }

    private fun drawXAxis(canvas: Canvas, left: Float, right: Float, bottom: Float, minTime: Long, maxTime: Long) {
        val labelY = bottom + labelPaint.textSize + dp(6f)
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dateFormat.format(Date(minTime)), left, labelY, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateFormat.format(Date(maxTime)), right, labelY, labelPaint)
        if (nowEpochMillis in minTime..maxTime) {
            labelPaint.textAlign = Paint.Align.CENTER
            val timeSpan = (maxTime - minTime).coerceAtLeast(1)
            val nowX = left + (nowEpochMillis - minTime).toFloat() / timeSpan * (right - left)
            canvas.drawText(dateFormat.format(Date(nowEpochMillis)), nowX, labelY, labelPaint)
        }
    }
}
