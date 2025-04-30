package com.example.test_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.test_app.ReadImageText
import com.example.test_app.databinding.ActivityPdfToolbarBinding
import com.example.test_app.databinding.ActivityPdfViewerBinding
import com.example.test_app.model.Stroke
import com.example.test_app.model.TextAnnotation
import com.example.test_app.utils.MyDocManager
import com.example.test_app.utils.PdfExporter
import com.example.test_app.view.DrawingView
import com.github.barteksc.pdfviewer.BuildConfig
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class PdfViewerActivity : AppCompatActivity() {

    /* ---------------- UI 바인딩 ---------------- */
    private lateinit var binding      : ActivityPdfViewerBinding
    private lateinit var toolBinding  : ActivityPdfToolbarBinding
    private lateinit var pdfView      : PDFView
    private lateinit var drawingView  : DrawingView

    /* ---------------- PDF·필기 ---------------- */
    private val pageStrokes   = mutableMapOf<Int, MutableList<Stroke>>()   // 페이지별 필기
    private var currentPage   = 0
    private var totalPages    = 0
    private lateinit var myDocPath: String
    private val textAnnos     = mutableListOf<TextAnnotation>()            // 텍스트 어노테이션

    /* ---------------- 모드 ---------------- */
    private var isPenMode = true

    /* ---------------- OCR / 번역 ---------------- */
    private val ocrOptions = arrayOf("텍스트 추출", "번역")
    private val AUTHORITY by lazy { "${packageName}.fileprovider" }
    private val CROP_EXTRACT   = 1001
    private val CROP_TRANSLATE = 1002

    /* ---------------- 녹음 ---------------- */
    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath = ""

    /* ---------------- PDFView ↔ DrawingView 동기화 ---------------- */
    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            drawingView.setPdfViewInfo(
                pdfView.zoom,
                pdfView.currentXOffset,
                pdfView.currentYOffset
            )
            handler.postDelayed(this, 10)
        }
    }

    /* ------------------------------------------------------------------ */
    /*  onCreate                                                          */
    /* ------------------------------------------------------------------ */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding     = ActivityPdfViewerBinding.inflate(layoutInflater)
        toolBinding = ActivityPdfToolbarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pdfView     = binding.pdfView
        drawingView = binding.drawingView

        /* ----- myDoc 로드 ----- */
        myDocPath = intent.getStringExtra("myDocPath") ?: return
        val myDoc  = MyDocManager(this).loadMyDoc(File(myDocPath))
        totalPages = getTotalPages(File(myDoc.pdfFilePath))

        /* 저장돼 있던 필기 복원 */
        myDoc.strokes.groupBy { it.page }.forEach { (p, s) ->
            pageStrokes[p] = s.toMutableList()
        }
        if (pageStrokes.isEmpty()) pageStrokes[0] = mutableListOf()

        /* ----- 첫 페이지 표시 ----- */
        loadPage(0)

        /* ------------------------------------------------------------------
         *  ▼▼▼ 툴바 버튼 리스너 ▼▼▼
         * ------------------------------------------------------------------ */
        val btnBack   = findViewById<ImageButton>(R.id.btnBack)
        val btnSave   = findViewById<ImageButton>(R.id.btnSave)
        val btnEraser = findViewById<ImageButton>(R.id.btnEraser)
        val btnRecord = findViewById<ImageButton>(R.id.btnRecord)
        val btnOcr    = findViewById<ImageButton>(R.id.btnOcr)

        /* 뒤로가기 */
        btnBack.setOnClickListener {
            persistAllStrokes()
            super.onBackPressed()
        }

        /* 저장 */
        btnSave.setOnClickListener {
            persistAllStrokes()
            Toast.makeText(this, "✅ 저장 완료", Toast.LENGTH_SHORT).show()
        }

        /* 필기 삭제 */
        btnEraser.setOnClickListener {
            pageStrokes[currentPage]?.clear()
            drawingView.setStrokes(emptyList())
            Toast.makeText(this, "현재 페이지 필기를 삭제했습니다.", Toast.LENGTH_SHORT).show()
        }

        /* 녹음 */
        btnRecord.setOnClickListener {
            if (isRecording) stopRecording(btnRecord) else startRecording(btnRecord)
        }

        /* OCR/번역 팝업 */
        btnOcr.setOnClickListener { showOcrDialog() }

        /* 페이지 네비게이션 */
        binding.nextPageButton.setOnClickListener {
            updateCurrentPageStrokes()
            if (currentPage < totalPages - 1) loadPage(currentPage + 1)
        }
        binding.prevPageButton.setOnClickListener {
            updateCurrentPageStrokes()
            if (currentPage > 0) loadPage(currentPage - 1)
        }

        /* 모드 전환 */
        binding.toggleModeButton.setOnClickListener {
            isPenMode = !isPenMode
            drawingView.setDrawingEnabled(isPenMode)
            binding.toggleModeButton.text = if (isPenMode) "필기" else "드래그"
        }

        /* Export */
        binding.exportButton.setOnClickListener { exportToPdf() }

        /* PDFView ↔ DrawingView 동기화 시작 */
        handler.post(syncRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(syncRunnable)
    }

    /* ------------------------------------------------------------------ */
    /*  페이지 로드                                                        */
    /* ------------------------------------------------------------------ */
    private fun loadPage(index: Int) {
        currentPage = index
        pdfView.fromFile(File(getBasePdfPath()))
            .enableSwipe(false)
            .pages(index)
            .onLoad(object : OnLoadCompleteListener {
                override fun loadComplete(nbPages: Int) {
                    /* 필기·어노테이션 적용 */
                    drawingView.setCurrentPage(currentPage)
                    drawingView.setStrokes(pageStrokes[currentPage] ?: mutableListOf())
                    drawingView.setTextAnnotations(textAnnos)
                }
            })
            .load()
    }

    /* ------------------------------------------------------------------ */
    /*  OCR 팝업 → uCrop 호출                                             */
    /* ------------------------------------------------------------------ */
    private fun showOcrDialog() {
        AlertDialog.Builder(this)
            .setItems(ocrOptions) { _, which ->
                val req = if (which == 0) CROP_EXTRACT else CROP_TRANSLATE
                startCrop(req)
            }
            .show()
    }

    private fun startCrop(requestCode: Int) {
        /* ① PDF + 필기 화면 캡처 */
        val bmp = Bitmap.createBitmap(pdfView.width, pdfView.height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            pdfView.draw(this)
            drawingView.draw(this)
        }

        /* ② 소스 이미지 파일 */
        val srcFile = File(cacheDir, "crop_src_${System.currentTimeMillis()}.jpg")
        FileOutputStream(srcFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        val srcUri = FileProvider.getUriForFile(this, AUTHORITY, srcFile)

        /* ③ 출력 파일 URI */
        val dstFile = File(cacheDir, "crop_dst_${System.currentTimeMillis()}.jpg")
        val dstUri  = Uri.fromFile(dstFile)

        /* ④ uCrop 실행 */
        val opt = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setFreeStyleCropEnabled(true)
        }
        UCrop.of(srcUri, dstUri)
            .withOptions(opt)
            .withAspectRatio(0f, 0f)
            .start(this, requestCode)
    }

    override fun onActivityResult(reqCode: Int, resCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resCode, data)
        if (resCode != RESULT_OK || data == null) return

        val uri = UCrop.getOutput(data) ?: return
        val cropped = contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it) } ?: return

        when (reqCode) {
            CROP_EXTRACT   -> runOcr(cropped)
            CROP_TRANSLATE -> runTranslate(cropped) // 추후 구현
        }
    }

    /* ------------------------------------------------------------------ */
    /*  OCR 수행 & 텍스트 어노테이션 추가                                  */
    /* ------------------------------------------------------------------ */
    private fun runOcr(bmp: Bitmap) {
        // context가 필요 없는 클래스이므로 생성자에 this 전달 X
        ReadImageText()
            .processImage(bmp) { extracted ->
                runOnUiThread { addTextAnno(extracted) }
            }
    }

    private fun addTextAnno(text: String) {
        val cx = pdfView.width  / 2f
        val cy = pdfView.height / 2f
        val pdfX = (cx - pdfView.currentXOffset) / pdfView.zoom
        val pdfY = (cy - pdfView.currentYOffset) / pdfView.zoom

        textAnnos += TextAnnotation(currentPage, text, pdfX, pdfY)
        drawingView.setTextAnnotations(textAnnos)
    }

    private fun runTranslate(bmp: Bitmap) {
        // TODO: 번역 처리 후 addTextAnno 호출
    }

    /* ------------------------------------------------------------------ */
    /*  필기·파일 저장/로드                                                */
    /* ------------------------------------------------------------------ */
    private fun updateCurrentPageStrokes() {
        val strokes = drawingView.getStrokes().toMutableList()
        strokes.forEach { it.page = currentPage }
        pageStrokes[currentPage] = strokes
    }

    private fun persistAllStrokes() {
        updateCurrentPageStrokes()
        val all = pageStrokes.values.flatten()
        MyDocManager(this).saveMyDoc(
            File(myDocPath).name,
            getBasePdfPath(),
            all
        )
    }

    private fun getBasePdfPath(): String =
        MyDocManager(this).loadMyDoc(File(myDocPath)).pdfFilePath

    private fun getTotalPages(file: File): Int =
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            .use { it.pageCount }

    /* ------------------------------------------------------------------ */
    /*  Export                                                            */
    /* ------------------------------------------------------------------ */
    private fun exportToPdf() {
        persistAllStrokes()
        PdfExporter.export(
            this,
            myDocPath,
            "Exported_${System.currentTimeMillis()}.pdf"
        )
    }

    /* ------------------------------------------------------------------ */
    /*  녹음                                                               */
    /* ------------------------------------------------------------------ */
    private fun startRecording(btn: ImageButton) {
        if (!checkPermissions()) { requestPermissions(); return }
        isRecording = true
        btn.setImageResource(R.drawable.ic_recording)

        val fileName   = "record_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.mp3"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val audioFile  = File(storageDir, fileName)
        audioFilePath  = audioFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFilePath)
            prepare()
            start()
        }
    }

    private fun stopRecording(btn: ImageButton) {
        isRecording = false
        btn.setImageResource(R.drawable.ic_record)
        mediaRecorder?.apply { stop(); release() }
        mediaRecorder = null
    }

    private fun checkPermissions(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)

    /* ------------------------------------------------------------------ */
    /*  뒤로가기                                                           */
    /* ------------------------------------------------------------------ */
    override fun onBackPressed() {
        persistAllStrokes()
        super.onBackPressed()
    }
}
