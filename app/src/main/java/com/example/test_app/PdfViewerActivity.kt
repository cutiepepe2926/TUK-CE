package com.example.test_app

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.test_app.databinding.ActivityPdfToolbarBinding
import com.example.test_app.databinding.ActivityPdfViewerBinding
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.shockwave.pdfium.PdfiumCore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest



class PdfViewerActivity : AppCompatActivity() {

    //바인딩 객체 선언
    private lateinit var binding: ActivityPdfViewerBinding
    //툴바 객체 선언
    private lateinit var toolbinding : ActivityPdfToolbarBinding

    private var isRecording = false // 🔹 녹음 상태 저장

    private var isDrawingMode = true // 기본값: 필기 모드

    private var mediaRecorder: MediaRecorder? = null // 🔹 녹음기 객체
    private var audioFilePath: String = "" // 🔹 저장될 파일 경로


    private var currentPage = 0 // 현재 페이지 번호
    
    fun onPageChanged(newPage: Int) {
        // ✅ 기존 페이지의 필기 내용 저장
        binding.drawingView.saveCurrentPageDrawing(currentPage)

        // ✅ 페이지 변경
        currentPage = newPage

        // ✅ 새로운 페이지의 필기 내용 불러오기
        binding.drawingView.loadPageDrawing(currentPage)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.drawingView.clearAllDrawings()
        // ✅ 액티비티 종료 시 필기 데이터 해제 (안하면 메모리누수)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //바인딩 객체 획득
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        toolbinding = ActivityPdfToolbarBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // 📌 MainActivity에서 전달된 URI와 이름 가져오기
        val pdfUriString = intent.getStringExtra("pdfUri")
        val pdfName = intent.getStringExtra("pdfName")

        //PDF URI가 존재하면 Uri 객체로 변환
        if (pdfUriString != null) {
            var pdfUri = Uri.parse(pdfUriString)

            println("pdfUri : $pdfUri")
            println("pdfName : $pdfName")

            try {
                // ✅ SAF(content://com.android.providers...) URI만 권한 요청
                if (pdfUri.authority?.contains("com.android.providers") == true) {
                    contentResolver.takePersistableUriPermission(
                        pdfUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                displayPdf(pdfUri)

            } catch (e: SecurityException) {
                e.printStackTrace()
                println("🚨 권한 문제 발생: ${e.message}")
            }
        } else {
            println("PdfViewerActivity에서 받은 URI가 null입니다.")
        }

        // 📌 DrawingView가 PDFView를 참조하도록 설정
        binding.drawingView.pdfView = binding.pdfView


        // 툴바 설정
        setSupportActionBar(toolbinding.pdfToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // 타이틀 비설정

        // 툴바 버튼 설정(뒤로가기)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        // 🔹 뒤로 가기 버튼 기능
        btnBack.setOnClickListener {
            onBackPressed()
        }

        // 툴바 버튼 설정(저장하기)
        val btnSave = findViewById<ImageButton>(R.id.btnSave)
        // 🔹 저장 하기 버튼 기능
        btnSave.setOnClickListener {
            savePdfWithDrawing()
        }

        // 툴바 버튼 설정(필기삭제)
        val btnEraser = findViewById<ImageButton>(R.id.btnEraser)
        // 🔹 필기 삭제 버튼 기능
        btnEraser.setOnClickListener {
            println("🧽 현재 페이지 ($currentPage) 필기 삭제")

            // 🔹 현재 페이지의 필기만 삭제
            binding.drawingView.clearCurrentPageDrawing(currentPage)
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


        // 📌 이전 페이지로 이동
        binding.prevPageButton.setOnClickListener {
            if (currentPage > 0) {
                onPageChanged(currentPage - 1)
                binding.pdfView.jumpTo(currentPage, true)
            }
        }

        // 📌 다음 페이지로 이동
        binding.nextPageButton.setOnClickListener {
            val pageCount = binding.pdfView.pageCount
            if (currentPage < pageCount - 1) {
                onPageChanged(currentPage + 1)
                binding.pdfView.jumpTo(currentPage, true)
            }
        }

        // 필기 모드 / 스크롤 모드 전환 버튼
        binding.toggleModeButton.setOnClickListener {
            isDrawingMode = !isDrawingMode  // 모드 변경
            binding.drawingView.toggleDrawingMode(isDrawingMode)

            // 버튼 텍스트 변경
            binding.toggleModeButton.text = if (isDrawingMode) "필기 모드" else "스크롤 모드"
        }
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



    //PDF 열기
    private fun displayPdf(uri: Uri) {
        try {
            println("PDF 로드 시도: $uri")

            //기존 필기 내용 초기화
            binding.drawingView.clearAllDrawings()

            binding.pdfView.fromUri(uri)
                .enableSwipe(false) // 🔹 스와이프(손가락으로 넘기기) 비활성화
                .swipeHorizontal(false) // 🔹 가로 스크롤 비활성화 (세로로 넘김)
                .enableDoubletap(true) // 🔹 더블탭 확대 활성화
                .defaultPage(0) // 🔹 PDF를 첫 번째 페이지부터 시작
                .enableAnnotationRendering(true) // 🔹 PDF 내부의 주석(메모, 마크업) 렌더링 활성화
                .fitEachPage(true) // 🔹 페이지 크기에 맞게 자동 조정
                .pageFitPolicy(FitPolicy.BOTH) // 🔹 페이지 크기 조정 정책 (너비 & 높이 모두 맞춤)
                .spacing(10) // 🔹 페이지 간 간격 설정 (10dp)
                .pageSnap(true) // 🔹 페이지 자동 스냅(페이지 이동 시 정확한 위치로 맞춤)
                .pageFling(true) // 🔹 페이지를 빠르게 넘길 수 있도록 설정
                .onPageChange { page, _ ->
                    onPageChanged(page)
                }
                .load()

        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 PDF 로드 중 오류 발생: ${e.message}")
        }
    }

    // PDF + 필기 내용을 저장하는 함수
    //최적화 버전 + 해상도 유지 (코드 분리 필요)
    private fun savePdfWithDrawing() {
        try {
            //파일명 설정 및 공백 분리
            // 기존 파일명 유지 (saved_ 제거)
            val originalFileName = intent.getStringExtra("pdfName") ?: "new_document"
            val cleanedFileName = originalFileName.replace(".pdf", "").replace(" ", "_") // 공백만 변환


            //저장할 디렉토리 생성
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SavedPDFs")
            if (!directory.exists()) directory.mkdirs()

            //새로운 파일 생성 (동일명 존재 시 덮어쓰기)
            // 파일 경로 설정 (원본 파일명 그대로 사용)
            val newFile = File(directory, "${cleanedFileName}.pdf")
            if (newFile.exists()) newFile.delete()

            //원본 PDF 파일 열기
            val uri = Uri.parse(intent.getStringExtra("pdfUri"))
            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return
            val pdfiumCore = PdfiumCore(this)
            val pdfDocumentOriginal = pdfiumCore.newDocument(parcelFileDescriptor)

            //새로운 PDF 문서 생성
            val pdfDocument = PdfDocument()
            val pageCount = pdfiumCore.getPageCount(pdfDocumentOriginal)

            //PDF 크기 조정 (고해상도) dpi는 그대로 두고 옆에 120f로 조정하기, 내릴수록 고해상도, 올리수록 저해상도
            //너무 내리면 용량 5배로 늘어남
            val dpi = resources.displayMetrics.densityDpi
            val scaleFactor = dpi / 120f

            //PDF의 모든 페이지를 복사 및 필기 내용 추가
            for (pageIndex in 0 until pageCount) {
                pdfiumCore.openPage(pdfDocumentOriginal, pageIndex)
                val pageWidth = pdfiumCore.getPageWidthPoint(pdfDocumentOriginal, pageIndex)
                val pageHeight = pdfiumCore.getPageHeightPoint(pdfDocumentOriginal, pageIndex)

                //페이지 크기 조정
                if (pageWidth <= 0 || pageHeight <= 0) continue
                val highResPageWidth = (pageWidth * scaleFactor).toInt()
                val highResPageHeight = (pageHeight * scaleFactor).toInt()

                //새로운 PDF 페이지 생성
                val pageInfo = PdfDocument.PageInfo.Builder(highResPageWidth, highResPageHeight, pageIndex).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                //원본 PDF 페이지를 비트맵으로 변환 후 추가
                val bitmap = Bitmap.createBitmap(highResPageWidth, highResPageHeight, Bitmap.Config.ARGB_8888)
                pdfiumCore.renderPageBitmap(pdfDocumentOriginal, bitmap, pageIndex, 0, 0, highResPageWidth, highResPageHeight)
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, highResPageWidth, highResPageHeight, true)

                //필기 내용 필터 적용
                val paint = Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                    isDither = true
                }

                canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

                //사용자의 필기 내용을 PDF에 추가
                val drawingBitmap = binding.drawingView.getPageDrawingBitmap(pageIndex)
                if (drawingBitmap != null) {
                    val highResDrawingBitmap = Bitmap.createScaledBitmap(drawingBitmap, highResPageWidth, highResPageHeight, true)
                    canvas.drawBitmap(highResDrawingBitmap, 0f, 0f, null)
                }

                //페이지 저장 후 다음 페이지로 이동
                pdfDocument.finishPage(page)
            }

            //원본 PDF 닫기
            pdfiumCore.closeDocument(pdfDocumentOriginal)
            parcelFileDescriptor.close()

            //새로운 PDF 파일 저장
            val outputStream = FileOutputStream(newFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()

            println("✅ PDF 저장 완료: ${newFile.absolutePath}")

            // 📌 새로운 저장된 PDF의 URI와 파일 이름 전달
            // 📌 새로운 PDF를 SharedPreferences에 저장 (문제 해결됐는 지 체크할 것)
            val newPdfUri = Uri.fromFile(newFile)

            val resultIntent = Intent().apply {
                putExtra("newPdfUri", newPdfUri.toString()) // 새로운 URI 전달
                putExtra("newPdfName", newFile.name) // 새로운 파일 이름 전달
            }
            setResult(RESULT_OK, resultIntent)
            finish() // PdfViewerActivity 종료

        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 PDF 저장 실패: ${e.message}")
        }
    }
}