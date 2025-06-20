package com.example.photoeditorcanva.sample

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.photoeditorcanva.R
import com.example.photoeditorcanva.databinding.ActivityPhotoEditorBinding

class photoEditorActivity : AppCompatActivity() {
    private val binding by lazy { ActivityPhotoEditorBinding.inflate(layoutInflater) }
    private lateinit var optionAdapter : OptionAdapter
    private val optionList : MutableList<String> = mutableListOf()
    private var drawToggle = false
    private var selectToggle = false
    private var eraseToggle = false

    private val markers = mutableListOf<Marker>()
    private var pinUnit = "1"
    private var pinStatus = false
    private var showDeleteIcons = false

    private var brushColor : Int?= null
    private var brushSize : Float?= null

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
            setImageResource(R.drawable.delete)
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

    private fun onClick(){
        binding.redoIV.setOnClickListener {
            Log.d("redo touch","dlsfh")
            binding.touchDrawView.redo()
        }

        binding.undoIV.setOnClickListener {
            Log.d("redo touch","dlsfh")
            binding.touchDrawView.undo()
        }

        binding.drawIV.setOnClickListener {
            drawToggle = !drawToggle
            if (drawToggle){
                binding.drawIV.setBackgroundResource(R.color.green)
                binding.pinIV.setBackgroundResource(R.color.white)
                binding.touchDrawView.setMode(InteractionMode.DRAW)
                binding.touchDrawView.isClickable = true
                binding.touchDrawView.isFocusable = true

//                binding.filterOption.visibility = View.VISIBLE
//                binding.closeIV.visibility = View.VISIBLE
                binding.filterType.text = "Draw Tool"
                binding.pinUnit.visibility = View.GONE
                binding.drawColorLL.visibility = View.VISIBLE
                binding.drawSizeLL.visibility = View.VISIBLE
            }else binding.drawIV.setBackgroundResource(R.color.white)

        }

        binding.closeIV.setOnClickListener {
            binding.closeIV.visibility = View.GONE
            binding.filterOption.visibility = View.GONE
            binding.touchDrawView.setBrush(brushColor, brushSize)
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

        binding.draw15.setOnClickListener { brushSize = 15f }
        binding.draw25.setOnClickListener { brushSize = 25f }
        binding.draw45.setOnClickListener { brushSize = 45f }
        binding.draw90.setOnClickListener { brushSize = 90f }

        binding.drawRed.setOnClickListener { brushColor = getColor(R.color.red) }
        binding.drawBlack.setOnClickListener { brushColor = getColor(R.color.black) }
        binding.drawYellow.setOnClickListener { brushColor = getColor(R.color.yellow) }
        binding.drawGreen.setOnClickListener { brushColor = getColor(R.color.green) }

        binding.saveIV.setOnClickListener {
            saveViewAsImage(binding.imageContainer, this)
            val uri = binding.touchDrawView.saveDrawingToGallery(this)
            if (uri != null) {
                Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.selectIV.setOnClickListener {
            if (selectToggle){
                selectToggle = false
                binding.selectIV.setBackgroundResource(R.color.white)
            }
            else {
                selectToggle = true
                binding.selectIV.setBackgroundResource(R.color.green)
            }
            binding.touchDrawView.toggleSelectionMode(selectToggle)
        }

        binding.eraseIV.setOnClickListener {
            eraseToggle = !eraseToggle
            binding.eraseIV.setBackgroundResource(if (eraseToggle) R.color.green else R.color.white)
            binding.touchDrawView.toggleEraseMode(eraseToggle)
        }

        binding.pinIV.setOnClickListener {
            pinStatus = !pinStatus
            binding.pinIV.setBackgroundResource(if (pinStatus) R.color.green else R.color.white)
            if (pinStatus) {
                binding.drawIV.setBackgroundResource(R.color.white)
                binding.touchDrawView.setMode(InteractionMode.NONE)
                binding.touchDrawView.setBackgroundColor(Color.TRANSPARENT)
                binding.touchDrawView.isClickable = false
                binding.touchDrawView.isFocusable = false

                binding.closeIV.visibility = View.VISIBLE
                binding.filterOption.visibility = View.VISIBLE
                binding.filterType.text = "Pin Tool"

                binding.pinUnit.visibility = View.VISIBLE
                binding.drawColorLL.visibility = View.GONE
                binding.drawSizeLL.visibility = View.GONE

            } else {
                binding.touchDrawView.setMode(InteractionMode.DRAW)
                binding.touchDrawView.isClickable = true
                binding.touchDrawView.isFocusable = true
            }
//            binding.touchDrawView.setMode(InteractionMode.PIN)
//            updateButtonBackgrounds(binding.pinIV)
        }
    }

    private fun updateButtonBackgrounds(selected: View) {
        val buttons = listOf(binding.selectIV, binding.drawIV, binding.eraseIV, binding.pinIV)
        buttons.forEach { it.setBackgroundResource(R.color.white) }
        selected.setBackgroundResource(R.color.green)
    }

    fun saveViewAsImage(view: View, context: Context): Uri? {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas) // ← includes all children!

        val filename = "drawing_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyDrawings")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            context.contentResolver.openOutputStream(it).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, contentValues, null, null)
        }

        return uri
    }



    /*private fun setAdapter(){
        for (i in 0 until 6){
            optionList.add("hello")
        }

        binding.editOptionRV.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        optionAdapter = OptionAdapter(optionList)
        binding.editOptionRV.adapter = optionAdapter
    }*/
}