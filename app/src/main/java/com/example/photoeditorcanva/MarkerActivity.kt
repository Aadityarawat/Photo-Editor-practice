package com.example.photoeditorcanva

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.photoeditorcanva.databinding.ActivityMainBinding

class MarkerActivity : AppCompatActivity() {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    // Store all markers (optional, for tracking or saving later)
    private val markers = mutableListOf<Marker>()
    private var pinUnit = "1"
    private var pinStatus = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setUpMarker()
        onClick()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpMarker(){
        // Add marker on image tap
        binding.backgroundImage.post {
            val imgWidth = binding.backgroundImage.width
            val imgHeight = binding.backgroundImage.height

            binding.backgroundImage.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val xPercent = event.x / imgWidth
                    val yPercent = event.y / imgHeight

                    val newMarker = Marker(xPercent, yPercent)

                    if (pinStatus){
                        markers.add(newMarker)
                        addDraggableMarker(this, binding.imageContainer, binding.backgroundImage, newMarker)
                    }
                }
                true
            }
        }
    }

    private fun onClick(){
        binding.pin.setOnClickListener {
            if (pinStatus){
                pinStatus = false
                binding.pin.setBackgroundResource(R.color.red)
            }else{
                pinStatus = true
                binding.pin.setBackgroundResource(R.color.green)
            }
        }

        binding.pinUnit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pinUnit = s.toString()
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

    }

    data class Marker(var xPercent: Float, var yPercent: Float)

    @SuppressLint("ClickableViewAccessibility")
    fun addDraggableMarker(
        context: Context,
        container: FrameLayout,
        imageView: ImageView,
        marker: Marker
    ) {

        val markerLayout = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(90, 110)
        }

        val markerIcon = ImageView(context).apply {
            setImageResource(R.drawable.locationpointer) // your marker icon
            scaleType = ImageView.ScaleType.FIT_XY
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val markerLabel = TextView(context).apply {
            text = pinUnit
            Log.d("Pin value", pinUnit)
            setTextColor(resources.getColor(android.R.color.black))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            elevation = 10f
            setPadding(0,10,0,0)
            textSize = 12f
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        markerLayout.addView(markerIcon)
        markerLayout.addView(markerLabel)

        imageView.post {
            val imgWidth = imageView.width
            val imgHeight = imageView.height

            markerLayout.x = marker.xPercent * imgWidth - 40
            markerLayout.y = marker.yPercent * imgHeight - 40

            container.addView(markerLayout)

            // Set up drag logic
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
