package com.example.test_app

import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.test_app.databinding.ActivityPdfViewerBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.github.barteksc.pdfviewer.PDFView
import android.graphics.pdf.PdfRenderer
import android.media.AudioFormat
import android.media.AudioRecord
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.animation.Animation
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.example.test_app.view.DrawingView
import com.example.test_app.model.Stroke
import com.example.test_app.model.TextAnnotation
import com.example.test_app.utils.MyDocManager
import com.example.test_app.utils.PdfExporter
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.SeekBar
import com.yalantis.ucrop.UCrop
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import androidx.core.graphics.createBitmap
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.graphics.toColorInt

@Suppress("DEPRECATION")
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
    private var isEraserMode = false

    /* ---------------- OCR ---------------- */
    private val ocrOptions   = arrayOf("텍스트 요약", "번역")
    private var currentCropMode = 0
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

    /* ---------------- side menu ----------------*/
    private lateinit var sideMenu: LinearLayout
    private lateinit var btnMenu: ImageButton
    private lateinit var btnRecord: ImageButton
    private lateinit var btnOcr: ImageButton
    private lateinit var exportButton: ImageButton

    /* ---------------- 애니메이션 ----------------*/
    private lateinit var slideDown: Animation
    private lateinit var slideUp: Animation

    /* ---------------- 펜 옵션 ------------*/
    private lateinit var penOptionLayout: LinearLayout
    private lateinit var penSizeCircle: View
    private lateinit var penSizeSeekBar: SeekBar
    private lateinit var btnPen: ImageButton
    private lateinit var colorBlack: View
    private lateinit var colorBlue: View
    private lateinit var colorGreen: View
    private lateinit var colorRed: View
    private lateinit var colorYellow: View

    /* ---------------- 지우개 옵션 ------------*/
    private lateinit var btnEraser: ImageButton
    private lateinit var eraserSizeCircle  : View

    private var isMenuOpen = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //바인딩 객체 획득
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // PDF 뷰어, 필기 뷰 초기화
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

        // 모드 전환 버튼
        binding.toggleModeButton.setOnClickListener {
            isPenMode = !isPenMode
            drawingView.setDrawingEnabled(isPenMode)
            // pen 모드일 때(연하게), drag 모드일 때(진하게)
            binding.toggleModeButton.alpha = if (isPenMode) 0.4f else 1.0f
        }

        // Export 버튼은 기존 로직 그대로
        exportButton = findViewById<ImageButton>(R.id.exportButton)
        exportButton.setOnClickListener {
            exportToPdf()
        }

        //OCR 기능
        btnOcr = findViewById(R.id.btnOcr)
        //OCR 버튼 기능
        btnOcr.setOnClickListener {
            showOcrDialog()
        }

        // 뒤로 가기 버튼
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // 뒤로 가기 버튼 기능
        btnBack.setOnClickListener {
            persistAll()
            onBackPressedDispatcher.onBackPressed()
            Toast.makeText(this, "저장 완료",Toast.LENGTH_SHORT).show()
        }

        // 녹음 버튼
        btnRecord = findViewById(R.id.btnRecord)

        btnRecord.setOnClickListener {
            println("녹음 버튼이 클릭됨!")
            if (isRecording) {
                stopRecording(btnRecord)
            } else {
                startRecording(btnRecord)
            }
        }

        // 사이드 메뉴
        sideMenu = findViewById(R.id.sideMenu)

        slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        slideDown = AnimationUtils.loadAnimation(this, R.anim.slide_down)

        // 햄버거
        btnMenu = findViewById(R.id.btnMenu)

        btnMenu.setOnClickListener { toggleSideMenu() }

        // 펜, 지우개
        btnPen = findViewById(R.id.btnPen)
        btnEraser = findViewById(R.id.btnEraser)
        penOptionLayout = findViewById(R.id.penOptionLayout)
        penSizeCircle = findViewById(R.id.penSizeCircle)
        penSizeSeekBar = findViewById(R.id.penSizeSeekBar)

        colorBlack  = findViewById(R.id.colorBlack)
        colorBlue   = findViewById(R.id.colorBlue)
        colorGreen  = findViewById(R.id.colorGreen)
        colorRed    = findViewById(R.id.colorRed)
        colorYellow = findViewById(R.id.colorYellow)

        updateToolSize(penSizeSeekBar.progress)

        btnPen.setOnClickListener {
            if(isEraserMode){
                isEraserMode = false
                drawingView.setEraserEnabled(false)
                drawingView.setDrawingEnabled(true)

                btnPen.alpha = 1.0f
                btnEraser.alpha = 0.4f

                penOptionLayout.visibility = View.GONE
            }else{
                penOptionLayout.visibility =
                    if(penOptionLayout.isVisible) View.GONE
                    else View.VISIBLE
            }
        }

        btnEraser.setOnClickListener{
            if (!isEraserMode) {
                // 지우개 모드 진입
                isEraserMode = true
                drawingView.setEraserEnabled(true)
                drawingView.setDrawingEnabled(false)

                // 버튼 시각 표시
                btnEraser.alpha = 1.0f
                btnPen   .alpha = 0.4f

                // 펜 옵션창 숨기기
                penOptionLayout.visibility = View.GONE
            }
        }

        penSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, prog: Int, fromUser: Boolean) {
                if (!isEraserMode) updateToolSize(prog)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar)  {}
        })

        colorBlack.setOnClickListener{ applyPenColor(Color.BLACK) }
        colorBlue.setOnClickListener { applyPenColor("#025AB1".toColorInt()) }
        colorGreen.setOnClickListener { applyPenColor("#2E7D32".toColorInt()) }
        colorRed.setOnClickListener { applyPenColor("#C62828".toColorInt()) }
        colorYellow.setOnClickListener { applyPenColor("#F9A825".toColorInt()) }

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

    // OCR 결과를 서버로 전송하여 요약 요청 (Termux Flask 서버로 POST)
    private fun sendTextForSummarization(extractedText: String, fileName: String){

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val request = SummarizeRequest(extractedText)

        val call = RetrofitClient.fileUploadService.summarizeText("Bearer $accessToken", request)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string()

                    try {

                        val json = JSONObject(responseBody ?: "")

                        val taskId = json.optString("task_id", "")

                        // task_id를 SummarizeActivity와 같은 SharedPreferences에 저장
                        saveSummaryTaskId(taskId, fileName)


                        Log.d("OCR_SUMMARIZE", "서버 요약 요청 완료 (Task ID: $taskId)")

                        Toast.makeText(this@PdfViewerActivity, "요약 요청이 전송되었습니다.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@PdfViewerActivity, "응답 파싱 오류", Toast.LENGTH_SHORT).show()
                        Log.e("OCR_SUMMARIZE", "응답 파싱 오류: ${e.message}")
                    }
                }

                else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("OCR_SUMMARIZE", "요약 요청 실패: ${response.code()} - $errorBody")
                    Toast.makeText(this@PdfViewerActivity, "요약 요청 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@PdfViewerActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 크롭 작업 시작 (UCrop 라이브러리 사용)
    private fun startCrop(reqCode: Int) {
        val scale = 1080f / pdfView.width
        val bmp = createBitmap(
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

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    // 이미지 크롭 결과 처리 (OCR 수행)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val cropped = contentResolver.openInputStream(UCrop.getOutput(data) ?: return)
            ?.use { BitmapFactory.decodeStream(it) } ?: return
        when (requestCode) {
            CROP_EXTRACT -> runOcr(cropped)
            CROP_TRANS   -> runTranslate(cropped)
        }
    }

    /* =============================================================== */
    /*  OCR 수행                                                       */
    /* =============================================================== */
    private fun runOcr(bmp: Bitmap) {
        ReadImageText().processImage(bmp) { extracted ->
            runOnUiThread {
                if (currentCropMode == CROP_EXTRACT) {
                    showTitleInputDialog(extracted)
                } else {
                    addTextAnno(extracted)
                }
            }
        }
    }

    private fun showTitleInputDialog(extractedText: String) {
        val editText = EditText(this)
        editText.hint = "요약 제목을 입력하세요"

        AlertDialog.Builder(this)
            .setTitle("요약 제목 입력")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    sendTextForSummarization(extractedText, fileName)
                } else {
                    Toast.makeText(this, "제목을 입력해야 합니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }


    private fun saveSummaryTaskId(taskId: String, fileName: String) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val existingJson = sharedPreferences.getString("summary_task_id_list", "[]")
        val type = object : TypeToken<MutableList<SummaryTask>>() {}.type
        val taskList: MutableList<SummaryTask> = Gson().fromJson(existingJson, type)

        taskList.add(SummaryTask(taskId, fileName))

        val newJson = Gson().toJson(taskList)
        sharedPreferences.edit { putString("summary_task_id_list", newJson) }
    }


    /* ---------- 문자열 래핑 ---------- */
    private fun wrapText(src: String, maxChars: Int = 10): String {
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
        val wrapped = wrapText(raw)      // ← 40글자마다 줄바꿈
        val cx = pdfView.width / 2f
        val cy = pdfView.height / 2f
        val pdfX = (cx - pdfView.currentXOffset) / pdfView.zoom
        val pdfY = (cy - pdfView.currentYOffset) / pdfView.zoom

        textAnnos += TextAnnotation(currentPage, wrapped, pdfX, pdfY, 40f)
        drawingView.setTextAnnotations(textAnnos)
    }

    private fun runTranslate(bmp: Bitmap) {
        ReadImageText().processImage(bmp) { extractedText ->
            val intent = Intent(this, TranslateActivity::class.java)
            intent.putExtra("ocrText", extractedText)
            startActivity(intent)
        }
    }

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
    @Deprecated("Deprecated due to AndroidX API changes")
    override fun onBackPressed() { persistAll(); super.onBackPressed() }

    /* =============================================================== */
    /*  펜, 지우개 관련                                                  */
    /* =============================================================== */
    private fun updateToolSize(sizeDp: Int){
        val dp = sizeDp.coerceAtLeast(1)
        val px = dpToPx(dp)
        if(isEraserMode){
            resizeCircle(eraserSizeCircle, px)
        }else{
            drawingView.setCurrentStrokeWidth(dp.toFloat())
            resizeCircle(penSizeCircle, px)
        }
    }
    /** dp → px 변환 */
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
    private fun applyPenColor(color: Int){
        drawingView.setCurrentStrokeColor(color)
        penSizeCircle.background.setTint(color)
        btnPen.setColorFilter(color)
    }
    /** 뷰 크기(px 단위) 변경 */
    private fun resizeCircle(view: View, sizePx: Int) {
        view.layoutParams = view.layoutParams.apply {
            width = sizePx
            height = sizePx
        }
        view.requestLayout()
    }
    /* =============================================================== */
    /*  애니메이션                                                      */
    /* =============================================================== */
    private fun toggleSideMenu(){
        if(!isMenuOpen){
            sideMenu.startAnimation(slideDown)
        }else{
            slideUp.setAnimationListener(object : Animation.AnimationListener{
                override fun onAnimationStart(a: Animation) {}
                override fun onAnimationRepeat(a: Animation) {}
                override fun onAnimationEnd(a: Animation){
                    sideMenu.visibility = View.VISIBLE
                }
            })
            sideMenu.startAnimation(slideUp)
        }
        isMenuOpen = !isMenuOpen

        // 버튼들 visibility 토글
        val v = if(isMenuOpen) View.VISIBLE else View.GONE
        btnRecord.visibility = v
        btnOcr.visibility = v
        exportButton.visibility = v

        sideMenu.bringToFront()
    }
    /* =============================================================== */
    /*  녹음                                                           */
    /* =============================================================== */
    // WAV 녹음 시작 함수
    @SuppressLint("MissingPermission")
    private fun startRecording(btnRecord: ImageButton) {
        if (!checkPermissions()) {
            println("권한이 없어서 녹음을 시작할 수 없습니다!")
            requestPermissions()
            return
        }

        isRecording = true
        btnRecord.setImageResource(R.drawable.ic_recording) // 아이콘 변경

        val fileName = generateFileName().replace(".mp3", ".wav") // 파일 이름 확장자 변경
        //val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) // 앱 내부 저장소 사용
        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) //다운로드 파일
        val audioFile = File(storageDir, fileName) // 파일 생성
        audioFilePath = audioFile.absolutePath

        println("파일 저장 경로: $audioFilePath") // 파일 경로 출력

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
                println("WAV 파일 저장 완료: $audioFilePath")
            }

            recordingThread?.start()
            println("WAV 녹음 시작됨!")

        } catch (e: Exception) {
            e.printStackTrace()
            println("녹음 중 오류 발생: ${e.message}")
        }
    }


    // 녹음 중지 함수
    private fun stopRecording(btnRecord: ImageButton) {
        println("녹음 중지 요청됨")

        try {
            isRecording = false
            recordingThread?.join()
            btnRecord.setImageResource(R.drawable.ic_record) // 🎤 아이콘 변경
            println("녹음 완료! 파일 저장 위치: $audioFilePath")
        } catch (e: Exception) {
            e.printStackTrace()
            println("녹음 중지 중 오류 발생: ${e.message}")
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




    // 파일 이름 생성 함수 (yyyyMMdd_HHmm.mp3 형식)
    private fun generateFileName(): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "record_$timeStamp.wav"
    }

    // 녹음 권한 확인 함수
    private fun checkPermissions(): Boolean {
        return try {
            val recordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)

            println("🔍 권한 확인 - RECORD_AUDIO: $recordPermission")

            recordPermission == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            e.printStackTrace()
            println("권한 확인 중 오류 발생: ${e.message}")
            false // 예외 발생 시 false 반환 (앱 크래시 방지)
        }
    }



    // 녹음 권한 요청 함수
    private fun requestPermissions() {
        try {
            println("권한 요청 실행")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO), //파일 저장 권한 제거
                200
            )
        } catch (e: Exception) {
            e.printStackTrace()
            println("권한 요청 중 오류 발생: ${e.message}")
        }
    }


    //권한 승인 여부 확인
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 200) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                println("오디오 녹음 권한이 승인되었습니다!")
            } else {
                println("오디오 녹음 권한이 거부되었습니다.")
            }
        }
    }
}