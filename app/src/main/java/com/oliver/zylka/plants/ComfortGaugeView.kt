package com.oliver.zylka.plants

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.oliver.zylka.R

/**
 * Horizontaler Gradient-Balken (trocken → Komfortzone → feucht) mit einem Knob an der
 * aktuellen Luftfeuchte-Position - Vorbild: das Komfort-Gauge aus der ThermoPro-App, hier in
 * unserem Farbschema (Fehler-Rot/Marken-Grün/Tertiär-Blau statt Regenbogen). Grundgerüst wie
 * [PotWaterLevelChartView], nur deutlich einfacher (ein Wert statt einer Kurve). Genutzt sowohl
 * im Sensor-Karten-Layout ([SensorAdapter]) als auch im Kopf von `SensorDetailActivity`.
 */
class ComfortGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var humidityPercent: Double? = null

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val knobRadius = dp(9f)
    private val barHeight = dp(10f)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.brand_surface)
    }
    private val knobStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = ContextCompat.getColor(context, R.color.brand_on_surface)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
    }

    private val colorDry = ContextCompat.getColor(context, R.color.brand_error)
    private val colorComfort = ContextCompat.getColor(context, R.color.brand_primary)
    private val colorHumid = ContextCompat.getColor(context, R.color.brand_tertiary)

    private val labelDry = context.getString(R.string.sensor_gauge_dry)
    private val labelComfort = context.getString(R.string.sensor_gauge_comfort)
    private val labelHumid = context.getString(R.string.sensor_gauge_humid)

    fun setHumidity(percent: Double?) {
        humidityPercent = percent
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val measuredHeight = (knobRadius * 2 + dp(4f) + labelPaint.textSize + dp(2f)).toInt()
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = knobRadius
        val right = width - knobRadius
        if (right <= left) return
        val barTop = knobRadius - barHeight / 2f
        val barBottom = knobRadius + barHeight / 2f

        barPaint.shader = LinearGradient(
            left, 0f, right, 0f,
            intArrayOf(colorDry, colorComfort, colorHumid),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(RectF(left, barTop, right, barBottom), barHeight / 2f, barHeight / 2f, barPaint)

        val percent = humidityPercent
        if (percent != null) {
            val fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
            val knobX = left + fraction * (right - left)
            canvas.drawCircle(knobX, knobRadius, knobRadius, knobFillPaint)
            canvas.drawCircle(knobX, knobRadius, knobRadius - knobStrokePaint.strokeWidth / 2f, knobStrokePaint)
        }

        val labelY = knobRadius * 2 + dp(4f) + labelPaint.textSize
        labelPaint.color = colorDry
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(labelDry, left, labelY, labelPaint)
        labelPaint.color = colorComfort
        labelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(labelComfort, (left + right) / 2f, labelY, labelPaint)
        labelPaint.color = colorHumid
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(labelHumid, right, labelY, labelPaint)
    }
}
