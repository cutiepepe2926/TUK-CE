package com.example.test_app.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.R
import com.example.test_app.databinding.ItemNoteBinding
import com.example.test_app.model.Note
import java.io.File

class NoteAdapter(
    private val notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount() = notes.size

    class NoteViewHolder(
        private val binding: ItemNoteBinding,
        private val onItemClick: (Note) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.noteTitle.text = note.title
            // 썸네일 이미지 설정(테스트 중)

            val thumbnailPath = note.thumbnailPath
            if (!thumbnailPath.isNullOrEmpty()) {
                val file = File(thumbnailPath)
                println("🔍 썸네일 파일 존재? ${file.exists()} / 경로: ${file.absolutePath}")  // ✅ 추가
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    binding.thumbnailView.setImageBitmap(bitmap)
                } else {
                    println("🚨 썸네일 이미지 디코딩 실패")  // ✅ 추가
                    binding.thumbnailView.setImageResource(R.drawable.ic_pdf_placeholder)
                }
            } else {
                binding.thumbnailView.setImageResource(R.drawable.ic_pdf_placeholder)
            }

            binding.root.setOnClickListener {
                onItemClick(note)
            }
        }
    }
}
