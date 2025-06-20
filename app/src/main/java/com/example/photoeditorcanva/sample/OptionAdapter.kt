package com.example.photoeditorcanva.sample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.photoeditorcanva.R
import com.example.photoeditorcanva.databinding.EditOptionItemBinding

class OptionAdapter(private val list: List<String>): RecyclerView.Adapter<OptionAdapter.MyViewHolder>() {

    inner class MyViewHolder(val binding: EditOptionItemBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = EditOptionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(binding)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.binding.apply {
            optionImage.setImageResource(R.drawable.locationpointer)
        }
    }
}