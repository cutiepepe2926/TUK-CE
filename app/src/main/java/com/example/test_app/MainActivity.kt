package com.example.test_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.example.test_app.databinding.ActivityMainBinding
import com.example.test_app.databinding.ActivityMainToolbarBinding
import com.example.test_app.utils.PdfUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shockwave.pdfium.PdfiumCore
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.FileNotFoundException
import java.io.FileOutputStream
import com.example.test_app.model.Note
import androidx.recyclerview.widget.RecyclerView
import com.example.test_app.adapter.NoteAdapter
import com.example.test_app.utils.MyDocManager
import com.google.android.material.bottomsheet.BottomSheetDialog


class MainActivity : AppCompatActivity() {

    //바인딩 초기 선언
    private lateinit var binding: ActivityMainBinding
    private lateinit var toolbarBinding: ActivityMainToolbarBinding

    //!!신규 바인딩 2개!!
    private lateinit var noteAdapter: NoteAdapter
    private val noteList = mutableListOf<Note>()

    //!!신규 런처!!
    // PDF 선택 런처
    private val pdfPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                //createNoteFromPdf(uri)
                showTitleDialogThenCreateNote(it) // ✅ 아래 함수로 분리
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

        //화면 출력
        setContentView(binding.root)
        
        // 툴바 설정
        setSupportActionBar(toolbarBinding.mainToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // 타이틀 비설정


        // 툴바 버튼 설정(로그인)
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

//        // OCR 페이지 이동 버튼 (사진으로 OCR)
//        val btnOcr = findViewById<ImageButton>(R.id.btnOcr)
//        btnOcr.setOnClickListener {
//            val intent = Intent(this, OcrActivity::class.java)
//            startActivity(intent)
//        }


        // 리사이클러뷰 & 어댑터 설정
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        //recyclerView.layoutManager = LinearLayoutManager(this)
        val spanCount = 3 // 태블릿은 3도 추천 가능
        recyclerView.layoutManager = GridLayoutManager(this, spanCount)

        noteAdapter = NoteAdapter(noteList) { note ->
            openNote(note)
        }
        recyclerView.adapter = noteAdapter

        //BottomSheetDialog 생성 버튼
        val btnAdd = findViewById<Button>(R.id.addBtn)
        btnAdd.setOnClickListener {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_add_menu, null)
            val dialog = BottomSheetDialog(this)
            dialog.setContentView(bottomSheetView)

            val importPdf = bottomSheetView.findViewById<TextView>(R.id.menu_import_pdf)
            val createNote = bottomSheetView.findViewById<TextView>(R.id.menu_create_new_note)

            importPdf.setOnClickListener {
                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                dialog.dismiss()
            }

            createNote.setOnClickListener {
                showNewNoteDialog()
                dialog.dismiss()
            }

            dialog.show()
        }

        // 앱 실행 시 저장된 노트 목록 불러오기 (notes.json)
        loadNoteList()
        noteAdapter.notifyDataSetChanged()

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

            println("🖼️ PDF 첫 페이지 렌더링 완료: ${bitmap.width}x${bitmap.height}")  // ✅ 추가

            pdfiumCore.closeDocument(pdfDocument) // 리소스 해제
            parcelFileDescriptor.close() //파일 탐색 닫기

            Bitmap.createScaledBitmap(bitmap, 300, 400, true) // 썸네일 크기 조정

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



    // 동적으로 ImageView를 생성하고 추가하는 함수(썸네일 + 파일명 + 파일URI)
    // 🔥 썸네일 클릭 시 새로운 PDF 열도록 수정
//    private fun addPdfImage(bitmap: Bitmap, fileUri: Uri, fileName: String) {
//
//        //PDF 썸네일을 담을 LinearLayout 생성
//        val container = LinearLayout(this).apply {
//            orientation = LinearLayout.VERTICAL
//            layoutParams = GridLayout.LayoutParams().apply {
//                width = 500
//                height = GridLayout.LayoutParams.WRAP_CONTENT
//                setMargins(50, 40, 50, 20)
//            }
//        }
//
//        //PDF 썸네일을 표시할 ImageView 생성
////        val imageView = ImageView(this).apply {
////            layoutParams = LinearLayout.LayoutParams(500, 600)
////            scaleType = ImageView.ScaleType.CENTER_CROP
////            setImageBitmap(bitmap)
////
////            //PDF 썸네일 클릭 시 PdfViewerActivity 실행
////            setOnClickListener {
////                val finalUri = if (fileUri.scheme == "file") {
////                    getFileUri(File(fileUri.path!!))
////                // ✅ `file://`을 `content://`로 변환
////                } else {
////                    fileUri
////                }
////
////                val intent = Intent(this@MainActivity, PdfViewerActivity::class.java).apply {
////                    putExtra("pdfUri", finalUri.toString()) // 최신 PDF URI 전달
////                    putExtra("pdfName", fileName)
////                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
////                }
////                println("✅ 최신 PDF 열기: $finalUri")
////                pdfViewerResultLauncher.launch(intent)
////            }
////        }
////
////        //PDF 파일명을 표시할 TextView 생성
////        val textView = TextView(this).apply {
////            layoutParams = LinearLayout.LayoutParams(
////                LinearLayout.LayoutParams.WRAP_CONTENT,
////                LinearLayout.LayoutParams.WRAP_CONTENT
////            )
////            text = fileName
////            textSize = 16f
////            setPadding(10, 10, 10, 10)
////            setTextColor(resources.getColor(android.R.color.white, theme))
////        }
//
//        //PDF 삭제 기능을 표시할 Button 생성
//        val deleteView = Button(this).apply {
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.WRAP_CONTENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//            )
//            text = "삭제"
//            textSize = 16f
//            setPadding(10,10,10,10)
//            setTextColor(resources.getColor(android.R.color.black,theme))
//
//            setOnClickListener {
//                removePdf(fileUri, container) // 📌 삭제 함수 호출
//            }
//        }
//
//        //LinearLayout에 ImageView와 TextView 추가
//        container.addView(imageView)
//        container.addView(textView)
//        container.addView(deleteView)
//        binding.pdfContainer.addView(container)
//    }




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

    //!!신규!! 아래는 통합될 함수 목록들임.

    private fun showTitleDialogThenCreateNote(uri: Uri) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("노트 이름을 입력하세요")
        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton("확인") { _, _ ->
            val title = input.text.toString()
            if (title.isNotEmpty()) {
                val note = PdfUtils.createNoteFromPdf(this, uri, title)

                // 🔧 .mydoc 파일에서 실제 base.pdf 경로를 추출
                val myDocData = MyDocManager(this).loadMyDoc(File(note.myDocPath))
                val basePdfFile = File(myDocData.pdfFilePath) // 🔥 여기가 실제 PDF 경로

                // ✅ 썸네일 생성 및 저장
                val bitmap = renderPdfToBitmap(Uri.fromFile(basePdfFile)) // 또는 원본 PDF 경로

                val thumbnailPath = bitmap?.let {
                    val file = File(filesDir, "thumb_${System.currentTimeMillis()}.png")

                    FileOutputStream(file).use { out ->
                        val success = it.compress(Bitmap.CompressFormat.PNG, 100, out)
                        println("📸 썸네일 저장 성공 여부: $success")
                    }

                    println("📂 썸네일 경로: ${file.absolutePath}")
                    file.absolutePath
                }

                // 노트에 썸네일 경로 포함시켜서 리스트에 추가
                val finalNote = note.copy(thumbnailPath = thumbnailPath)
                noteList.add(finalNote)
                noteAdapter.notifyItemInserted(noteList.size - 1)
                saveNoteList()
            }
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }


    // 1) 기기에서 PDF 선택 후 mydoc으로 만들기
    private fun createNoteFromPdf(uri: Uri) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("노트 이름을 입력하세요")
        val input = android.widget.EditText(this)
        builder.setView(input)
        builder.setPositiveButton("확인") { _, _ ->
            val title = input.text.toString()
            if (title.isNotEmpty()) {
                // 내부 저장소에 PDF 복사 후 mydoc 생성
                val note = PdfUtils.createNoteFromPdf(this, uri, title)
                noteList.add(note)
                noteAdapter.notifyItemInserted(noteList.size - 1)
                saveNoteList()
            }
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }

    // 2) 새 파일(빈 PDF) 생성 → mydoc 및 노트 생성
    private fun showNewNoteDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("새 노트 이름을 입력하세요")
        val input = android.widget.EditText(this)
        builder.setView(input)
        builder.setPositiveButton("확인") { _, _ ->
            val title = input.text.toString()
            if (title.isNotEmpty()) {
                val note = PdfUtils.createBlankNote(this, title)
                noteList.add(note)
                noteAdapter.notifyItemInserted(noteList.size - 1)
                saveNoteList()
            }
        }
        builder.setNegativeButton("취소", null)
        builder.show()
    }

    // 노트 클릭 시 PdfViewerActivity로 전환
    private fun openNote(note: Note) {
        val intent = Intent(this, PdfViewerActivity::class.java)
        intent.putExtra("noteId", note.id)
        intent.putExtra("myDocPath", note.myDocPath)
        startActivity(intent)
    }

    // 노트 목록을 filesDir의 "notes.json"에 저장
    private fun saveNoteList() {
        val notesFile = File(filesDir, "notes.json")
        val gson = Gson()
        val json = gson.toJson(noteList)
        notesFile.writeText(json)
    }

    // 저장된 "notes.json" 파일로부터 노트 목록을 불러옴
    private fun loadNoteList() {
        val notesFile = File(filesDir, "notes.json")
        if (notesFile.exists()) {
            val gson = Gson()
            val json = notesFile.readText()
            val type = object : TypeToken<List<Note>>() {}.type
            val loadedNotes = gson.fromJson<List<Note>>(json, type)
            noteList.clear()
            noteList.addAll(loadedNotes)
        }
    }
}