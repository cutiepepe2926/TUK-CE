package com.example.test_app.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.test_app.model.PointF
import com.example.test_app.model.Stroke
import com.example.test_app.model.TextAnnotation

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    /** ---------- 필기(Stroke) 관련 ---------- */
    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null

    /** ---------- 텍스트 어노테이션 ---------- */
    private val textAnnotations = mutableListOf<TextAnnotation>()

    /** ---------- PDF 변환 정보 ---------- */
    private var pdfScale   = 1f
    private var pdfOffsetX = 0f
    private var pdfOffsetY = 0f

    /** ---------- 상태 플래그 ---------- */
    private var drawingEnabled  = true
    private var viewCurrentPage = 0   // ★ PdfViewerActivity에서 전달받는 현재 페이지

    /** ---------- Paint ---------- */
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 20f
    }

    /*------------------------------------------------------------
     *  외부에서 호출하는 Setter 메서드
     *-----------------------------------------------------------*/

    /** PDFView 의 스케일·오프셋 정보를 주기적으로 전달 */
    fun setPdfViewInfo(scale: Float, offsetX: Float, offsetY: Float) {
        pdfScale   = scale
        pdfOffsetX = offsetX
        pdfOffsetY = offsetY
        invalidate()
    }

    /** 이 페이지의 텍스트 어노테이션 세트 */
    fun setTextAnnotations(annos: List<TextAnnotation>) {
        textAnnotations.clear()
        textAnnotations.addAll(annos)
        invalidate()
    }

    /** 현재 페이지 번호 전달 */
    fun setCurrentPage(page: Int) {
        viewCurrentPage = page
        invalidate()
    }

    /** 필기 가능 여부 */
    fun setDrawingEnabled(enabled: Boolean) {
        drawingEnabled = enabled
    }

    /** 외부에서 Stroke 주입 */
    fun setStrokes(loadedStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(loadedStrokes)
        invalidate()
    }

    /** 현재 Stroke 목록 반환 */
    fun getStrokes(): List<Stroke> = strokes

    /*------------------------------------------------------------
     *  터치 이벤트 – 필기 입력
     *-----------------------------------------------------------*/
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawingEnabled) return false   // 드래그 모드면 터치 이벤트 넘김

        // 화면 좌표 → PDF 좌표
        val xPdf = (event.x - pdfOffsetX) / pdfScale
        val yPdf = (event.y - pdfOffsetY) / pdfScale

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentStroke = Stroke(
                    color   = Color.RED,
                    width   = 5f,
                    points  = mutableListOf(PointF(xPdf, yPdf)),
                    page    = viewCurrentPage
                )
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke?.points?.add(PointF(xPdf, yPdf))
            }
            MotionEvent.ACTION_UP   -> {
                currentStroke?.points?.add(PointF(xPdf, yPdf))
                currentStroke?.let { strokes.add(it) }
                currentStroke = null
            }
        }
        invalidate()
        return true
    }

    /*------------------------------------------------------------
     *  그리기
     *-----------------------------------------------------------*/
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.translate(pdfOffsetX, pdfOffsetY)
        canvas.scale(pdfScale, pdfScale)

        /* ----- ① 저장된 Stroke 그리기 ----- */
        for (stroke in strokes.filter { it.page == viewCurrentPage }) {
            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.width
            for (i in 0 until stroke.points.size - 1) {
                val s = stroke.points[i]
                val e = stroke.points[i + 1]
                canvas.drawLine(s.x, s.y, e.x, e.y, strokePaint)
            }
        }

        /* ----- ② 현재 그리고 있는 Stroke ----- */
        currentStroke?.let { stroke ->
            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.width
            for (i in 0 until stroke.points.size - 1) {
                val s = stroke.points[i]
                val e = stroke.points[i + 1]
                canvas.drawLine(s.x, s.y, e.x, e.y, strokePaint)
            }
        }

        /* ----- ③ 텍스트 어노테이션 ----- */
        textAnnotations
            .filter { it.page == viewCurrentPage }
            .forEach { anno ->
                // 배경 박스
                val padding = 8f
                val w = textPaint.measureText(anno.text)

                textPaint.style = Paint.Style.FILL
                textPaint.color = Color.WHITE
                canvas.drawRect(
                    anno.x - padding,
                    anno.y - textPaint.textSize - padding,
                    anno.x + w + padding,
                    anno.y + padding,
                    textPaint
                )

                // 텍스트
                textPaint.color = Color.BLACK
                canvas.drawText(anno.text, anno.x, anno.y, textPaint)
            }

        canvas.restore()
    }
}
