package com.oliver.zylka.kennzeichen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.oliver.zylka.data.kennzeichen.GeoBounds
import com.oliver.zylka.data.kennzeichen.GeoShape
import kotlin.math.cos

/**
 * Draws a set of [GeoShape]s as a shaded, pinch-zoomable/pannable/tappable map. Uses a
 * simple equirectangular projection with a cosine(latitude) correction so country-scale
 * shapes aren't visibly stretched, and sizes itself to roughly match the shapes' real
 * aspect ratio once they're known.
 *
 * Geometry is projected to view-pixel space once (on layout/data change) and cached as
 * [Path]s; pan/zoom is then a cheap [Matrix] applied to the canvas rather than
 * re-projecting ~30k points every frame.
 */
class KennzeichenMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var shapes: List<GeoShape> = emptyList()
    private var colorProvider: (GeoShape) -> Int = { Color.LTGRAY }
    private var onShapeTapped: ((GeoShape) -> Unit)? = null
    private var aspectRatio: Float = 1.3f // width / height

    private var bounds: GeoBounds? = null
    private var basePaths: List<Path> = emptyList()
    /** Same rings as [basePaths] but as raw points, for point-in-polygon hit testing. */
    private var baseRings: List<List<List<PointF>>> = emptyList()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#9E9E9E")
    }

    private val matrix = Matrix()
    private var totalScale = 1f
    private val minScale = 1f
    private val maxScale = 12f

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        strokeWidthDp(1f)
    }

    private fun strokeWidthDp(dp: Float) {
        strokePaint.strokeWidth = dp * resources.displayMetrics.density
    }

    fun setData(shapes: List<GeoShape>, colorProvider: (GeoShape) -> Int) {
        this.shapes = shapes
        this.colorProvider = colorProvider
        matrix.reset()
        totalScale = 1f
        if (shapes.isNotEmpty()) {
            val b = GeoBounds.of(shapes)
            bounds = b
            val latCorrection = cos(Math.toRadians(b.centerLat)).coerceAtLeast(0.2)
            val lonSpan = (b.maxLon - b.minLon) * latCorrection
            val latSpan = b.maxLat - b.minLat
            if (latSpan > 0) {
                aspectRatio = (lonSpan / latSpan).toFloat().coerceIn(0.4f, 3f)
            }
        } else {
            bounds = null
        }
        requestLayout()
        rebuildProjection()
        invalidate()
    }

    /** Recolor without touching pan/zoom or re-projecting geometry. */
    fun refreshColors() = invalidate()

    fun setOnShapeTapped(listener: (GeoShape) -> Unit) {
        onShapeTapped = listener
    }

    fun resetView() {
        matrix.reset()
        totalScale = 1f
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width / aspectRatio).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildProjection()
    }

    private fun rebuildProjection() {
        val b = bounds ?: run {
            basePaths = emptyList()
            baseRings = emptyList()
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val latCorrection = cos(Math.toRadians(b.centerLat)).coerceAtLeast(0.2).toFloat()
        val lonSpan = (b.maxLon - b.minLon).toFloat() * latCorrection
        val latSpan = (b.maxLat - b.minLat).toFloat()
        if (lonSpan <= 0f || latSpan <= 0f) return
        val scale = minOf(w / lonSpan, h / latSpan)
        val drawnWidth = lonSpan * scale
        val drawnHeight = latSpan * scale
        val offsetX = (w - drawnWidth) / 2f
        val offsetY = (h - drawnHeight) / 2f

        fun px(lon: Double): Float = ((lon - b.minLon) * latCorrection).toFloat() * scale + offsetX
        fun py(lat: Double): Float = h - (((lat - b.minLat).toFloat() * scale) + offsetY)

        val paths = ArrayList<Path>(shapes.size)
        val ringsOut = ArrayList<List<List<PointF>>>(shapes.size)
        for (shape in shapes) {
            val path = Path()
            val shapeRings = ArrayList<List<PointF>>(shape.polygons.size)
            for (ring in shape.polygons) {
                if (ring.size < 3) continue
                val pts = ArrayList<PointF>(ring.size)
                val first = PointF(px(ring[0].first), py(ring[0].second))
                pts.add(first)
                path.moveTo(first.x, first.y)
                for (i in 1 until ring.size) {
                    val p = PointF(px(ring[i].first), py(ring[i].second))
                    pts.add(p)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                shapeRings.add(pts)
            }
            paths.add(path)
            ringsOut.add(shapeRings)
        }
        basePaths = paths
        baseRings = ringsOut
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // basePaths/baseRings are only ever replaced together (see rebuildProjection), but
        // guard against the brief window where shapes has been updated and a rebuild is
        // still pending (e.g. view not laid out yet).
        val count = minOf(shapes.size, basePaths.size)
        if (count == 0) return
        canvas.save()
        canvas.concat(matrix)
        for (i in 0 until count) {
            fillPaint.color = colorProvider(shapes[i])
            canvas.drawPath(basePaths[i], fillPaint)
            canvas.drawPath(basePaths[i], strokePaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun clampMatrix() {
        val values = FloatArray(9)
        matrix.getValues(values)
        val w = width.toFloat()
        val h = height.toFloat()
        val extraX = w * (totalScale - 1) / 2f + w * 0.4f
        val extraY = h * (totalScale - 1) / 2f + h * 0.4f
        values[Matrix.MTRANS_X] = values[Matrix.MTRANS_X].coerceIn(-extraX, extraX)
        values[Matrix.MTRANS_Y] = values[Matrix.MTRANS_Y].coerceIn(-extraY, extraY)
        matrix.setValues(values)
    }

    private fun handleTap(x: Float, y: Float) {
        val inverse = Matrix()
        if (!matrix.invert(inverse)) return
        val pts = floatArrayOf(x, y)
        inverse.mapPoints(pts)
        val bx = pts[0]
        val by = pts[1]
        val count = minOf(shapes.size, baseRings.size)
        for (i in (count - 1) downTo 0) {
            for (ring in baseRings[i]) {
                if (pointInRing(bx, by, ring)) {
                    onShapeTapped?.invoke(shapes[i])
                    return
                }
            }
        }
    }

    private fun pointInRing(x: Float, y: Float, ring: List<PointF>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val pi = ring[i]
            val pj = ring[j]
            if ((pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newTotal = (totalScale * detector.scaleFactor).coerceIn(minScale, maxScale)
            val factor = newTotal / totalScale
            totalScale = newTotal
            matrix.postScale(factor, factor, detector.focusX, detector.focusY)
            clampMatrix()
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (totalScale > 1.01f) {
                parent?.requestDisallowInterceptTouchEvent(true)
                matrix.postTranslate(-distanceX, -distanceY)
                clampMatrix()
                invalidate()
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (totalScale > 1.01f) {
                resetView()
            } else {
                val newTotal = 3f
                val factor = newTotal / totalScale
                totalScale = newTotal
                matrix.postScale(factor, factor, e.x, e.y)
                clampMatrix()
                invalidate()
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
    }
}
