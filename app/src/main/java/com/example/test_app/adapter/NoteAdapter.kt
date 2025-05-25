package com.example.test_app.adapter

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.R
import com.example.test_app.databinding.ItemNoteBinding
import com.example.test_app.model.Note
import java.io.File


// 노트 목록을 표시하기 위한 어댑터 클래스
class NoteAdapter(
    private val notes: List<Note>, // 노트 리스트
    private val onItemClick: (Note) -> Unit // 노트 클릭 시 실행할 콜백 함수
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    // ViewHolder 생성 (XML 레이아웃을 inflate)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding, onItemClick)
    }

    // ViewHolder에 데이터 바인딩
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    // 전체 아이템 수 반환
    override fun getItemCount() = notes.size

    // 개별 노트를 표시하기 위한 ViewHolder 정의
    class NoteViewHolder(
        private val binding: ItemNoteBinding,
        private val onItemClick: (Note) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        // 하나의 노트 항목을 화면에 표시하는 함수
        fun bind(note: Note) {

            // 노트 제목 설정
            binding.noteTitle.text = note.title

            // 썸네일 경로가 있는 경우 이미지 설정
            val thumbnailPath = note.thumbnailPath

            if (!thumbnailPath.isNullOrEmpty()) {
                val file = File(thumbnailPath)

                // 썸네일 경로 검사
                println("썸네일 파일 존재? ${file.exists()} / 경로: ${file.absolutePath}")
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)

                // 이미지뷰에 썸네일 표시
                if (bitmap != null) {
                    binding.thumbnailView.setImageBitmap(bitmap)
                }

                // 표시 불가 시
                else {
                    println("🚨 썸네일 이미지 디코딩 실패")
                    binding.thumbnailView.setImageResource(R.drawable.ic_pdf_placeholder)
                }
            }

            // 썸네일 경로가 없으면 기본 이미지 표시
            else {
                binding.thumbnailView.setImageResource(R.drawable.ic_pdf_placeholder)
            }

            // 노트 항목 클릭 시 콜백 함수 실행
            binding.root.setOnClickListener {
                onItemClick(note)
            }
        }
    }
}
