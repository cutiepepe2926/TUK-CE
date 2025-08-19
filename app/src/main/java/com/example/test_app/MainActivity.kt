package com.example.test_app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.test_app.databinding.ActivityMainBinding
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
import com.example.test_app.databinding.ProfilePopupBinding
import com.example.test_app.utils.MyDocManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale


class MainActivity : AppCompatActivity() {

    //바인딩 초기 선언
    private lateinit var binding: ActivityMainBinding // 메인 액티비티 xml 바인딩
    private lateinit var profileBinding: ProfilePopupBinding // 프로필 팝업 xml 바인딩
    private var profilePopupWindow: PopupWindow? = null // 프로필 팝업 창 확인용

    private lateinit var noteAdapter: NoteAdapter
    private val noteList = mutableListOf<Note>()

    // PDF 선택 런처
    private val pdfPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                showTitleDialogThenCreateNote(it) // 아래 함수로 분리
            }
        }
    
    
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        //바인딩 초기화 및 바인딩 객체 획득
        binding = ActivityMainBinding.inflate(layoutInflater)

        // 로그인 상태 유지 (토큰 확인) (서버 닫힌경우에는 주석처리하기)
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)

        // 로그인 검사 문
//        if (accessToken == null) {
//            // 로그인 정보가 없으면 로그인 화면으로 이동
//            val intent = Intent(this, LoginActivity::class.java)
//            startActivity(intent)
//            finish()
//        } else {
//            // 로그인 정보가 있으면 메인 화면 표시
//            setContentView(binding.root)
//        }

        // 화면 출력
        setContentView(binding.root)
        

        // 왼쪽 상단 버튼 클릭 시 네비게이션 표시
        binding.btnLeftSideNavigator.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // 우측 상단 프로필 버튼 클릭 시 프로필 팝업 표시
        binding.btnProfile.setOnClickListener {
            // 이미 떠 있으면 닫기
            if (profilePopupWindow?.isShowing == true) {
                profilePopupWindow?.dismiss()
                return@setOnClickListener
            }
            // ViewBinding으로 레이아웃 inflate
            profileBinding = ProfilePopupBinding.inflate(layoutInflater)

            // 팝업 뷰 생성
            profilePopupWindow = PopupWindow(
                profileBinding.root,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            // 팝업 뷰 스타일 세팅
            profilePopupWindow?.elevation = 10f
            profilePopupWindow?.isOutsideTouchable = true
            profilePopupWindow?.isFocusable = true

            // X 버튼 동작
            profileBinding.btnClose.setOnClickListener {
                profilePopupWindow?.dismiss()
            }

            // 로그아웃 버튼 동작
            profileBinding.btnLogout.setOnClickListener {
                Toast.makeText(this, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show()
                profilePopupWindow?.dismiss() //팝업해제 후 로그인 액티비티로 이동
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }

            // 팝업 표시 위치 (버튼 아래 또는 화면 오른쪽 상단 등)
            profilePopupWindow?.showAsDropDown(binding.btnProfile, -150, 20) // x, y 오프셋 조절

        }

        // 좌측 네비게이션 문서 클릭 시 메인 화면 문서 페이지 이동
        val btnDocument = binding.sideMenu.findViewById<View>(R.id.btnDocument)
        btnDocument.setOnClickListener {
            // 현재 액티비티가 MainActivity일 경우 → 네비게이션 닫기
            if (this::class.java == MainActivity::class.java) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            // 현재가 MainActivity가 아니면 → MainActivity로 이동
            else {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish() // 현재 액티비티 종료
            }
        }

        // 좌측 네비게이션 휴지통 클릭 시 휴지통 페이지 이동 (휴지통 페이지 작성 필요)
        val btnTrash = binding.sideMenu.findViewById<View>(R.id.btnTrash)
        btnTrash.setOnClickListener {

        }

        // 좌측 네비게이션 음성 텍스트 클릭 시 음성 텍스트 페이지 이동
        val btnSTT = binding.sideMenu.findViewById<View>(R.id.btnSTT)
        btnSTT.setOnClickListener {
            val intent = Intent(this, SttActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 텍스트 요약 클릭 시 요약 페이지 이동
        val btnSummarize = binding.sideMenu.findViewById<View>(R.id.btnSummarize)
        btnSummarize.setOnClickListener {
            val intent = Intent(this, SummarizeActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 하단 문서 생성(노트) 클릭 시 노트 추가 팝업 출력하기
        val btnWrite = binding.sideMenu.findViewById<View>(R.id.btnWrite)
        btnWrite.setOnClickListener {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_add_menu, binding.root,false)
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

        // 좌측 네비게이션 하단 음성 텍스트(마이크) 클릭 시 음성 텍스트 페이지 이동
        val btnSTTUnder = binding.sideMenu.findViewById<View>(R.id.btnSTT_under)
        btnSTTUnder.setOnClickListener {
            val intent = Intent(this, SttActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 하단 텍스트 요약 클릭 시 요약 페이지 이동
        val btnSummarizeUnder = binding.sideMenu.findViewById<View>(R.id.btnSummarize_under)
        btnSummarizeUnder.setOnClickListener {
            val intent = Intent(this, SummarizeActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 하단 설정(톱니바퀴) 클릭 시 이동 (설정 페이지 작성 필요)
        val btnSetting = binding.sideMenu.findViewById<View>(R.id.btnSetting)
        btnSetting.setOnClickListener {

        }

        // 리사이클러뷰 & 어댑터 설정
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewNotes)
        //recyclerView.layoutManager = LinearLayoutManager(this)
        val spanCount = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            4 // 가로 모드에서는 더 많은 열
        } else {
            3 // 세로 모드에서는 기본 열 수
        }

        recyclerView.layoutManager = GridLayoutManager(this, spanCount)

        noteAdapter = NoteAdapter(
            noteList,
            onItemClick = { note -> openNote(note) }, // 클릭 시 호출
            onItemLongClick = { note -> showNoteOptionsDialog(note) } // 롱클릭 시 호출
        )

        recyclerView.adapter = noteAdapter

        //BottomSheetDialog 생성 버튼
        val btnAdd = findViewById<Button>(R.id.addBtn)
        btnAdd.setOnClickListener {
            val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_add_menu, binding.root,false)
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

            // 파일 존재 여부 확인 (디버깅 로그 추가)
            println("PDF 파일 확인: $uri")

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

            // 해상도를 원본 크기 또는 2배로 설정
            //val scaleFactor = 2 // 원하는 배율로 조정 가능 (2배 해상도)
            val width = pdfiumCore.getPageWidthPoint(pdfDocument, 0)
            val height = pdfiumCore.getPageHeightPoint(pdfDocument, 0) * 2

            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565) //Bitmap.Config.ARGB_8888)
            pdfiumCore.renderPageBitmap(pdfDocument, bitmap, 0, 0, 0, width, height)

            println(" PDF 첫 페이지 렌더링 완료: ${bitmap.width}x${bitmap.height}")  //

            pdfiumCore.closeDocument(pdfDocument) // 리소스 해제
            parcelFileDescriptor.close() //파일 탐색 닫기

            bitmap.scale(300, 400) // 썸네일 크기 조정

            return bitmap
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            println("🚨 파일을 찾을 수 없음! 경로 오류: $uri")
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 PDF 렌더링 중 오류 발생: ${e.message}")
        }
        return null
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

                // .mydoc 파일에서 실제 base.pdf 경로를 추출
                val myDocData = MyDocManager(this).loadMyDoc(File(note.myDocPath))
                val basePdfFile = File(myDocData.pdfFilePath) // 여기가 실제 PDF 경로

                // 썸네일 생성 및 저장
                val bitmap = renderPdfToBitmap(Uri.fromFile(basePdfFile)) // 또는 원본 PDF 경로

                val thumbnailPath = bitmap?.let {
                    val file = File(filesDir, "thumb_${System.currentTimeMillis()}.png")

                    FileOutputStream(file).use { out ->
                        val success = it.compress(Bitmap.CompressFormat.PNG, 100, out)
                        println("썸네일 저장 성공 여부: $success")
                    }

                    println("썸네일 경로: ${file.absolutePath}")
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


    // 2) 새 파일(빈 PDF) 생성 → mydoc 및 노트 생성
    private fun showNewNoteDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("새 노트 이름을 입력하세요")
        val input = EditText(this)
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

    // 노트 옵션 선택 다이얼로그
    private fun showNoteOptionsDialog(note: Note) {
        val options = arrayOf("이름 바꾸기", "삭제")

        AlertDialog.Builder(this)
            .setTitle("노트 옵션")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(note)
                    1 -> confirmDeleteNote(note)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 이름 바꾸기 다이얼로그
    private fun showRenameDialog(note: Note) {
        val input = EditText(this)
        input.setText(note.title)

        AlertDialog.Builder(this)
            .setTitle("이름 바꾸기")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val newTitle = input.text.toString()
                if (newTitle.isNotBlank()) {
                    val index = noteList.indexOfFirst { it.id == note.id }
                    if (index != -1) {
                        noteList[index] = note.copy(title = newTitle)
                        noteAdapter.notifyItemChanged(index)
                        saveNoteList()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 삭제 확인
    private fun confirmDeleteNote(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("노트 삭제")
            .setMessage("정말로 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                deleteNote(note)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 삭제 함수
    private fun deleteNote(note: Note) {
        val index = noteList.indexOfFirst { it.id == note.id }
        if (index != -1) {
            noteList.removeAt(index)
            noteAdapter.notifyItemRemoved(index)
            saveNoteList()
        }
    }


}