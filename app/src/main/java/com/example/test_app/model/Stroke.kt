package com.example.test_app.model

// 1. 점 좌표를 나타내는 데이터 클래스
// 필기나 선을 구성하는 개별 점의 좌표
data class PointF(val x: Float, val y: Float)

// 2. 필기 데이터를 구성하는 클래스 (펜 선 하나 = Stroke 하나)
data class Stroke(
    val color: Int, // 선 색상
    val width: Float, // 선 굵기
    val points: MutableList<PointF>, // 선을 구성하는 좌표 리스트 (연속된 점들)
    var page: Int = 0  // 어느 페이지에서 작성된 필기인지 표시 (기본값은 0페이지)
)
// - 사용자가 그린 하나의 펜 선을 표현
// - PDF 페이지 단위로 stroke들을 분리해서 저장 가능

// 3. 텍스트 주석(예: OCR 결과)을 저장하는 클래스
data class TextAnnotation(
    val page: Int,       // 텍스트가 위치한 PDF 페이지
    val text: String,    // 표시할 텍스트 내용 (예: OCR 결과)
    val x: Float,        // PDF 좌표계 X
    val y: Float,         // PDF 좌표계 Y
    val fontSize: Float = 75f // 텍스트 크기 (기본값: 75f)
)
// - OCR 등으로 추출된 텍스트를 PDF 상에 위치시키는 용도로 사용
// - PDF 좌표계를 기준으로 위치를 저장함