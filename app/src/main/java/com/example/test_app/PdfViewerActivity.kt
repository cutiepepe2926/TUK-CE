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
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.github.barteksc.pdfviewer.PDFView
import android.graphics.pdf.PdfRenderer
import android.media.AudioFormat
import android.media.AudioRecord
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.example.test_app.view.DrawingView
import com.example.test_app.model.Stroke
import com.example.test_app.model.TextAnnotation
import com.example.test_app.utils.MyDocManager
import com.example.test_app.utils.PdfExporter
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.yalantis.ucrop.UCrop
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile


class PdfViewerActivity : AppCompatActivity() {

    /* ---------------- UI ---------------- */
    private lateinit var binding     : ActivityPdfViewerBinding
    private lateinit var pdfView     : PDFView
    private lateinit var drawingView : DrawingView

    /* ---------------- 데이터 ---------------- */
    private val pageStrokes = mutableMapOf<Int, MutableList<Stroke>>()
    private val textAnnos   = mutableListOf<TextAnnotation>()
    private var currentPage = 0
    private var totalPages  = 0
    private lateinit var myDocPath: String

    /* ---------------- 모드 ---------------- */
    private var isPenMode = true

    /* ---------------- OCR ---------------- */
    private val ocrOptions   = arrayOf("텍스트 추출", "번역")
    private val AUTHORITY    by lazy { "${packageName}.fileprovider" }
    private val CROP_EXTRACT = 1001
    private val CROP_TRANS   = 1002

    /* ---------------- 녹음 ---------------- */
    private var isRecording = false
    private var audioRecord: AudioRecord? = null // 🔹 녹음기 객체
    private var audioFilePath: String = "" // 🔹 저장될 파일 경로
    private var recordingThread: Thread? = null

    /* ---------------- Sync ---------------- */
    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            drawingView.setPdfViewInfo(pdfView.zoom, pdfView.currentXOffset, pdfView.currentYOffset)
            handler.postDelayed(this, 10)
        }
    }

    /* ---------------- 툴바 객체 ---------------- */
    private lateinit var toolbinding : ActivityPdfToolbarBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //바인딩 객체 획득
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        toolbinding = ActivityPdfToolbarBinding.inflate(layoutInflater)

        setContentView(binding.root)

        //!!신규 2개!!
        pdfView = binding.pdfView
        drawingView = binding.drawingView

        /* --- myDoc 로드 --- */
        myDocPath = intent.getStringExtra("myDocPath") ?: return
        val myDoc = MyDocManager(this).loadMyDoc(File(myDocPath))
        totalPages = getTotalPages(File(myDoc.pdfFilePath))


        // 저장된 stroke들을 페이지별로 분리 (stroke의 page 값이 있다면 사용)
        myDoc.strokes.groupBy { it.page }.forEach { (p, s) -> pageStrokes[p] = s.toMutableList() }
        if (pageStrokes.isEmpty()) pageStrokes[0] = mutableListOf()
        textAnnos.addAll(myDoc.annotations)

        loadPage(0)                    // 첫 페이지

        // "다음 페이지" 버튼
        binding.nextPageButton.setOnClickListener {
            updateCurrentPageStrokes()
            if (currentPage < totalPages - 1) loadPage(currentPage + 1)
        }

        // "이전 페이지" 버튼
        binding.prevPageButton.setOnClickListener {
            updateCurrentPageStrokes()
            if (currentPage > 0) loadPage(currentPage - 1)
        }

        // Export 버튼은 기존 로직 그대로
        binding.exportButton.setOnClickListener {
            exportToPdf()
        }

        // 모드 전환 버튼
        binding.toggleModeButton.setOnClickListener {
            isPenMode = !isPenMode
            drawingView.setDrawingEnabled(isPenMode)
            binding.toggleModeButton.text = if (isPenMode) "필기" else "드래그"
        }
        

        // 툴바 설정
        setSupportActionBar(toolbinding.pdfToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // 타이틀 비설정

        //툴바 버튼 설정(OCR)
        val btnOCR = findViewById<ImageButton>(R.id.btnOcr)
        //OCR 버튼 기능
        btnOCR.setOnClickListener {
            showOcrDialog()
        }

        // 툴바 버튼 설정(뒤로가기)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        // 🔹 뒤로 가기 버튼 기능
        btnBack.setOnClickListener {
            persistAll(); super.onBackPressed()
            Toast.makeText(this, "✅ 저장 완료",Toast.LENGTH_SHORT).show()
        }

        // 툴바 버튼 설정(저장하기)
        //val btnSave = findViewById<ImageButton>(R.id.btnSave)
        // 🔹 저장 하기 버튼 기능
        /*btnSave.setOnClickListener {
            updateCurrentPageStrokes()
            val allStrokes = pageStrokes.flatMap { it.value }
            MyDocManager(this).saveMyDoc(
                fileName = File(myDocPath).name,
                pdfFilePath = getBasePdfPath(),
                strokes = allStrokes
            )
            Toast.makeText(this, "✅ 저장 완료",Toast.LENGTH_SHORT).show()
        }*/

        // 툴바 버튼 설정(필기삭제)
        val btnEraser = findViewById<ImageButton>(R.id.btnEraser)
        // 🔹 필기 삭제 버튼 기능
        btnEraser.setOnClickListener {
            println("🧽 현재 페이지 ($currentPage) 필기 삭제")

            pageStrokes[currentPage]?.clear(); drawingView.setStrokes(emptyList())

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


        handler.post(syncRunnable)
    }

    override fun onDestroy() {
        super.onDestroy(); handler.removeCallbacks(syncRunnable)
    }

    /* =============================================================== */
    /*  페이지 로드                                                    */
    /* =============================================================== */
    private fun loadPage(index: Int) {
        currentPage = index
        pdfView.fromFile(File(getBasePdfPath()))
            .enableSwipe(false).pages(index)
            .onLoad(object : OnLoadCompleteListener {
                override fun loadComplete(nbPages: Int) {
                    drawingView.setCurrentPage(currentPage)
                    drawingView.setStrokes(pageStrokes[currentPage] ?: mutableListOf())
                    drawingView.setTextAnnotations(textAnnos)
                }
            }).load()
    }

    /* =============================================================== */
    /*  OCR → uCrop                                                   */
    /* =============================================================== */
    private fun showOcrDialog() {
        AlertDialog.Builder(this)
            .setItems(ocrOptions) { _, w -> startCrop(if (w == 0) CROP_EXTRACT else CROP_TRANS) }
            .show()
    }

    private fun startCrop(reqCode: Int) {
        val scale = 1080f / pdfView.width
        val bmp = Bitmap.createBitmap(
            (pdfView.width * scale).toInt(),
            (pdfView.height * scale).toInt(),
            Bitmap.Config.RGB_565
        )
        Canvas(bmp).apply { scale(scale, scale); pdfView.draw(this); drawingView.draw(this) }

        val srcFile = File(cacheDir, "crop_src_${System.currentTimeMillis()}.jpg")
        FileOutputStream(srcFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle(); System.gc()

        val srcUri = FileProvider.getUriForFile(this, AUTHORITY, srcFile)
        val dstUri = Uri.fromFile(File(cacheDir, "crop_dst_${System.currentTimeMillis()}.jpg"))

        UCrop.of(srcUri, dstUri)
            .withOptions(UCrop.Options().apply {
                setCompressionFormat(Bitmap.CompressFormat.JPEG); setFreeStyleCropEnabled(true)
            })
            .withAspectRatio(0f, 0f)
            .start(this, reqCode)
    }

    override fun onActivityResult(reqCode: Int, resCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resCode, data)
        if (resCode != RESULT_OK || data == null) return
        val cropped = contentResolver.openInputStream(UCrop.getOutput(data) ?: return)
            ?.use { BitmapFactory.decodeStream(it) } ?: return
        when (reqCode) {
            CROP_EXTRACT -> runOcr(cropped)
            CROP_TRANS   -> runTranslate(cropped)
        }
    }

    /* =============================================================== */
    /*  OCR 수행                                                       */
    /* =============================================================== */
    private fun runOcr(bmp: Bitmap) {
        ReadImageText().processImage(bmp) { extracted ->
            runOnUiThread { addTextAnno(extracted) }
        }
    }

    /* ---------- 문자열 래핑 ---------- */
    private fun wrapText(src: String, maxChars: Int = 30): String {
        val words = src.split("\\s+".toRegex())
        val sb = StringBuilder()
        var lineLen = 0
        for (w in words) {
            if (lineLen + w.length + 1 > maxChars) {
                sb.append('\n'); lineLen = 0
            } else if (lineLen > 0) {
                sb.append(' '); lineLen++
            }
            sb.append(w); lineLen += w.length
        }
        return sb.toString()
    }

    private fun addTextAnno(raw: String) {
        val wrapped = wrapText(raw, 40)      // ← 40글자마다 줄바꿈
        val cx = pdfView.width / 2f
        val cy = pdfView.height / 2f
        val pdfX = (cx - pdfView.currentXOffset) / pdfView.zoom
        val pdfY = (cy - pdfView.currentYOffset) / pdfView.zoom

        textAnnos += TextAnnotation(currentPage, wrapped, pdfX, pdfY, 40f)
        drawingView.setTextAnnotations(textAnnos)
    }

    private fun runTranslate(bmp: Bitmap) { /* 추후 구현 */ }

    /* =============================================================== */
    /*  저장 / 로드                                                    */
    /* =============================================================== */
    private fun updateCurrentPageStrokes() {
        val strokes = drawingView.getStrokes().toMutableList()
        strokes.forEach { it.page = currentPage }
        pageStrokes[currentPage] = strokes
    }

    private fun persistAll() {
        updateCurrentPageStrokes()
        MyDocManager(this).saveMyDoc(
            File(myDocPath).name,
            getBasePdfPath(),
            pageStrokes.values.flatten(),
            textAnnos
        )
    }

    private fun getBasePdfPath(): String =
        MyDocManager(this).loadMyDoc(File(myDocPath)).pdfFilePath

    private fun getTotalPages(file: File): Int =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            .use { it.pageCount }

    /* =============================================================== */
    /*  Export                                                         */
    /* =============================================================== */
    private fun exportToPdf() {
        persistAll()
        PdfExporter.export(this, myDocPath, "Exported_${System.currentTimeMillis()}.pdf")
    }

    /* =============================================================== */
    /*  뒤로가기                                                       */
    /* =============================================================== */
    override fun onBackPressed() { persistAll(); super.onBackPressed() }


    /* =============================================================== */
    /*  녹음                                                           */
    /* =============================================================== */
    // ✅ WAV 녹음 시작 함수
    @SuppressLint("MissingPermission")
    private fun startRecording(btnRecord: ImageButton) {
        if (!checkPermissions()) {
            println("🚨 권한이 없어서 녹음을 시작할 수 없습니다!")
            requestPermissions()
            return
        }

        isRecording = true
        btnRecord.setImageResource(R.drawable.ic_recording) // 🔴 아이콘 변경

        val fileName = generateFileName().replace(".mp3", ".wav") // 🔁 파일 이름 확장자 변경
        //val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) // 🔹 앱 내부 저장소 사용
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) //🔹 다운로드 파일
        val audioFile = File(storageDir, fileName) // 🔹 파일 생성
        audioFilePath = audioFile.absolutePath

        println("📂 파일 저장 경로: $audioFilePath") // ✅ 파일 경로 출력

        try {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            val outputStream = FileOutputStream(audioFile)
            writeWavHeader(outputStream, sampleRate, 1, audioFormat)

            audioRecord?.startRecording()

            recordingThread = Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        outputStream.write(buffer, 0, read)
                    }
                }
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                updateWavHeader(audioFile)
                outputStream.close()
                println("✅ WAV 파일 저장 완료: $audioFilePath")
            }

            recordingThread?.start()
            println("🎤 WAV 녹음 시작됨!")

        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 녹음 중 오류 발생: ${e.message}")
        }
    }


    // ✅ 녹음 중지 함수
    private fun stopRecording(btnRecord: ImageButton) {
        println("🛑 녹음 중지 요청됨")

        try {
            isRecording = false
            recordingThread?.join()
            btnRecord.setImageResource(R.drawable.ic_record) // 🎤 아이콘 변경
            println("✅ 녹음 완료! 파일 저장 위치: $audioFilePath")
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 녹음 중지 중 오류 발생: ${e.message}")
        }
    }

    private fun writeWavHeader(out: OutputStream, sampleRate: Int, channels: Int, encoding: Int) {
        val bitsPerSample = if (encoding == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)

        // ChunkID "RIFF"
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // ChunkSize (임시 0)
        // Format "WAVE"
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // Subchunk1ID "fmt "
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1Size = 16 for PCM
        header[16] = 16
        header[20] = 1 // PCM
        header[22] = channels.toByte()
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[34] = bitsPerSample.toByte()

        // Subchunk2ID "data" + Subchunk2Size (임시 0)
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        out.write(header, 0, 44)
    }

    private fun updateWavHeader(wavFile: File) {
        val sizes = wavFile.length() - 44
        val header = RandomAccessFile(wavFile, "rw")

        header.seek(4)
        header.write(intToByteArray((sizes + 36).toInt()))
        header.seek(40)
        header.write(intToByteArray(sizes.toInt()))
        header.close()
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }




    // ✅ 파일 이름 생성 함수 (yyyyMMdd_HHmm.mp3 형식)
    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "record_$timeStamp.wav"
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