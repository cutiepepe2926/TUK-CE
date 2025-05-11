package com.example.test_app.model

data class PointF(val x: Float, val y: Float)

data class Stroke(
    val color: Int,
    val width: Float,
    val points: MutableList<PointF>,
    var page: Int = 0  // 새로 추가: 이 stroke가 어느 페이지에서 작성되었는지
)

data class TextAnnotation(
    val page: Int,       // 현재 페이지
    val text: String,    // 추출된 문자열
    val x: Float,        // PDF 좌표계 X
    val y: Float,         // PDF 좌표계 Y
    val fontSize: Float = 75f // 글자 크기
)