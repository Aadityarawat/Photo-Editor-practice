package com.example.photoeditorcanva.draw

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.lang.Math.toDegrees
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min


class TouchDrawView3 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // Drawing
    private var currentPath = Path()
    private val paths = mutableListOf<Path>()
    private val undonePaths = mutableListOf<Path>()

    // Paint
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Selection
    private var isSelecting = false
    private var isDrawingSelectionBox = false
    private var isManipulatingSelection = false

    private val selectedPaths = mutableListOf<Path>()
    private var selectionBounds = RectF()
    private val selectionStart = PointF()
    private val selectionEnd = PointF()

    // Gesture support
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialAngle = 0.0
    private var initialDistance = 0f

    // Region reuse
    private val clipRegion = Region()
    private val pathRegion = Region()

    init {
        clipRegion.set(0, 0, width, height)
    }

    fun toggleSelectionMode(enabled: Boolean) {
        isSelecting = enabled
        selectedPaths.clear()
        invalidate()
    }

    fun undo() {
        if (paths.isNotEmpty()) {
            val removed = paths.removeAt(paths.lastIndex)
            undonePaths.add(removed)
            invalidate()
        }
    }

    fun redo() {
        if (undonePaths.isNotEmpty()) {
            val restored = undonePaths.removeAt(undonePaths.lastIndex)
            paths.add(restored)
            invalidate()
        }
    }

    fun clearAll() {
        paths.clear()
        undonePaths.clear()
        currentPath.reset()
        selectedPaths.clear()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerCount = event.pointerCount
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                if (isSelecting) {
                    if (selectionBounds.contains(x, y)) {
                        isManipulatingSelection = true
                        lastTouchX = x
                        lastTouchY = y
                    } else {
                        isDrawingSelectionBox = true
                        selectionStart.set(x, y)
                        selectionEnd.set(x, y)
                    }
                } else {
                    currentPath = Path().apply { moveTo(x, y) }
                    undonePaths.clear()
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 2 && selectedPaths.isNotEmpty()) {
                    val newAngle = getAngle(event)
                    val newDistance = getSpacing(event)
                    val angleDelta = (newAngle - initialAngle).toFloat()
                    val scale = newDistance / initialDistance

                    val cx = selectionBounds.centerX()
                    val cy = selectionBounds.centerY()

                    val matrix = Matrix().apply {
                        postScale(scale, scale, cx, cy)
                        postRotate(angleDelta, cx, cy)
                    }

                    selectedPaths.forEach { it.transform(matrix) }
                    updateSelectionBounds()

                    initialAngle = newAngle
                    initialDistance = newDistance
                } else if (pointerCount == 1) {
                    val x = event.x
                    val y = event.y
                    if (isSelecting) {
                        if (isDrawingSelectionBox) {
                            selectionEnd.set(x, y)
                        } else if (isManipulatingSelection) {
                            val dx = x - lastTouchX
                            val dy = y - lastTouchY
                            val matrix = Matrix().apply { setTranslate(dx, dy) }
                            selectedPaths.forEach { it.transform(matrix) }
                            lastTouchX = x
                            lastTouchY = y
                            updateSelectionBounds()
                        }
                    } else {
                        currentPath.lineTo(x, y)
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isSelecting) {
                    if (isDrawingSelectionBox) {
                        selectPathsWithinBox()
                    }
                    isDrawingSelectionBox = false
                    isManipulatingSelection = false
                } else {
                    currentPath.lineTo(event.x, event.y)
                    paths.add(currentPath)
                    currentPath = Path()
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 2 && selectedPaths.isNotEmpty()) {
                    initialAngle = getAngle(event)
                    initialDistance = getSpacing(event)
                    return true
                }
            }

            /*MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 2 && selectedPaths.isNotEmpty()) {
                    val newAngle = getAngle(event)
                    val newDistance = getSpacing(event)
                    val angleDelta = (newAngle - initialAngle).toFloat()
                    val scale = newDistance / initialDistance

                    val cx = selectionBounds.centerX()
                    val cy = selectionBounds.centerY()

                    val matrix = Matrix()
                    matrix.postScale(scale, scale, cx, cy)
                    matrix.postRotate(angleDelta, cx, cy)

                    selectedPaths.forEach { it.transform(matrix) }
                    updateSelectionBounds()

                    initialAngle = newAngle
                    initialDistance = newDistance
                    invalidate()
                    return true
                }
            }*/

            MotionEvent.ACTION_POINTER_UP -> {
                return true
            }
        }
        return false
    }

    private fun getAngle(event: MotionEvent): Double {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return toDegrees(atan2(dy, dx).toDouble())
    }

    private fun getSpacing(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    private fun selectPathsWithinBox() {
        val box = RectF(
            min(selectionStart.x, selectionEnd.x),
            min(selectionStart.y, selectionEnd.y),
            maxOf(selectionStart.x, selectionEnd.x),
            maxOf(selectionStart.y, selectionEnd.y)
        )
        selectedPaths.clear()
        for (path in paths) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            if (RectF.intersects(bounds, box)) {
                selectedPaths.add(path)
            }
        }
        updateSelectionBounds()
    }

    private fun updateSelectionBounds() {
        if (selectedPaths.isEmpty()) return
        val allBounds = RectF()
        val tmp = RectF()
        selectedPaths.first().computeBounds(allBounds, true)
        for (i in 1 until selectedPaths.size) {
            selectedPaths[i].computeBounds(tmp, true)
            allBounds.union(tmp)
        }
        selectionBounds = allBounds
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw paths
        for (path in paths) {
            val isSelected = selectedPaths.contains(path)
            val pathPaint = if (isSelected) {
                Paint(paint).apply { color = Color.BLUE }
            } else paint
            canvas.drawPath(path, pathPaint)
        }

        // Draw current drawing path
        canvas.drawPath(currentPath, paint)

        // Draw selection box
        if (isDrawingSelectionBox) {
            val boxPaint = Paint().apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            val box = RectF(
                min(selectionStart.x, selectionEnd.x),
                min(selectionStart.y, selectionEnd.y),
                maxOf(selectionStart.x, selectionEnd.x),
                maxOf(selectionStart.y, selectionEnd.y)
            )
            canvas.drawRect(box, boxPaint)
        }

        // Draw bounding box around selected paths
        if (selectedPaths.isNotEmpty()) {
            val boxPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(selectionBounds, boxPaint)
        }
    }
}
