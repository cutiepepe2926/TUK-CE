package com.example.test_app.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.test_app.model.PointF
import com.example.test_app.model.Stroke
import com.example.test_app.model.TextAnnotation

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    /* ---------- 필드 ---------- */
    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null
    private val textAnnotations = mutableListOf<TextAnnotation>()

    private var pdfScale = 1f
    private var pdfOffsetX = 0f
    private var pdfOffsetY = 0f
    private var drawingEnabled = true
    private var viewCurrentPage = 0
    private var currentColor = Color.RED
    private var currentWidth = 5f

    private val strokePaint = Paint().apply {
        isAntiAlias = true; style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply { isAntiAlias = true }

    /* ---------- 외부 Setter ---------- */
    fun setPdfViewInfo(scale: Float, offsetX: Float, offsetY: Float) {
        pdfScale = scale; pdfOffsetX = offsetX; pdfOffsetY = offsetY; invalidate()
    }
    fun setTextAnnotations(annos: List<TextAnnotation>) {
        textAnnotations.clear(); textAnnotations.addAll(annos); invalidate()
    }
    fun setCurrentPage(page: Int) { viewCurrentPage = page; invalidate() }
    fun setDrawingEnabled(b: Boolean) { drawingEnabled = b }
    fun setStrokes(list: List<Stroke>) { strokes.clear(); strokes.addAll(list); invalidate() }
    fun getStrokes(): List<Stroke> = strokes

    /* ---------- 터치 ---------- */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!drawingEnabled) return false
        val xPdf = (e.x - pdfOffsetX) / pdfScale
        val yPdf = (e.y - pdfOffsetY) / pdfScale
        when (e.action) {
            MotionEvent.ACTION_DOWN -> currentStroke = Stroke(currentColor, currentWidth, mutableListOf(PointF(xPdf, yPdf)), viewCurrentPage)
            MotionEvent.ACTION_MOVE -> currentStroke?.points?.add(PointF(xPdf, yPdf))
            MotionEvent.ACTION_UP   -> {
                currentStroke?.points?.add(PointF(xPdf, yPdf))
                currentStroke?.let { strokes.add(it) }; currentStroke = null
            }
        }
        invalidate(); return true
    }

    /* ---------- 그리기 ---------- */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.translate(pdfOffsetX, pdfOffsetY); canvas.scale(pdfScale, pdfScale)

        // ① 기존 필기
        for (st in strokes.filter { it.page == viewCurrentPage }) {
            strokePaint.color = st.color; strokePaint.strokeWidth = st.width
            for (i in 0 until st.points.size - 1) {
                val s = st.points[i]; val e = st.points[i + 1]
                canvas.drawLine(s.x, s.y, e.x, e.y, strokePaint)
            }
        }
        currentStroke?.let { st ->
            strokePaint.color = st.color; strokePaint.strokeWidth = st.width
            for (i in 0 until st.points.size - 1) {
                val s = st.points[i]; val e = st.points[i + 1]
                canvas.drawLine(s.x, s.y, e.x, e.y, strokePaint)
            }
        }

        // ② 텍스트 어노테이션 (줄바꿈 지원)
        for (anno in textAnnotations.filter { it.page == viewCurrentPage }) {
            textPaint.textSize = anno.fontSize
            textPaint.color = Color.BLACK
            val lines = anno.text.split('\n')
            var y = anno.y
            for (ln in lines) {
                canvas.drawText(ln, anno.x, y, textPaint)
                y += textPaint.textSize + 8f          // 줄 간 간격
            }
        }
        canvas.restore()
    }

    /* -------- 펜 크기 및 색상 -------*/
    fun setCurrentStrokeColor(color: Int){
        currentColor = color
    }
    fun setCurrentStrokeWidth(width: Float){
        currentWidth = width
    }
}