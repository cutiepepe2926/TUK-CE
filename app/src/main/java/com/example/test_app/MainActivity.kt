package com.example.test_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.test_app.databinding.ActivityMainBinding
import com.example.test_app.databinding.ActivityMainToolbarBinding
import com.shockwave.pdfium.PdfiumCore
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.FileNotFoundException
import java.io.FileOutputStream



class MainActivity : AppCompatActivity() {

    //바인딩 초기 선언
    private lateinit var binding: ActivityMainBinding
    private lateinit var toolbarBinding: ActivityMainToolbarBinding

    //PDF 추가 시 URI를 SharedPreferences에 저장하는 코드
    private val sharedPref: SharedPreferences by lazy {
        getSharedPreferences("pdf_storage", MODE_PRIVATE)
    }

    // AddActivity로부터 Uri 결과를 받기 위한 Launcher
    private val addActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val uri = result.data?.data
            
            //잘 받아왔는지 체크
            println("MainActivity Uri is  $uri")
            
            if (uri != null) {
                val bitmap = renderPdfToBitmap(uri) //PDF를 Bitmap으로 변환
                val fileName = getFileName(uri) // 파일 이름 가져오기
                if (bitmap != null) {
                    addPdfImage(bitmap, uri, fileName)
                    // 파일 이름과 함께 추가
                    savePdfUri(uri, fileName) // ✅ SharedPreferences에 저장
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        //바인딩 초기화 및 바인딩 객체 획득
        binding = ActivityMainBinding.inflate(layoutInflater)
        toolbarBinding = ActivityMainToolbarBinding.inflate(layoutInflater)

        //로그인 상태 유지 (토큰 확인) (서버 닫힌경우에는 주석처리하기)
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken == null) {
            // 로그인 정보가 없으면 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            // 로그인 정보가 있으면 메인 화면 표시
            setContentView(binding.root)
        }

        //추가 버튼 클릭 시 이벤트 발생
        binding.addBtn.setOnClickListener {
            // AddActivity로 이동하는 Intent 생성
            val intent = Intent(this, AddActivity::class.java)
            addActivityResultLauncher.launch(intent) // AddActivity 시작
        }

        // 최신화 버튼 클릭 시 PDF 다시 불러오기
        binding.refreshBtn.setOnClickListener {
            println("🔄 최신화 버튼 클릭됨! PDF 목록 새로 불러오기")

            // ✅ 기존 썸네일 초기화 (해결 방법)
            binding.pdfContainer.removeAllViews()

            loadSavedPdfs() // 최신 PDF 불러오기
        }

        //액티비티 화면 출력
        setContentView(binding.root)

        // 저장된 PDF URI 불러오기
        loadSavedPdfs()


        // 툴바 설정
        setSupportActionBar(toolbarBinding.mainToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // 타이틀 비설정

        // 툴바 버튼 설정(저장하기)
        val userBtn = findViewById<ImageButton>(R.id.btnUser)
        // 🔹 로그인 하기 버튼 기능
        userBtn.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // 파일 선택 버튼 (음성 파일 업로드)
        val btnSendRecord = findViewById<ImageButton>(R.id.btnSendRecord)
        btnSendRecord.setOnClickListener {
            openFilePicker()
        }

        // OCR 페이지 이동 버튼 (사진으로 OCR)
        val btnOcr = findViewById<ImageButton>(R.id.btnOcr)
        btnOcr.setOnClickListener {
            val intent = Intent(this, OcrActivity::class.java)
            startActivity(intent)
        }

    }

    // 🔹 파일 탐색기 열기 (MP3 파일 선택)
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*" // 🔹 모든 오디오 파일 형식 지원
        }
        filePickerLauncher.launch(intent)
    }

    // 🔹 파일 선택 결과 처리
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val selectedFileUri = result.data!!.data
                if (selectedFileUri != null) {
                    println("✅ 선택된 파일 URI: $selectedFileUri")
                    uploadFile(selectedFileUri) // 🔹 선택한 파일을 서버로 업로드
                }
            } else {
                Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    // 🔹 파일 업로드 함수
    private fun uploadFile(fileUri: Uri) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken == null) {
            println("🚨 로그인 정보 없음: 토큰이 없습니다.")
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔹 Uri → 실제 파일 변환 (임시 파일 생성)
        val file = uriToFile(fileUri) ?: run {
            println("🚨 파일 변환 실패")
            return
        }

        val requestBody = RequestBody.create("audio/*".toMediaTypeOrNull(), file)
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)

        val call = RetrofitClient.fileUploadService.uploadFile("Bearer $accessToken", filePart) // ✅ 수정된 코드

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string() ?: "서버 응답 없음"
                    println("✅ 파일 업로드 성공! 서버 응답: $responseBody")
                    Toast.makeText(this@MainActivity, responseBody, Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류 발생"
                    println("🚨 파일 업로드 실패: $errorMessage")
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }

            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("🚨 네트워크 오류: ${t.message}")
                Toast.makeText(this@MainActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 🔹 Uri → File 변환 함수 (파일을 임시로 복사하여 저장)
    private fun uriToFile(uri: Uri): File? {
        val tempFile = File(cacheDir, "temp_audio.mp3")
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    //PDF 추가 시 URI를 SharedPreferences에 저장하는 코드
    private fun savePdfUri(uri: Uri, fileName: String) {
        val pdfList = getSavedPdfList()  // 기존에 저장된 PDF 리스트 가져오기
        pdfList.put(uri.toString(), fileName)  // 새로운 PDF 추가

        val editor = sharedPref.edit()  // SharedPreferences 편집 모드
        editor.putString("pdf_list", pdfList.toString())  // JSON 문자열로 저장
        editor.apply()  // 변경 사항 적용
    }


    //// 저장된 PDF URI 불러오는 함수
    private fun loadSavedPdfs() {

        binding.pdfContainer.removeAllViews()
        println("\uD83D\uDEA8 기존에 저장된 PDF 목록 제거")
        // 📌 기존에 저장된 PDF 목록 제거

        val pdfList = getSavedPdfList()  // 📌 저장된 PDF 목록 불러오기

        for (i in 0 until pdfList.length()) {  // 📌 저장된 PDF 개수만큼 반복
            val uriString = pdfList.names()?.getString(i) ?: continue  // 📌 PDF URI 가져오기
            val fileName = pdfList.getString(uriString) ?: continue  // 📌 파일 이름 가져오기
            val uri = Uri.parse(uriString)  // 📌 String → Uri 변환

            // ✅ 디버깅 로그 추가
            println("🔍 불러온 PDF URI: $uri (파일명: $fileName)")

            val bitmap = renderPdfToBitmap(uri)
            if (bitmap != null) {
                addPdfImage(bitmap, uri, fileName) // 📌 PDF의 썸네일 이미지 생성
            } else {
                println("🚨 PDF 불러오기 실패: $uri")
            }
        }
    }


    //저장할 PDF 리스트 함수
    private fun getSavedPdfList(): JSONObject {
        val jsonString = sharedPref.getString("pdf_list", "{}") ?: "{}"
        val pdfList = JSONObject(jsonString)

        val validPdfList = JSONObject()

        for (key in pdfList.keys()) {
            val file = File(Uri.parse(key).path ?: "")

            if (file.exists()) {
                validPdfList.put(key, pdfList.getString(key))
            } else {
                println("🚨 삭제된 PDF 제거: $key")
            }
        }

        // ✅ 최신 PDF 목록 저장
        sharedPref.edit().putString("pdf_list", validPdfList.toString()).apply()

        return validPdfList
    }
        // PDF 파일을 Bitmap으로 변환
    private fun renderPdfToBitmap(uri: Uri): Bitmap? {
        try {

            // ✅ 파일 존재 여부 확인 (디버깅 로그 추가)
            println("🔍 PDF 파일 확인: $uri")

            val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            //PDF 파일을 Uri를 통해 열기

            //파일이 존재하지 않을 시
            if (parcelFileDescriptor == null) {
                println("🚨 파일을 찾을 수 없음! PDF가 존재하지 않습니다.")
                return null
            }

            val pdfiumCore = PdfiumCore(this)
            val pdfDocument = pdfiumCore.newDocument(parcelFileDescriptor)

            pdfiumCore.openPage(pdfDocument, 0)
            // 첫 번째 페이지 열기

            // 🔥 해상도를 원본 크기 또는 2배로 설정
            //val scaleFactor = 2 // 원하는 배율로 조정 가능 (2배 해상도)
            val width = pdfiumCore.getPageWidthPoint(pdfDocument, 0)
            val height = pdfiumCore.getPageHeightPoint(pdfDocument, 0) * 2

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565) //Bitmap.Config.ARGB_8888)
            pdfiumCore.renderPageBitmap(pdfDocument, bitmap, 0, 0, 0, width, height)

            pdfiumCore.closeDocument(pdfDocument) // 리소스 해제
            parcelFileDescriptor.close() //파일 탐색 닫기

            Bitmap.createScaledBitmap(bitmap, 500, 600, true) // 썸네일 크기 조정

            return bitmap
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            println("🚨 파일을 찾을 수 없음! 경로 오류: ${uri}")
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 PDF 렌더링 중 오류 발생: ${e.message}")
        }
        return null
    }




    //변경된 PDF 정보를 받아서 UI를 갱신하는 함수
    private val pdfViewerResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            //새로운 PDF URI와 파일명을 가져오기
            val newPdfUri = result.data?.getStringExtra("newPdfUri")
            val newPdfName = result.data?.getStringExtra("newPdfName")

            if (newPdfUri != null && newPdfName != null) {
                println("🔄 새 PDF 저장: $newPdfName")
                //체크용 로그

                // ✅ 필기된 PDF를 SharedPreferences에 추가
                savePdfUri(Uri.parse(newPdfUri), newPdfName)
                //체크용 로그
                println("🔄 새 PDF 이름: $newPdfName")
                println("🔄 새 PDF URI: $newPdfUri")

                // 새로운 썸네일 생성 후 UI 갱신
                val uri = Uri.parse(newPdfUri)
                val bitmap = renderPdfToBitmap(uri)
                if (bitmap != null) {
                    addPdfImage(bitmap, uri, newPdfName) // 새로운 파일 이름 반영
                }
            }
        }
    }


    // 동적으로 ImageView를 생성하고 추가하는 함수(썸네일 + 파일명 + 파일URI)
    // 🔥 썸네일 클릭 시 새로운 PDF 열도록 수정
    private fun addPdfImage(bitmap: Bitmap, fileUri: Uri, fileName: String) {

        //PDF 썸네일을 담을 LinearLayout 생성
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = GridLayout.LayoutParams().apply {
                width = 500
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(50, 40, 50, 20)
            }
        }

        //PDF 썸네일을 표시할 ImageView 생성
        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(500, 600)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)

            //PDF 썸네일 클릭 시 PdfViewerActivity 실행
            setOnClickListener {
                val finalUri = if (fileUri.scheme == "file") {
                    getFileUri(File(fileUri.path!!))
                // ✅ `file://`을 `content://`로 변환
                } else {
                    fileUri
                }

                val intent = Intent(this@MainActivity, PdfViewerActivity::class.java).apply {
                    putExtra("pdfUri", finalUri.toString()) // 최신 PDF URI 전달
                    putExtra("pdfName", fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                println("✅ 최신 PDF 열기: $finalUri")
                pdfViewerResultLauncher.launch(intent)
            }
        }

        //PDF 파일명을 표시할 TextView 생성
        val textView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = fileName
            textSize = 16f
            setPadding(10, 10, 10, 10)
            setTextColor(resources.getColor(android.R.color.white, theme))
        }

        //PDF 삭제 기능을 표시할 Button 생성
        val deleteView = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "삭제"
            textSize = 16f
            setPadding(10,10,10,10)
            setTextColor(resources.getColor(android.R.color.black,theme))

            setOnClickListener {
                removePdf(fileUri, container) // 📌 삭제 함수 호출
            }
        }

        //LinearLayout에 ImageView와 TextView 추가
        container.addView(imageView)
        container.addView(textView)
        container.addView(deleteView)
        binding.pdfContainer.addView(container)
    }

    // PDF 파일 삭제 함수
    private fun removePdf(uri: Uri, container: LinearLayout) {
        val pdfList = getSavedPdfList()

        // SharedPreferences에서 해당 PDF 제거
        pdfList.remove(uri.toString())
        val editor = sharedPref.edit()
        editor.putString("pdf_list", pdfList.toString())
        editor.apply()

        // UI에서 삭제
        binding.pdfContainer.removeView(container)

        println("🗑 PDF 삭제 완료: $uri")
    }



    //로컬 파일을 content URI로 변환하여 반환하는 함수
    private fun getFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            this,
            "com.example.test_app.provider", // ✅ 패키지명에 맞게 변경
            file
        )
    }



    //PDF 파일 이름을 가져오는 함수
    private fun getFileName(uri: Uri): String {
        var name = "알수없음.pdf" // 기본 이름 설정
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex("_display_name")
                //파일명 가져오기

                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
}