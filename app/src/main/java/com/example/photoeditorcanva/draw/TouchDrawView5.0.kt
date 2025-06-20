// Optimized and Stable TouchDrawView5 with All Fixes
// - Safe erase at any point
// - Visible selection box
// - Shared bounding box for multi-path selection with padding

package com.example.photoeditorcanva.draw

import android.content.Context
import android.graphics.*
import android.graphics.fonts.Font
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.example.photoeditorcanva.sample.InteractionMode
import java.lang.Math.toDegrees
import kotlin.math.*

class TouchDrawView50 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val undoStack = mutableListOf<DrawAction>()
    private val redoStack = mutableListOf<DrawAction>()

    private var isErasing = false
    private var eraseBuffer = mutableListOf<DrawAction>()

    private var isSelecting = false
    private var isDrawingSelectionBox = false
    private var isManipulatingSelection = false
    private var selectionBounds = RectF()
    private val selectionStart = PointF()
    private val selectionEnd = PointF()
    private var selectionMatrix = Matrix()
    private var gestureActions = mutableListOf<DrawAction>()
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialAngle = 0.0
    private var initialDistance = 0f
    private var eraseSize = 30f
    private var brushColor : Int?= null
    private var brushSize : Float?= null


    var interactionMode: InteractionMode = InteractionMode.DRAW

    fun setMode(mode: InteractionMode) {
        interactionMode = mode
    }

    private val paths = mutableListOf<Path>()
    private val selectedPaths = mutableSetOf<Path>()
    private var currentPath = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = brushColor ?: Color.RED
        strokeWidth = brushSize ?: 6f
        style = Paint.Style.STROKE
    }

    sealed class DrawAction {
        data class Add(val path: Path): DrawAction()
        data class Erase(val path: Path): DrawAction()
        data class Compound(val actions: List<DrawAction>): DrawAction()
        data class Transform(val paths: List<Path>, val matrix: Matrix, val inverse: Matrix): DrawAction()
    }

    fun toggleEraseMode(enabled: Boolean) {
        isErasing = enabled
        eraseBuffer.clear()
    }

    fun toggleSelectionMode(enabled: Boolean) {
        isSelecting = enabled
        selectedPaths.clear()
        invalidate()
    }

    fun setEraseSize(size: Float){
        eraseSize = size
    }

    fun setBrush(color: Int?, size: Float?){
        brushSize = size
        brushColor = color
        Log.d("setBrush","$size  $color")
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.removeAt(undoStack. lastIndex)
            applyUndo(action)
            redoStack.add(action)
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.removeAt(redoStack. lastIndex)
            applyRedo(action)
            undoStack.add(action)
            invalidate()
        }
    }

    fun clearAll() {
        paths.clear()
        selectedPaths.clear()
        undoStack.clear()
        redoStack.clear()
        invalidate()
    }

    fun saveDrawingToGallery(context: Context): Uri? {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
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

    override fun onDraw(canvas: Canvas) {
        for (path in paths) {
            val paintToUse = if (selectedPaths.contains(path)) Paint(paint).apply { color = Color.BLUE } else paint
            canvas.drawPath(path, paintToUse)
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
                min(selectionStart.x, selectionEnd.x),
                min(selectionStart.y, selectionEnd.y),
                max(selectionStart.x, selectionEnd.x),
                max(selectionStart.y, selectionEnd.y)
            )
            canvas.drawRect(box, boxPaint)
        }

        if (selectedPaths.isNotEmpty()) {
            val selPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(selectionBounds, selPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(interactionMode){
            InteractionMode.DRAW -> {
                val x = event.x
                val y = event.y

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureActions.clear()
                        if (isErasing) {
                            eraseAt(x, y)
                            return true
                        }
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
                        }
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount == 2 && selectedPaths.isNotEmpty()) {
                            initialAngle = getAngle(event)
                            initialDistance = getSpacing(event)
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (isErasing) {
                            eraseAt(x, y)
                        } else if (event.pointerCount == 2 && selectedPaths.isNotEmpty()) {
                            val angle = getAngle(event)
                            val dist = getSpacing(event)
                            val scale = dist / initialDistance
                            val rotate = (angle - initialAngle).toFloat()
                            val cx = selectionBounds.centerX()
                            val cy = selectionBounds.centerY()
                            val mtx = Matrix().apply {
                                postScale(scale, scale, cx, cy)
                                postRotate(rotate, cx, cy)
                            }
                            val inv = Matrix().apply { mtx.invert(this) }
                            selectedPaths.forEach { it.transform(mtx) }
                            gestureActions.add(DrawAction.Transform(selectedPaths.toList(), mtx, inv))
                            initialAngle = angle
                            initialDistance = dist
                            updateSelectionBounds()
                        } else if (isSelecting) {
                            if (isDrawingSelectionBox) {
                                selectionEnd.set(x, y)
                            } else if (isManipulatingSelection) {
                                val dx = x - lastTouchX
                                val dy = y - lastTouchY
                                val mtx = Matrix().apply { setTranslate(dx, dy) }
                                val inv = Matrix().apply { mtx.invert(this) }
                                selectedPaths.forEach { it.transform(mtx) }
                                gestureActions.add(DrawAction.Transform(selectedPaths.toList(), mtx, inv))
                                lastTouchX = x
                                lastTouchY = y
                                updateSelectionBounds()
                            }
                        } else {
                            currentPath.lineTo(x, y)
                        }
                        invalidate()
                    }

                    MotionEvent.ACTION_UP -> {
                        if (isErasing) {
                            if (eraseBuffer.isNotEmpty()) {
                                undoStack.add(DrawAction.Compound(eraseBuffer.toList()))
                                eraseBuffer.clear()
                                redoStack.clear()
                            }
                            return true
                        }
                        if (isSelecting) {
                            if (isDrawingSelectionBox) selectPathsInBox()
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
                    }
                }
            }
            else -> return false
        }

        return true
    }

    private fun eraseAt(x: Float, y: Float) {
        val radius = eraseSize
        val updatedPaths = mutableListOf<Path>()
        val toRemove = mutableListOf<Path>()
        val actions = mutableListOf<DrawAction>()

        for (path in paths.toList()) {
            val measure = PathMeasure(path, false)
            val segments = mutableListOf<Path>()
            var dist = 0f
            val pos = FloatArray(2)
            val step = 1f

            var current = Path()
            var inside = false
            var hasErased = false

            while (dist <= measure.length) {
                if (measure.getPosTan(dist, pos, null)) {
                    val dx = pos[0] - x
                    val dy = pos[1] - y
                    val isInside = dx * dx + dy * dy < radius * radius

                    if (isInside) {
                        if (!current.isEmpty) {
                            segments.add(Path(current))
                            current.reset()
                        }
                        inside = true
                        hasErased = true
                    } else {
                        if (!inside) {
                            if (current.isEmpty) current.moveTo(pos[0], pos[1])
                            else current.lineTo(pos[0], pos[1])
                        } else {
                            // Restarting after an erased section
                            current.moveTo(pos[0], pos[1])
                            inside = false
                        }
                    }
                }
                dist += step
            }

            // Add any remaining part
            if (!current.isEmpty) {
                segments.add(current)
            }

            if (hasErased) {
                toRemove.add(path)
                updatedPaths.addAll(segments)
                actions.add(DrawAction.Erase(path))
                segments.forEach { actions.add(DrawAction.Add(it)) }
            }
        }

        paths.removeAll(toRemove)
        paths.addAll(updatedPaths)
        selectedPaths.removeAll(toRemove)
        eraseBuffer.addAll(actions)
    }


    private fun selectPathsInBox() {
        val box = RectF(
            min(selectionStart.x, selectionEnd.x),
            min(selectionStart.y, selectionEnd.y),
            max(selectionStart.x, selectionEnd.x),
            max(selectionStart.y, selectionEnd.y)
        )
        selectedPaths.clear()
        val bounds = RectF()
        for (p in paths) {
            p.computeBounds(bounds, true)
            if (RectF.intersects(bounds, box)) selectedPaths.add(p)
        }
        updateSelectionBounds()
    }

    private fun updateSelectionBounds() {
        if (selectedPaths.isEmpty()) {
            selectionBounds.setEmpty()
            return
        }
        val union = RectF().apply { selectedPaths.first().computeBounds(this, true) }
        val tmp = RectF()
        selectedPaths.drop(1).forEach {
            it.computeBounds(tmp, true)
            union.union(tmp)
        }
        val padding = 20f
        selectionBounds.set(union)
        selectionBounds.inset(-padding, -padding)
    }

    private fun getAngle(ev: MotionEvent): Double {
        val dx = ev.getX(1) - ev.getX(0)
        val dy = ev.getY(1) - ev.getY(0)
        return toDegrees(atan2(dy, dx).toDouble())
    }

    private fun getSpacing(ev: MotionEvent): Float {
        val dx = ev.getX(0) - ev.getX(1)
        val dy = ev.getY(0) - ev.getY(1)
        return hypot(dx, dy)
    }

    private fun applyUndo(action: DrawAction) {
        when (action) {
            is DrawAction.Add -> paths.remove(action.path)
            is DrawAction.Erase -> paths.add(action.path)
            is DrawAction.Compound -> action.actions.reversed().forEach { applyUndo(it) }
            is DrawAction.Transform -> action.paths.forEach { it.transform(action.inverse) }
        }
    }

    private fun applyRedo(action: DrawAction) {
        when (action) {
            is DrawAction.Add -> paths.add(action.path)
            is DrawAction.Erase -> paths.remove(action.path)
            is DrawAction.Compound -> action.actions.forEach { applyRedo(it) }
            is DrawAction.Transform -> action.paths.forEach { it.transform(action.matrix) }
        }
    }
}
