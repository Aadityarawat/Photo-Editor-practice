package com.example.photoeditorcanva.draw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.photoeditorcanva.R
import java.lang.Math.toDegrees
import kotlin.math.atan2

class TouchDrawView2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs){

    //Undo Redo
    private var currentPath = Path()
    private val paths = mutableListOf<Path>()
    private val undonePaths = mutableListOf<Path>()

    //Selection
    private var selectedPath: Path? = null
    private var isSelecting = false

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var isRotating = false
    private var initialAngle = 0.0

    private var paintColor : Int? = null
    private var paintStroke : Float? = null

    //Zoom
    private var isScaling = false
    private var initialDistance = 0f


    private val paint = Paint().apply {
        color = paintColor ?: ContextCompat.getColor(context, R.color.red)
        strokeWidth = paintStroke ?: 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val pointerCount = event.pointerCount

        if (isSelecting){
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val x = event.getX(0)
                    val y = event.getY(0)
                    selectedPath = findTouchedPath(x, y)
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2 && selectedPath != null) {
                        isRotating = true
                        isScaling = true
                        initialAngle = getAngle(event)
                        initialDistance = getSpacing(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2 && selectedPath != null) {
                        if (isRotating) {
                            val newAngle = getAngle(event)
                            val deltaAngle = newAngle - initialAngle
                            rotateSelected(deltaAngle.toFloat())
                            initialAngle = newAngle
                        }

                        if (isScaling) {
                            val newDistance = getSpacing(event)
                            val scale = newDistance / initialDistance
                            scaleSelected(scale)
                            initialDistance = newDistance
                        }
                    } else if (selectedPath != null) {
                        val x = event.getX(0)
                        val y = event.getY(0)
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        val matrix = Matrix()
                        matrix.setTranslate(dx, dy)
                        selectedPath?.transform(matrix)
                        lastTouchX = x
                        lastTouchY = y
                    }
                    invalidate()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    isRotating = false
                    isScaling = false
                }
            }

            return true
        }

        // Drawing Mode
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path()
                currentPath.moveTo(x, y)
                undonePaths.clear()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                currentPath.lineTo(x, y)
                paths.add(currentPath)
                currentPath = Path()
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()

        for (path in paths) {
            if (path == selectedPath) {
                // Draw the selected path with blue stroke
                val highlightPaint = Paint(paint).apply {
                    color = Color.BLUE
                    strokeWidth = 8f
                }
                canvas.drawPath(path, highlightPaint)

                // Draw bounding box
                val bounds = RectF()
                path.computeBounds(bounds, true)
                val borderPaint = Paint().apply {
                    color = Color.BLUE
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                }

                canvas.drawRect(bounds, borderPaint)
            } else {
                canvas.drawPath(path, paint)
            }
        }

        // Draw current drawing path
        canvas.drawPath(currentPath, paint)
        canvas.restore()
    }


    fun undo() {
        if (paths.isNotEmpty()) {
            val path = paths.removeAt(paths.lastIndex)
            undonePaths.add(path)
            invalidate()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            val path = undonePaths.removeAt(undonePaths.lastIndex)
            paths.add(path)
            invalidate()
        }
    }

    fun clearAll() {
        paths.clear()
        undonePaths.clear()
        currentPath.reset()
        selectedPath = null
        invalidate()
    }

    fun toggleSelectionMode(enable: Boolean) {
        isSelecting = enable
        if (!enable) selectedPath = null
        invalidate()
    }

    fun setPen(paintColor: Int, paintStroke: Float){
        this.paintColor = paintColor
        this.paintStroke = paintStroke
    }

    private fun findTouchedPath(x: Float, y: Float): Path? {
        val region = Region()
        val clip = Region(0, 0, width, height)
        for (path in paths.reversed()) {
            val bounds = RectF()
            path.computeBounds(bounds, true)

            // Expand bounds slightly for easier tapping
            val touchBounds = RectF(bounds)
            touchBounds.inset(-20f, -20f)

            if (touchBounds.contains(x, y)) {
                region.setPath(path, clip)
                // Check if point is in path region or just inside bounding box
                if (region.contains(x.toInt(), y.toInt()) || touchBounds.contains(x, y)) {
                    return path
                }
            }
        }
        return null
    }


    private fun getAngle(event: MotionEvent): Double {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    }

    fun rotateSelected(degrees: Float) {
        selectedPath?.let {
            val bounds = RectF()
            it.computeBounds(bounds, true)
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val matrix = Matrix()
            matrix.setRotate(degrees, cx, cy)
            it.transform(matrix)
            invalidate()
        }
    }

    private fun getSpacing(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }

    private fun scaleSelected(scaleFactor: Float) {
        selectedPath?.let {
            val bounds = RectF()
            it.computeBounds(bounds, true)
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val matrix = Matrix()
            matrix.setScale(scaleFactor, scaleFactor, cx, cy)
            it.transform(matrix)
        }
    }

}
