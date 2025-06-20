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
    private var isEraserEnabled = false
    private var viewCurrentPage = 0
    private var currentColor = Color.BLACK
    private var currentWidth = 5f
    private val eraserSize: Float = 15f * resources.displayMetrics.density
    private var eraseOverlayX = -1f
    private var eraseOverlayY = -1f

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
    }

    private val eraserPaint = Paint().apply {
        color = Color.RED
        alpha = 100
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    /* ---------- 외부 Setter ---------- */
    fun setPdfViewInfo(scale: Float, offsetX: Float, offsetY: Float) {
        pdfScale = scale; pdfOffsetX = offsetX; pdfOffsetY = offsetY; invalidate()
    }
    fun setTextAnnotations(annos: List<TextAnnotation>) {
        textAnnotations.clear(); textAnnotations.addAll(annos); invalidate()
    }
    fun setCurrentPage(page: Int) { viewCurrentPage = page; invalidate() }
    fun setDrawingEnabled(b: Boolean) {
        drawingEnabled = b
        /* ➜  드로잉/지우개가 모두 OFF 면 View 자체를 비활성화해
           Touch-Target 선정 단계에서 제외시킨다.  */
        isEnabled = b || isEraserEnabled
    }
    fun setEraserEnabled(b: Boolean) {
        isEraserEnabled = b
        isEnabled = drawingEnabled || b
    }
    fun setStrokes(list: List<Stroke>) { strokes.clear(); strokes.addAll(list); invalidate() }
    fun getStrokes(): List<Stroke> = strokes

    /* ---------- 터치 ---------- */
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!drawingEnabled && !isEraserEnabled){
            return false
        }
        val xPdf = (e.x - pdfOffsetX) / pdfScale
        val yPdf = (e.y - pdfOffsetY) / pdfScale
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if(isEraserEnabled){
                    performErase(e.x, e.y, xPdf, yPdf)
                }else{
                    if(e.actionMasked == MotionEvent.ACTION_DOWN){
                        currentStroke = Stroke(
                            color = currentColor,
                            width = currentWidth,
                            page = viewCurrentPage,
                            points = mutableListOf(PointF(xPdf, yPdf)),
                        )
                    }else{
                        currentStroke?.points?.add(PointF(xPdf, yPdf))
                    }
                }
            }
            MotionEvent.ACTION_UP   -> {
                if(!isEraserEnabled){
                    currentStroke?.let { strokes.add(it)}
                    currentStroke = null
                }
            }
        }
        invalidate()
        return true
    }

    private fun performErase(viewX: Float, viewY: Float, pdfX: Float, pdfY: Float) {
        eraseOverlayX = viewX
        eraseOverlayY = viewY

        val radiusPdf = eraserSize / pdfScale

        // 새로 갱신할 strokes 리스트
        val newStrokes = mutableListOf<Stroke>()

        // 기존 strokes 순회
        val it = strokes.iterator()
        while (it.hasNext()) {
            val st = it.next()
            if (st.page != viewCurrentPage) {
                newStrokes += st
                continue
            }

            // 현재 stroke의 포인트 리스트
            val pts = st.points
            var segment = mutableListOf<PointF>()

            fun flushSegment() {
                if (segment.size >= 2) {
                    // 남은 segment를 새 Stroke로 추가
                    newStrokes += Stroke(
                        color  = st.color,
                        width  = st.width,
                        page   = st.page,
                        points = segment.toMutableList()
                    )
                }
                segment = mutableListOf()
            }

            // 각 포인트가 지우개 반경에 있는지 검사하며 분할
            for (pt in pts) {
                if (PointF(pt.x, pt.y).let { (x, y) ->
                        // dist in pdf coords
                        val dx = x - pdfX
                        val dy = y - pdfY
                        Math.hypot(dx.toDouble(), dy.toDouble()) > radiusPdf
                    }
                ) {
                    // 반경 밖 ⇒ segment에 포함
                    segment.add(pt)
                } else {
                    // 반경 안 ⇒ 지금까지 모은 segment를 하나의 Stroke로 마무리
                    flushSegment()
                }
            }
            // 루프 끝나고 남은 segment flush
            flushSegment()

            // 원본 획은 모두 분할했으므로 지우개가 적용된 부분만 남기고 원본 제거
            it.remove()
        }

        // 분할한 새 획들로 strokes 교체
        strokes.clear()
        strokes.addAll(newStrokes)
    }


    /* ---------- 그리기 ---------- */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(pdfOffsetX, pdfOffsetY)
        canvas.scale(pdfScale, pdfScale)

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

        // 지우개 모드일 때 터치 위에 커서 원 그리기
        if(isEraserEnabled && eraseOverlayX >= 0 && eraseOverlayY >= 0){
            canvas.drawCircle(eraseOverlayX, eraseOverlayY, eraserSize, eraserPaint)
        }
    }

    /* -------- 펜 크기 및 색상 -------*/
    fun setCurrentStrokeColor(color: Int){
        currentColor = color
    }
    fun setCurrentStrokeWidth(width: Float){
        currentWidth = width
    }
}