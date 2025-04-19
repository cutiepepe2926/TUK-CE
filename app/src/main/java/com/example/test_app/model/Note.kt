package com.example.test_app.model

data class Note(
    val id: Long,            // 노트 식별자
    val title: String,       // 노트 이름 (사용자가 지정)
    val myDocPath: String,   // 내부저장소에 있는 .mydoc 파일 경로
    val thumbnailPath: String? = null  // 썸네일 경로(선택)
)