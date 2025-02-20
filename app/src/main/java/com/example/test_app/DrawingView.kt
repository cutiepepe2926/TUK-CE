package com.example.test_app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    //사용자가 필기한 선을 저장하는 객체 (Path)
    private var path = Path()

    //필기 스타일을 정의하는 Paint 객체
    private var paint = Paint().apply {
        color = Color.BLACK // 기본 색상 검은색
        style = Paint.Style.STROKE //선만 그리도록 설정
        strokeWidth = 8f //선의 두께 설정
        isAntiAlias = true //부드러운 선이 그려지도록 설정
    }
    //실제 필기 내용을 저장하는 Bitmap
    private var canvasBitmap: Bitmap? = null
    //사용자가 그리는 내용을 담을 Canvas 객체
    private var drawCanvas: Canvas? = null
    //// 페이지별 필기 저장
    private val pageDrawings = HashMap<Int, Bitmap>()
    //PDFView 참조
    var pdfView: View? = null
    // 📌 true = 필기 모드, false = 스크롤 모드
    var isDrawingMode = true



    //현재 페이지의 필기 내용을 Bitmap으로 변환하여 저장
    fun saveCurrentPageDrawing(currentPage: Int) {

        if (canvasBitmap == null) {
            println("⚠ canvasBitmap이 null (페이지: $currentPage)")
            return
        }

        // ✅ 기존 Bitmap 재사용 & 투명도 유지 (최적화 버전)
        val existingBitmap = pageDrawings[currentPage]
        val bitmap = existingBitmap ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmap)
        //tempCanvas.drawColor(Color.WHITE) // ✅ 배경을 흰색으로 설정 (검은색 방지)
        
        tempCanvas.drawBitmap(canvasBitmap!!, 0f, 0f, null) // 기존 필기 복사
        tempCanvas.drawPath(path, paint) // 새 필기 추가

        pageDrawings[currentPage] = bitmap // 📌 현재 페이지 필기 저장
        println("✅ 필기 저장 완료 (페이지: $currentPage)")
    }

    //페이지가 변경될 때, 기존 필기 내용을 삭제하고 새 페이지의 필기 내용을 불러옴
    fun loadPageDrawing(currentPage: Int) {
        drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) // 기존 필기 삭제
        //pageDrawings[currentPage]?.let { drawCanvas?.drawBitmap(it, 0f, 0f, null) } // 필기 로드
        pageDrawings[currentPage]?.let {
            println("✅ 기존 필기 로드 완료 (페이지: $currentPage)")
            drawCanvas?.drawBitmap(it, 0f, 0f, null) // 필기 로드
        } ?: println("⚠ 불러올 필기 없음 (페이지: $currentPage)")

        invalidate() //화면을 다시 그려서 변경된 필기 내용을 반영
    }

    //터치 이벤트를 받을 수 있도록 포커스를 활성화
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    //뷰의 크기가 변경될 때, 새로운 Bitmap을 생성하여 필기 내용을 저장할 수 있도록 설정
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap!!)
    }

    //현재까지 그려진 Bitmap을 화면에 출력
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap!!, 0f, 0f, null)
        canvas.drawPath(path, paint)
    }

    //터치 이벤트 처리
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawingMode) {
            // 📌 스크롤 모드일 때 PDFView에 터치 이벤트 전달
            pdfView?.dispatchTouchEvent(event)
            return false
        }

        // 📌 필기 모드 (isDrawingMode = true)일 때만 필기 가능
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> { //사용자가 화면을 터치하면 경로 시작
                path.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> { //손가락을 움직일 때 선을 그림
                path.lineTo(x, y)
                drawCanvas?.drawPath(path, paint) // 실제 Canvas에 그리기
            }
            MotionEvent.ACTION_UP -> { //손가락을 떼면 선을 초기화
                path.reset()
            }
        }
        invalidate() //뷰를 다시 그려서 변경 내용 반영
        return true
    }

    // 필기 모드 변경 함수
    fun toggleDrawingMode(isDrawing: Boolean) {
        isDrawingMode = isDrawing
    }

    //필기 내용을 Bitmap으로 변환하는 함수
    fun getPageDrawingBitmap(pageIndex: Int): Bitmap? {
        return pageDrawings[pageIndex]?.let { Bitmap.createBitmap(it) }
    }

    //필기 메모리 초기화 함수
    fun clearAllDrawings() {
        println("🧽 필기 내용 초기화 함수 호출됨")  // ✅ 디버깅 로그 추가

        for ((_, bitmap) in pageDrawings) {
            bitmap.recycle() // ✅ 메모리 해제
        }
        pageDrawings.clear() // ✅ 모든 데이터 삭제
        path.reset() // ✅ 현재 경로도 초기화
        drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) // ✅ 실제 화면도 지우기

        invalidate() // ✅ 화면을 다시 그려서 변경 사항 반영

        println("✅ 모든 필기 데이터 삭제 완료")  // ✅ 확인용 로그 추가
    }

    // 🔹 특정 페이지의 필기만 삭제하는 함수 추가
    fun clearCurrentPageDrawing(currentPage: Int) {
        println("🧽 페이지 ${currentPage}의 필기 삭제 요청됨")

        // 현재 페이지의 필기 비트맵만 제거
        pageDrawings.remove(currentPage)

        // 캔버스를 투명하게 초기화하여 반영
        drawCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        invalidate() // 화면 다시 그리기

        println("✅ 페이지 ${currentPage}의 필기 삭제 완료")
    }



}
