package com.example.photoeditorcanva.draw

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.photoeditorcanva.R
import com.example.photoeditorcanva.databinding.ActivityDrawBinding

class DrawActivity : AppCompatActivity() {
    private val binding by lazy { ActivityDrawBinding.inflate(layoutInflater) }
    private var toggle = false
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.redo.setOnClickListener {
            Log.d("redo touch","dlsfh")
            binding.touchDrawView.redo()
        }

        binding.undo.setOnClickListener {
            Log.d("redo touch","dlsfh")
            binding.touchDrawView.undo()
        }


        binding.selection.setOnClickListener {
            if (toggle){
                toggle = false
                binding.selection.text = "OFF"
            }
            else {
                toggle = true
                binding.selection.text = "ON"
            }
            binding.touchDrawView.toggleSelectionMode(toggle)
        }

    }
}