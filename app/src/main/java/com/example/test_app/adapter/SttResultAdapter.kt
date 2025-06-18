package com.example.test_app.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.R
import com.example.test_app.model.SttResultItem

class SttResultAdapter(
    private val items: List<SttResultItem>
) : RecyclerView.Adapter<SttResultAdapter.SttResultViewHolder>() {

    inner class SttResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileNameTextView: TextView = itemView.findViewById(R.id.noteTitle)          // 파일명 TextView
        val resultTextView: TextView = itemView.findViewById(R.id.resultTextView)       // 결과 TextView (추가 필요)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SttResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stt_result, parent, false)
        return SttResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SttResultViewHolder, position: Int) {
        val item = items[position]

        holder.fileNameTextView.text = item.fileName

        // 결과가 없으면 회색 글씨, 있으면 일반 글씨
        if (item.fileResult.isBlank()) {
            holder.resultTextView.text = "결과 없음"
            holder.resultTextView.setTextColor(Color.GRAY)
        } else {
            holder.resultTextView.text = item.fileResult
            holder.resultTextView.setTextColor(Color.BLACK)
        }
    }

    override fun getItemCount(): Int = items.size
}
