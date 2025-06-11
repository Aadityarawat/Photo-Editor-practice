package com.example.photoeditorcanva.draw

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.RequiresApi
import java.lang.Math.toDegrees
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

class TouchDrawView4 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentPath = Path()
    private val paths = mutableListOf<Path>()
    private val selectedPaths = mutableListOf<Path>()

    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val undoStack = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()

    private var isSelecting = false
    private var isDrawingSelectionBox = false
    private var isManipulatingSelection = false
    private var selectionBounds = RectF()
    private val selectionStart = PointF()
    private val selectionEnd = PointF()
    private var selectionMatrix = Matrix()
    private val originalSelectionBounds = RectF()
    private val transformedSelectionPath = Path()


    private var isErasing = false
    private var isErasingActive = false
    private var tempEraseActions = mutableListOf<DrawAction>()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialAngle = 0.0
    private var initialDistance = 0f

    private var gestureActions = mutableListOf<DrawAction>()

    private val SELECTION_PADDING = 20f

    sealed class DrawAction {
        data class Add(val path: Path) : DrawAction()
        data class Erase(val path: Path) : DrawAction()
        data class Compound(val actions: List<DrawAction>) : DrawAction()
        data class Transform(val affected: List<Path>, val matrix: Matrix, val inverse: Matrix) : DrawAction()
    }

    fun toggleSelectionMode(enabled: Boolean) {
        isSelecting = enabled
        selectedPaths.clear()
        invalidate()
    }

    fun toggleEraseMode(enabled: Boolean) {
        isErasing = enabled
        isErasingActive = false
        tempEraseActions.clear()
        invalidate()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack.lastIndex)
            when (action) {
                is DrawAction.Add -> paths.remove(action.path)
                is DrawAction.Erase -> paths.add(action.path)
                is DrawAction.Compound -> action.actions.reversed().forEach { undoAction(it) }
                is DrawAction.Transform -> action.affected.forEach { it.transform(action.inverse) }
            }
            redoStack.add(action)
            updateSelectionBounds()
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack.lastIndex)
            when (action) {
                is DrawAction.Add -> paths.add(action.path)
                is DrawAction.Erase -> paths.remove(action.path)
                is DrawAction.Compound -> action.actions.forEach { redoAction(it) }
                is DrawAction.Transform -> action.affected.forEach { it.transform(action.matrix) }
            }
            undoStack.add(action)
            updateSelectionBounds()
            invalidate()
        }
    }

    private fun undoAction(action: DrawAction) {
        when (action) {
            is DrawAction.Add -> paths.remove(action.path)
            is DrawAction.Erase -> paths.add(action.path)
            else -> {}
        }
    }

    private fun redoAction(action: DrawAction) {
        when (action) {
            is DrawAction.Add -> paths.add(action.path)
            is DrawAction.Erase -> paths.remove(action.path)
            else -> {}
        }
    }

    fun clearAll() {
        paths.clear()
        selectedPaths.clear()
        undoStack.clear()
        redoStack.clear()
        currentPath.reset()
        invalidate()
    }

    fun saveDrawingToGallery(context: Context): Uri? {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
//        canvas.drawColor(Color.BLACK) // Optional: set background to white
        draw(canvas)

        val filename = "drawing_${System.currentTimeMillis()}.png"
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/Drawings")
        }

        val contentResolver = context.contentResolver
        val uri = contentResolver.insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        if (uri != null) {
            contentResolver.openOutputStream(uri).use { out ->
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    return uri
                }
            }
        }
        return null
    }



    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureActions.clear()

                if (isErasing) {
                    isErasingActive = true
                    tempEraseActions.clear()
                    eraseAtPoint(x, y)
                    return true
                } else if (isSelecting) {
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
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2 && selectedPaths.isNotEmpty()) {
                    initialAngle = getAngle(event)
                    initialDistance = getSpacing(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isErasing) {
                    eraseAtPoint(x, y)
                } else if (event.pointerCount == 2 && selectedPaths.isNotEmpty()) {
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
                    val inverse = Matrix().apply { matrix.invert(this) }
                    selectedPaths.forEach { it.transform(matrix) }
                    gestureActions.add(DrawAction.Transform(selectedPaths.toList(), matrix, inverse))

                    initialAngle = newAngle
                    initialDistance = newDistance
                } else if (event.pointerCount == 1) {
                    if (isSelecting) {
                        if (isDrawingSelectionBox) {
                            selectionEnd.set(x, y)
                        } else if (isManipulatingSelection) {
                            val dx = x - lastTouchX
                            val dy = y - lastTouchY
                            val matrix = Matrix().apply { setTranslate(dx, dy) }
                            val inverse = Matrix().apply { matrix.invert(this) }
                            selectedPaths.forEach { it.transform(matrix) }
                            gestureActions.add(DrawAction.Transform(selectedPaths.toList(), matrix, inverse))
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
                if (isErasing && isErasingActive) {
                    if (tempEraseActions.isNotEmpty()) {
                        undoStack.add(DrawAction.Compound(tempEraseActions.toList()))
                        redoStack.clear()
                    }
                    tempEraseActions.clear()
                    isErasingActive = false
                    return true
                }

                if (isSelecting) {
                    if (isDrawingSelectionBox) selectPathsWithinBox()
                    isDrawingSelectionBox = false
                    isManipulatingSelection = false

                    if (gestureActions.isNotEmpty()) {
                        undoStack.add(DrawAction.Compound(gestureActions.toList()))
                        redoStack.clear()
                    }

                } else {
                    currentPath.lineTo(x, y)
                    paths.add(currentPath)
                    undoStack.add(DrawAction.Add(currentPath))
                    redoStack.clear()
                    currentPath = Path()
                }

                gestureActions.clear()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun eraseAtPoint(x: Float, y: Float) {
        val eraseRadius = 30f
        val samplingStep = 0.5f
        val pos = FloatArray(2)
        val toRemove = mutableListOf<Path>()
        val toAdd = mutableListOf<Path>()

        for (original in paths) {
            val measure = PathMeasure(original, false)
            val len = measure.length
            var distance = 0f
            var segment = Path()
            var isActive = false

            while (distance <= len) {
                if (measure.getPosTan(distance, pos, null)) {
                    val dx = pos[0] - x
                    val dy = pos[1] - y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val outside = dist > eraseRadius

                    if (outside) {
                        if (!isActive) {
                            segment.moveTo(pos[0], pos[1])
                            isActive = true
                        } else segment.lineTo(pos[0], pos[1])
                    } else if (isActive) {
                        toAdd.add(Path(segment))
                        segment.reset()
                        isActive = false
                    }
                }
                distance += samplingStep
            }
            if (isActive && !segment.isEmpty) toAdd.add(Path(segment))
            if (toAdd.isNotEmpty()) toRemove.add(original)
        }

        paths.removeAll(toRemove)
        paths.addAll(toAdd)

        toRemove.forEach { tempEraseActions.add(DrawAction.Erase(it)) }
        toAdd.forEach { tempEraseActions.add(DrawAction.Add(it)) }

        invalidate()
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
            min(selectionStart.x, selectionEnd.x) - SELECTION_PADDING,
            min(selectionStart.y, selectionEnd.y) - SELECTION_PADDING,
            maxOf(selectionStart.x, selectionEnd.x) + SELECTION_PADDING,
            maxOf(selectionStart.y, selectionEnd.y) + SELECTION_PADDING
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
        if (selectedPaths.isEmpty()) {
            selectionBounds.setEmpty()
            return
        }
        val allBounds = RectF()
        val tmp = RectF()
        selectedPaths.first().computeBounds(allBounds, true)
        for (i in 1 until selectedPaths.size) {
            selectedPaths[i].computeBounds(tmp, true)
            allBounds.union(tmp)
        }

        // Apply visual padding to the selection bounds here
        allBounds.inset(-SELECTION_PADDING, -SELECTION_PADDING)

        selectionBounds = allBounds
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (path in paths) {
            val pathPaint = if (selectedPaths.contains(path)) {
                Paint(paint).apply { color = Color.BLUE }
            } else paint
            canvas.drawPath(path, pathPaint)
        }
        canvas.drawPath(currentPath, paint)

        if (isDrawingSelectionBox) {
            val boxPaint = Paint().apply {
                color = Color.GRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            val box = RectF(
                min(selectionStart.x, selectionEnd.x) - SELECTION_PADDING,
                min(selectionStart.y, selectionEnd.y) - SELECTION_PADDING,
                maxOf(selectionStart.x, selectionEnd.x) + SELECTION_PADDING,
                maxOf(selectionStart.y, selectionEnd.y) + SELECTION_PADDING
            )
            canvas.drawRect(box, boxPaint)
        }

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
