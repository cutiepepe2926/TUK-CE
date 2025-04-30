package com.example.test_app

import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.test_app.databinding.ActivityPdfToolbarBinding
import com.example.test_app.databinding.ActivityPdfViewerBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import com.github.barteksc.pdfviewer.PDFView
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Toast
import android.widget.Toast.makeText
import com.example.test_app.view.DrawingView
import com.example.test_app.model.Stroke
import com.example.test_app.utils.MyDocManager
import com.example.test_app.utils.PdfExporter
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener


class PdfViewerActivity : AppCompatActivity() {

    //바인딩 객체 선언
    private lateinit var binding: ActivityPdfViewerBinding

    //!!신규!! 2개
    private lateinit var pdfView: PDFView
    private lateinit var drawingView: DrawingView

    // 페이지별 필기 데이터를 저장 (페이지 번호 -> Stroke 목록)
    //!!신규!! 1개
    private val pageStrokes = mutableMapOf<Int, MutableList<Stroke>>()
    
    //!!신규!! 3개
    private var currentPage = 0       // 현재 페이지 인덱스
    private var totalPages = 0        // 전체 페이지 수 (PdfRenderer로 계산)
    private lateinit var myDocPath: String

    // !!신규!! 1개
    // 모드: true = 필기, false = 드래그
    private var isPenMode = true



    //툴바 객체 선언
    private lateinit var toolbinding : ActivityPdfToolbarBinding

    private var isRecording = false // 🔹 녹음 상태 저장

    private var mediaRecorder: MediaRecorder? = null // 🔹 녹음기 객체
    private var audioFilePath: String = "" // 🔹 저장될 파일 경로


    // 드래그 모드일 때 PDFView의 zoom/offset을 DrawingView에 반영하기 위한 Handler
    private val handler = Handler(Looper.getMainLooper())
    private val updateTransformRunnable = object : Runnable {
        override fun run() {
            val scale = pdfView.zoom
            val offsetX = pdfView.currentXOffset
            val offsetY = pdfView.currentYOffset
            drawingView.setPdfViewInfo(scale, offsetX, offsetY)
            handler.postDelayed(this, 10)
        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //바인딩 객체 획득
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        toolbinding = ActivityPdfToolbarBinding.inflate(layoutInflater)

        setContentView(binding.root)

        //!!신규 2개!!
        pdfView = binding.pdfView
        drawingView = binding.drawingView

        //!!신규 2개!!
        // myDoc 로드 (PDF 경로 및 기존 필기 데이터)
        myDocPath = intent.getStringExtra("myDocPath") ?: return
        val myDocData = MyDocManager(this).loadMyDoc(File(myDocPath))

        // PdfRenderer로 전체 페이지 수 계산
        totalPages = getTotalPages(File(getBasePdfPath()))

        // 저장된 stroke들을 페이지별로 분리 (stroke의 page 값이 있다면 사용)
        myDocData.strokes.groupBy { it.page }.forEach { (page, strokes) ->
            pageStrokes[page] = strokes.toMutableList()
        }
        if (pageStrokes.isEmpty()) {
            pageStrokes[0] = mutableListOf()
        }

        // 첫 페이지 로드
        currentPage = 0
        loadSinglePage(currentPage)

        // "다음 페이지" 버튼
        binding.nextPageButton.setOnClickListener {
            updateCurrentPageStrokes()
            if (currentPage < totalPages - 1) {
                currentPage++
                loadSinglePage(currentPage)
            }
        }

        // "이전 페이지" 버튼
        binding.prevPageButton.setOnClickListener {
            updateCurrentPageStrokes()
            if (currentPage > 0) {
                currentPage--
                loadSinglePage(currentPage)
            }
        }

        // Export 버튼은 기존 로직 그대로
        binding.exportButton.setOnClickListener {
            exportToPdf()
        }

        // 모드 전환 버튼
        binding.toggleModeButton.setOnClickListener {
            isPenMode = !isPenMode
            if (isPenMode) {
                binding.toggleModeButton.text = "필기"
                drawingView.setDrawingEnabled(true)
            } else {
                binding.toggleModeButton.text = "드래그"
                drawingView.setDrawingEnabled(false)
            }
        }

        // 드래그 모드일 때 DrawingView가 PDFView와 동기화되도록 업데이트 시작
        handler.post(updateTransformRunnable)


        //여기까지가 새로운 코드
        //밑에 코드 수정 필요
        

        // 툴바 설정
        setSupportActionBar(toolbinding.pdfToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // 타이틀 비설정

        // 툴바 버튼 설정(뒤로가기)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        // 🔹 뒤로 가기 버튼 기능
        btnBack.setOnClickListener {
            updateCurrentPageStrokes()
            val allStrokes = pageStrokes.flatMap { it.value }
            MyDocManager(this).saveMyDoc(
                fileName = File(myDocPath).name,
                pdfFilePath = getBasePdfPath(),
                strokes = allStrokes
            )
            super.onBackPressed()
            Toast.makeText(this, "✅ 저장 완료",Toast.LENGTH_SHORT).show();
        }

        // 툴바 버튼 설정(저장하기)
        val btnSave = findViewById<ImageButton>(R.id.btnSave)
        // 🔹 저장 하기 버튼 기능
        btnSave.setOnClickListener {
            updateCurrentPageStrokes()
            val allStrokes = pageStrokes.flatMap { it.value }
            MyDocManager(this).saveMyDoc(
                fileName = File(myDocPath).name,
                pdfFilePath = getBasePdfPath(),
                strokes = allStrokes
            )
            Toast.makeText(this, "✅ 저장 완료",Toast.LENGTH_SHORT).show();
        }

        // 툴바 버튼 설정(필기삭제)
        val btnEraser = findViewById<ImageButton>(R.id.btnEraser)
        // 🔹 필기 삭제 버튼 기능
        btnEraser.setOnClickListener {
            println("🧽 현재 페이지 ($currentPage) 필기 삭제")

            // 현재 페이지 필기 데이터 삭제
            pageStrokes[currentPage]?.clear()

            // DrawingView에서 화면도 갱신
            drawingView.setStrokes(emptyList())

            Toast.makeText(this, "현재 페이지 필기가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }


        // 툴바 버튼 설정(녹음하기)
        val btnRecord = findViewById<ImageButton>(R.id.btnRecord)
        // 🔹 음성 녹음 버튼 기능
        // 🔹 녹음 버튼 기능 (아이콘 변경)
        // 🔹 녹음 버튼 기능 (아이콘 변경 & 녹음 기능 추가)
        btnRecord.setOnClickListener {
            println("🎤 녹음 버튼이 클릭됨!")
            if (isRecording) {
                stopRecording(btnRecord)
            } else {
                startRecording(btnRecord)
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTransformRunnable)
    }

    /**
     * 지정한 페이지 인덱스의 페이지만 로드하는 함수
     */
    private fun loadSinglePage(pageIndex: Int) {
        pdfView.fromFile(File(getBasePdfPath()))
            .enableSwipe(false)  // 스와이프로 전환하지 않고 버튼으로 변경
            .enableDoubletap(true) // 드래그 모드에서는 더블 탭 줌 지원
            .pages(pageIndex)    // 해당 페이지만 로드
            .onLoad(object : OnLoadCompleteListener {
                override fun loadComplete(nbPages: Int) {
                    // 로드된 페이지는 1개이므로, 현재 페이지의 필기를 DrawingView에 적용
                    val strokes = pageStrokes[pageIndex] ?: mutableListOf()
                    strokes.forEach { it.page = pageIndex }
                    drawingView.setStrokes(strokes)
                }
            })
            .load()
    }

    /**
     * 현재 페이지의 DrawingView 필기를 저장하고, pageStrokes 맵에 업데이트하는 함수
     */
    private fun updateCurrentPageStrokes() {
        val strokes = drawingView.getStrokes().toMutableList()
        strokes.forEach { it.page = currentPage }
        pageStrokes[currentPage] = strokes
    }

    override fun onBackPressed() {
        updateCurrentPageStrokes()
        val allStrokes = pageStrokes.flatMap { it.value }
        MyDocManager(this).saveMyDoc(
            fileName = File(myDocPath).name,
            pdfFilePath = getBasePdfPath(),
            strokes = allStrokes
        )
        super.onBackPressed()
    }

    private fun exportToPdf() {
        PdfExporter.export(
            context = this,
            myDocPath = myDocPath,
            outputFileName = "Exported_${System.currentTimeMillis()}.pdf"
        )
    }

    private fun getBasePdfPath(): String {
        val myDocData = MyDocManager(this).loadMyDoc(File(myDocPath))
        return myDocData.pdfFilePath
    }

    /**
     * PdfRenderer를 이용해 PDF 파일의 전체 페이지 수 계산 (API 21 이상)
     */
    private fun getTotalPages(pdfFile: File): Int {
        var pageCount = 0
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fileDescriptor).use { renderer ->
            pageCount = renderer.pageCount
        }
        fileDescriptor.close()
        return pageCount
    }

    // ✅ 녹음 시작 함수
    private fun startRecording(btnRecord: ImageButton) {
        if (!checkPermissions()) {
            println("🚨 권한이 없어서 녹음을 시작할 수 없습니다!")
            requestPermissions()
            return
        }

        isRecording = true
        btnRecord.setImageResource(R.drawable.ic_recording) // 🔴 아이콘 변경

        val fileName = generateFileName() // 🔹 저장할 파일 이름 생성
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) // 🔹 앱 내부 저장소 사용
        val audioFile = File(storageDir, fileName) // 🔹 파일 생성
        audioFilePath = audioFile.absolutePath

        println("📂 파일 저장 경로: $audioFilePath") // ✅ 파일 경로 출력

        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC) // 🔹 마이크 사용
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // 🔹 MP4 포맷 (MP3와 유사)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // 🔹 AAC 인코딩
                setOutputFile(audioFilePath) // 🔹 파일 저장 경로
                prepare()
                start()
            }
            println("🎤 녹음 시작됨!")
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 녹음 시작 중 오류 발생: ${e.message}")
        }
    }


    // ✅ 녹음 중지 함수
    private fun stopRecording(btnRecord: ImageButton) {
        println("🛑 녹음 중지 요청됨")

        isRecording = false
        btnRecord.setImageResource(R.drawable.ic_record) // 🎤 아이콘 변경

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            println("✅ 녹음 중지 완료! 파일 저장됨: $audioFilePath")
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 녹음 중지 중 오류 발생: ${e.message}")
        }
    }


    // ✅ 파일 이름 생성 함수 (yyyyMMdd_HHmm.mp3 형식)
    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "record_$timeStamp.mp3"
    }

    // ✅ 녹음 권한 확인 함수
    private fun checkPermissions(): Boolean {
        return try {
            val recordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

            println("🔍 권한 확인 - RECORD_AUDIO: $recordPermission")

            recordPermission == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 권한 확인 중 오류 발생: ${e.message}")
            false // 예외 발생 시 false 반환 (앱 크래시 방지)
        }
    }



    // ✅ 녹음 권한 요청 함수
    private fun requestPermissions() {
        try {
            println("🔔 권한 요청 실행")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO), // 🚀 파일 저장 권한 제거
                200
            )
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 권한 요청 중 오류 발생: ${e.message}")
        }
    }


    //권한 승인 여부 확인
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 200) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                println("✅ 오디오 녹음 권한이 승인되었습니다!")
            } else {
                println("❌ 오디오 녹음 권한이 거부되었습니다.")
            }
        }
    }
    
}