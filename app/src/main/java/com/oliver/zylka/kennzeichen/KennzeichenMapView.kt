package com.oliver.zylka.kennzeichen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.oliver.zylka.data.kennzeichen.GeoBounds
import com.oliver.zylka.data.kennzeichen.GeoShape
import kotlin.math.cos

/**
 * Draws a set of [GeoShape]s as a shaded map, colored per-shape via a caller-supplied
 * function. Uses a simple equirectangular projection with a cosine(latitude) correction
 * so country-scale shapes aren't visibly stretched, and sizes itself to roughly match the
 * shapes' real aspect ratio once they're known.
 */
class KennzeichenMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var shapes: List<GeoShape> = emptyList()
    private var colorProvider: (GeoShape) -> Int = { Color.LTGRAY }
    private var aspectRatio: Float = 1.3f // width / height

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#9E9E9E")
    }
    private val path = Path()

    fun setData(shapes: List<GeoShape>, colorProvider: (GeoShape) -> Int) {
        this.shapes = shapes
        this.colorProvider = colorProvider
        if (shapes.isNotEmpty()) {
            val bounds = GeoBounds.of(shapes)
            val latCorrection = cos(Math.toRadians(bounds.centerLat)).coerceAtLeast(0.2)
            val lonSpan = (bounds.maxLon - bounds.minLon) * latCorrection
            val latSpan = bounds.maxLat - bounds.minLat
            if (latSpan > 0) {
                aspectRatio = (lonSpan / latSpan).toFloat().coerceIn(0.5f, 2.5f)
            }
        }
        requestLayout()
        invalidate()
    }

    /** Call after [setData] to recolor without recomputing the layout. */
    fun refreshColors() = invalidate()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / aspectRatio).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shapes.isEmpty()) return
        val bounds = GeoBounds.of(shapes)
        val latCorrection = cos(Math.toRadians(bounds.centerLat)).coerceAtLeast(0.2).toFloat()
        val lonSpan = (bounds.maxLon - bounds.minLon).toFloat() * latCorrection
        val latSpan = (bounds.maxLat - bounds.minLat).toFloat()
        if (lonSpan <= 0f || latSpan <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()
        val scale = minOf(w / lonSpan, h / latSpan)
        val drawnWidth = lonSpan * scale
        val drawnHeight = latSpan * scale
        val offsetX = (w - drawnWidth) / 2f
        val offsetY = (h - drawnHeight) / 2f

        fun projectX(lon: Double): Float = ((lon - bounds.minLon) * latCorrection).toFloat() * scale + offsetX
        fun projectY(lat: Double): Float = h - (((lat - bounds.minLat).toFloat() * scale) + offsetY)

        for (shape in shapes) {
            fillPaint.color = colorProvider(shape)
            for (ring in shape.polygons) {
                if (ring.size < 3) continue
                path.reset()
                path.moveTo(projectX(ring[0].first), projectY(ring[0].second))
                for (i in 1 until ring.size) {
                    path.lineTo(projectX(ring[i].first), projectY(ring[i].second))
                }
                path.close()
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, strokePaint)
            }
        }
    }
}
