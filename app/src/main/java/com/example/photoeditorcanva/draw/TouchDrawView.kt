package com.example.photoeditorcanva.draw

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class TouchDrawView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var currentPath = Path()
    private val paths = mutableListOf<Path>()
    private val undonePaths = mutableListOf<Path>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
        for (path in paths) {
            canvas.drawPath(path, paint)
        }
        canvas.drawPath(currentPath, paint)
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
            val path = undonePaths.removeAt(undonePaths.lastIndex) // Safe access
            paths.add(path)
            invalidate()
        }
    }


    fun clearAll() {
        paths.clear()
        undonePaths.clear()
        currentPath.reset()
        invalidate()
    }
}
