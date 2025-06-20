package com.example.photoeditorcanva

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginEnd
import com.example.photoeditorcanva.databinding.ActivityMainBinding

class MarkerActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val markers = mutableListOf<Marker>()
    private var pinUnit = "1"
    private var pinStatus = false
    private var showDeleteIcons = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setUpMarker()
        onClick()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpMarker() {
        binding.backgroundImage.post {
            val imgWidth = binding.backgroundImage.width
            val imgHeight = binding.backgroundImage.height

            binding.backgroundImage.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val xPercent = event.x / imgWidth
                    val yPercent = event.y / imgHeight

                    val newMarker = Marker(xPercent, yPercent)

                    if (pinStatus) {
                        markers.add(newMarker)
                        addDraggableMarker(this, binding.imageContainer, binding.backgroundImage, newMarker)
                    }
                }
                true
            }
        }
    }

    private fun onClick() {
        binding.pin.setOnClickListener {
            pinStatus = !pinStatus
            binding.pin.setBackgroundResource(if (pinStatus) R.color.green else R.color.red)
        }

        binding.pinUnit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pinUnit = s.toString()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.toggleDelete.setOnClickListener {
            showDeleteIcons = !showDeleteIcons
            updateDeleteIconsVisibility()
        }
    }

    data class Marker(var xPercent: Float, var yPercent: Float, var view: View? = null)

    private fun updateDeleteIconsVisibility() {
        markers.forEach {
            val deleteIcon = it.view?.findViewWithTag<ImageView>("deleteIcon")
            deleteIcon?.visibility = if (showDeleteIcons) View.VISIBLE else View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun addDraggableMarker(
        context: Context,
        container: FrameLayout,
        imageView: ImageView,
        marker: Marker
    ) {
        val markerLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ) // increase height to fit delete icon
            clipChildren = false
            clipToPadding = false
        }

        val markerIcon = ImageView(context).apply {
            setImageResource(R.drawable.locationpointer)
            setPadding(0,10,20,0)
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(90, 130).apply {
                topMargin = 30
                marginEnd = 10
            }
        }

        val markerLabel = TextView(context).apply {
            text = pinUnit
            setTextColor(resources.getColor(android.R.color.black))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            elevation = 10f
            setPadding(25, 56, 0, 0)
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }



        val deleteIcon = ImageView(context).apply {
            visibility = if (showDeleteIcons) View.VISIBLE else View.GONE
            tag = "deleteIcon"
            setImageResource(R.drawable.baseline_auto_delete_24)
            layoutParams = FrameLayout.LayoutParams(60, 80).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 8
            }
            setPadding(10, 2, 2, 2)
            elevation = 10f
            setOnClickListener {
                container.removeView(markerLayout)
                markers.remove(marker)
            }
        }

        markerLayout.addView(markerIcon)
        markerLayout.addView(markerLabel)
        markerLayout.addView(deleteIcon)

        imageView.post {
            val imgWidth = imageView.width
            val imgHeight = imageView.height

            markerLayout.x = marker.xPercent * imgWidth - 40
            markerLayout.y = marker.yPercent * imgHeight - 40

            marker.view = markerLayout
            container.addView(markerLayout)

            var dX = 0f
            var dY = 0f

            markerLayout.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dX = v.x - event.rawX
                        dY = v.y - event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newX = event.rawX + dX
                        val newY = event.rawY + dY

                        val maxX = imgWidth - v.width
                        val maxY = imgHeight - v.height

                        v.x = newX.coerceIn(0f, maxX.toFloat())
                        v.y = newY.coerceIn(0f, maxY.toFloat())
                    }
                    MotionEvent.ACTION_UP -> {
                        marker.xPercent = (v.x + v.width / 2) / imgWidth
                        marker.yPercent = (v.y + v.height / 2) / imgHeight
                        Log.d("Marker", "New position: ${marker.xPercent}, ${marker.yPercent}")
                    }
                }
                true
            }
        }
    }
}
