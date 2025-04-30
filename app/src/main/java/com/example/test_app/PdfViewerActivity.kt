package com.example.test_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
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
import com.example.test_app.databinding.ActivityPdfViewerBinding
import com.example.test_app.model.Stroke
import com.example.test_app.model.TextAnnotation
import com.example.test_app.utils.MyDocManager
import com.example.test_app.utils.PdfExporter
import com.example.test_app.view.DrawingView
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

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
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath = ""

    /* ---------------- Sync ---------------- */
    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            drawingView.setPdfViewInfo(pdfView.zoom, pdfView.currentXOffset, pdfView.currentYOffset)
            handler.postDelayed(this, 10)
        }
    }

    /* =============================================================== */
    /*  onCreate                                                       */
    /* =============================================================== */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pdfView     = binding.pdfView
        drawingView = binding.drawingView

        /* --- myDoc 로드 --- */
        myDocPath = intent.getStringExtra("myDocPath") ?: return
        val myDoc = MyDocManager(this).loadMyDoc(File(myDocPath))
        totalPages = getTotalPages(File(myDoc.pdfFilePath))

        myDoc.strokes.groupBy { it.page }.forEach { (p, s) -> pageStrokes[p] = s.toMutableList() }
        if (pageStrokes.isEmpty()) pageStrokes[0] = mutableListOf()
        textAnnos.addAll(myDoc.annotations)

        loadPage(0)                    // 첫 페이지

        /* --- 툴바 버튼 --- */
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { persistAll(); super.onBackPressed() }
        findViewById<ImageButton>(R.id.btnEraser).setOnClickListener {
            pageStrokes[currentPage]?.clear(); drawingView.setStrokes(emptyList())
        }
        findViewById<ImageButton>(R.id.btnRecord).setOnClickListener {
            if (isRecording) stopRecording(it as ImageButton) else startRecording(it as ImageButton)
        }
        findViewById<ImageButton>(R.id.btnOcr).setOnClickListener { showOcrDialog() }

        binding.nextPageButton.setOnClickListener {
            updateCurrentPageStrokes(); if (currentPage < totalPages - 1) loadPage(currentPage + 1)
        }
        binding.prevPageButton.setOnClickListener {
            updateCurrentPageStrokes(); if (currentPage > 0) loadPage(currentPage - 1)
        }
        binding.toggleModeButton.setOnClickListener {
            isPenMode = !isPenMode
            drawingView.setDrawingEnabled(isPenMode)
            binding.toggleModeButton.text = if (isPenMode) "필기" else "드래그"
        }
        binding.exportButton.setOnClickListener { exportToPdf() }

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
    /*  녹음                                                           */
    /* =============================================================== */
    private fun startRecording(btn: ImageButton) {
        if (!checkPermissions()) { requestPermissions(); return }
        isRecording = true; btn.setImageResource(R.drawable.ic_recording)

        val fileName = "record_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.mp3"
        val audioFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
        audioFilePath = audioFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFilePath)
            prepare(); start()
        }
    }

    private fun stopRecording(btn: ImageButton) {
        isRecording = false; btn.setImageResource(R.drawable.ic_record)
        mediaRecorder?.apply { stop(); release() }; mediaRecorder = null
    }

    private fun checkPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)

    /* =============================================================== */
    /*  뒤로가기                                                       */
    /* =============================================================== */
    override fun onBackPressed() { persistAll(); super.onBackPressed() }
}
