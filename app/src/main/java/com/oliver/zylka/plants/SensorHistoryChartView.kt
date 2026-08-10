package com.oliver.zylka.plants

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gefüllte Linienkurve für einen Temperatur- oder Feuchte-Verlauf über die Zeit, mit
 * hervorgehobenem Min-/Max-Punkt direkt auf der Kurve (Wert-Label) - Vorbild
 * [PotWaterLevelChartView], hier aber ohne Schwellenwert-Linie/Gieß-Marker und mit
 * austauschbarer Farbe/Einheit statt fest auf den Gießplaner zugeschnitten. Genutzt zweimal in
 * `SensorDetailActivity` (Temperatur, Luftfeuchte).
 */
class SensorHistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var points: List<Pair<Long, Double>> = emptyList()
    private var unitSuffix: String = ""

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
    }
    private val markerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        isFakeBoldText = true
    }

    /** Muss vor der ersten Anzeige einmal gesetzt werden - passt Linie/Füllung/Marker an das
     * jeweilige Diagramm (Temperatur/Feuchte) und unser Farbschema an. */
    fun setColors(lineColor: Int, fillColor: Int, markerColor: Int, axisLabelColor: Int) {
        linePaint.color = lineColor
        fillPaint.color = fillColor
        markerPaint.color = markerColor
        markerLabelPaint.color = markerColor
        axisLabelPaint.color = axisLabelColor
        invalidate()
    }

    fun setData(points: List<Pair<Long, Double>>, unitSuffix: String) {
        this.points = points
        this.unitSuffix = unitSuffix
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2) return

        val left = dp(4f)
        val right = width - dp(4f)
        val top = markerLabelPaint.textSize + dp(6f)
        val bottom = height - (axisLabelPaint.textSize + dp(10f))
        if (right <= left || bottom <= top) return

        val minTime = points.first().first
        val maxTime = points.last().first
        val timeSpan = (maxTime - minTime).coerceAtLeast(1)
        val minValue = points.minOf { it.second }
        val maxValue = points.maxOf { it.second }
        val valueSpan = (maxValue - minValue).coerceAtLeast(0.1)
        // Etwas Luft über/unter der Kurve lassen, damit die Min-/Max-Marker nicht am Rand kleben.
        val padding = valueSpan * 0.2
        val loValue = minValue - padding
        val hiValue = maxValue + padding

        fun x(time: Long): Float = left + (time - minTime).toFloat() / timeSpan * (right - left)
        fun y(value: Double): Float =
            bottom - ((value - loValue) / (hiValue - loValue)).toFloat().coerceIn(0f, 1f) * (bottom - top)

        drawCurve(canvas, bottom, ::x, ::y)
        drawMinMax(canvas, minValue, maxValue, ::x, ::y)
        drawXAxis(canvas, left, right, bottom, minTime, maxTime)
    }

    private fun drawCurve(canvas: Canvas, bottom: Float, x: (Long) -> Float, y: (Double) -> Float) {
        val linePath = Path()
        val fillPath = Path()
        points.forEachIndexed { index, (time, value) ->
            val px = x(time)
            val py = y(value)
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

    private fun drawMinMax(canvas: Canvas, minValue: Double, maxValue: Double, x: (Long) -> Float, y: (Double) -> Float) {
        val minPoint = points.minByOrNull { it.second } ?: return
        val maxPoint = points.maxByOrNull { it.second } ?: return
        val radius = dp(4f)

        markerLabelPaint.textAlign = Paint.Align.CENTER
        canvas.drawCircle(x(maxPoint.first), y(maxPoint.second), radius, markerPaint)
        canvas.drawText(
            formatValue(maxValue),
            x(maxPoint.first).coerceIn(dp(24f), width - dp(24f)),
            (y(maxPoint.second) - radius - dp(4f)).coerceAtLeast(markerLabelPaint.textSize),
            markerLabelPaint,
        )

        canvas.drawCircle(x(minPoint.first), y(minPoint.second), radius, markerPaint)
        canvas.drawText(
            formatValue(minValue),
            x(minPoint.first).coerceIn(dp(24f), width - dp(24f)),
            y(minPoint.second) + radius + markerLabelPaint.textSize,
            markerLabelPaint,
        )
    }

    private fun drawXAxis(canvas: Canvas, left: Float, right: Float, bottom: Float, minTime: Long, maxTime: Long) {
        val labelY = bottom + axisLabelPaint.textSize + dp(6f)
        axisLabelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(timeFormat.format(Date(minTime)), left, labelY, axisLabelPaint)
        axisLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(timeFormat.format(Date(maxTime)), right, labelY, axisLabelPaint)
    }

    private fun formatValue(value: Double): String = String.format(Locale.GERMANY, "%.1f%s", value, unitSuffix)
}
