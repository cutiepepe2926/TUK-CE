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

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // PDFView가 주는 배율/오프셋
    private var pdfScale = 1f
    private var pdfOffsetX = 0f
    private var pdfOffsetY = 0f

    // 필기 가능 여부 (필기 모드 vs 드래그 모드)
    private var drawingEnabled = true

    // NoteViewerActivity에서 매 주기적으로 호출
    fun setPdfViewInfo(scale: Float, offsetX: Float, offsetY: Float) {
        pdfScale = scale
        pdfOffsetX = offsetX
        pdfOffsetY = offsetY
        invalidate()
    }

    fun setDrawingEnabled(enabled: Boolean) {
        drawingEnabled = enabled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawingEnabled) {
            // 드래그 모드면 터치 이벤트 넘김(PDFView가 받음)
            return false
        }

        // 필기 모드에서는 화면 좌표 → PDF 좌표로 역변환하여 Stroke에 저장
        val xScreen = event.x
        val yScreen = event.y
        val xPdf = (xScreen - pdfOffsetX) / pdfScale
        val yPdf = (yScreen - pdfOffsetY) / pdfScale

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentStroke = Stroke(
                    color = Color.RED,
                    width = 5f,
                    points = mutableListOf(PointF(xPdf, yPdf))
                )
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke?.points?.add(PointF(xPdf, yPdf))
            }
            MotionEvent.ACTION_UP -> {
                currentStroke?.points?.add(PointF(xPdf, yPdf))
                currentStroke?.let { strokes.add(it) }
                currentStroke = null
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // (중요) 필기 모드든 드래그 모드든, 항상 PDF의 스케일/오프셋을 적용
        canvas.save()
        canvas.translate(pdfOffsetX, pdfOffsetY)
        canvas.scale(pdfScale, pdfScale)

        // PDF 좌표에 저장된 strokes를 그린다
        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            for (i in 0 until stroke.points.size - 1) {
                val s = stroke.points[i]
                val e = stroke.points[i + 1]
                canvas.drawLine(s.x, s.y, e.x, e.y, paint)
            }
        }

        // 현재 그리고 있는 stroke
        currentStroke?.let { stroke ->
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            for (i in 0 until stroke.points.size - 1) {
                val s = stroke.points[i]
                val e = stroke.points[i + 1]
                canvas.drawLine(s.x, s.y, e.x, e.y, paint)
            }
        }

        canvas.restore()
    }

    fun getStrokes(): List<Stroke> = strokes

    fun setStrokes(loadedStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(loadedStrokes)
        invalidate()
    }
}