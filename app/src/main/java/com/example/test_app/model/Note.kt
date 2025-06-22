package com.example.test_app.model

// 노트 정보를 저장하는 데이터 클래스
data class Note(
    val id: Long,            // 노트를 구분하기 위한 고유 ID
    val title: String,       // 노트 이름 (사용자가 지정)
    val myDocPath: String,   // 이 노트에 연결된 .mydoc 파일의 내부 저장소 경로
    val thumbnailPath: String? = null  // 썸네일 이미지 파일 경로 (옵션, 없으면 null)
)