package com.example.test_app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
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
import java.io.File
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
            val intent = Intent(this, SttActivity::class.java)
            startActivity(intent)
        }

        // OCR 페이지 이동 버튼 (사진으로 OCR)
        val btnOcr = findViewById<ImageButton>(R.id.btnOcr)
        btnOcr.setOnClickListener {
            val intent = Intent(this, OcrActivity::class.java)
            startActivity(intent)
        }

        // 요약 페이지 이동 버튼 (텍스트 파일로 요약)
        val btnSummarize = findViewById<ImageButton>(R.id.btnSummarize)
        btnSummarize.setOnClickListener {
            val intent = Intent(this, SummarizeActivity::class.java)
            startActivity(intent)
        }


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

    //아래는 통합될 함수 목록들임.

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