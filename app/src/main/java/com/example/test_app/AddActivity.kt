package com.example.test_app

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivityAddBinding
import java.io.File
import java.io.FileOutputStream

class AddActivity : AppCompatActivity() {

    private val PICK_PDF_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //바인딩 객체 획득
        val binding = ActivityAddBinding.inflate(layoutInflater)

        // + 버튼 누르면 뒤로가기
        binding.addBtn.setOnClickListener {
            super.onBackPressed()
            //deprecated 됐지만, 일단 사용
        }

        // PDF 추가 버튼 누르면 파일 선택기로 이동
        binding.fileBtn.setOnClickListener {
            openFilePicker()
        }

        // "새 노트" 버튼 클릭 시 다이얼로그 띄우기
        binding.newNoteBtn.setOnClickListener {
            showInputDialog()
        }

        //액티비티 화면 출력
        setContentView(binding.root)
    }
    
    
    //파일탐색기 여는 함수
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT) // ✅ 기존 ACTION_GET_CONTENT → ACTION_OPEN_DOCUMENT 변경함
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "application/pdf"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // ✅ 읽기 권한 부여
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) // ✅ 영구적 권한 부여
        startActivityForResult(intent, PICK_PDF_REQUEST) //intent에 정보 담아서 MainActivity로 반환
    }

    // ✅ PDF 이름을 입력받는 다이얼로그 표시하는 함수
    // ✅ PDF 이름을 입력받는 다이얼로그 표시
    private fun showInputDialog() {
        val editText = EditText(this)
        editText.hint = "저장할 PDF 이름 입력"

        val dialog = AlertDialog.Builder(this)
            .setTitle("새 PDF 이름 입력")
            .setView(editText)
            .setPositiveButton("확인") { _, _ ->
                val pdfName = editText.text.toString().trim()
                if (pdfName.isNotEmpty()) {
                    val pdfFile = createEmptyPdf(pdfName) // 빈 PDF 생성
                    if (pdfFile != null) {
                        returnToMainActivity(pdfName, pdfFile.absolutePath)
                    }
                }
            }
            .setNegativeButton("취소", null)
            .create()

        dialog.show()
    }

    // ✅ 빈 PDF 생성
    private fun createEmptyPdf(fileName: String): File? {
        try {
            val cleanedFileName = fileName.replace(".pdf", "").replace(" ", "_") // 공백 처리
            val directory = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "SavedPDFs")
            if (!directory.exists()) directory.mkdirs() // 폴더가 없으면 생성

            val pdfFile = File(directory, "$cleanedFileName.pdf")
            if (pdfFile.exists()) pdfFile.delete() // 동일한 이름이 있으면 삭제 후 새로 생성

            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 크기
            val page = pdfDocument.startPage(pageInfo)
            pdfDocument.finishPage(page)
            pdfDocument.writeTo(FileOutputStream(pdfFile)) // 파일 저장
            pdfDocument.close()

            println("✅ 빈 PDF 생성 완료: ${pdfFile.absolutePath}")
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            println("🚨 빈 PDF 생성 중 오류 발생: ${e.message}")
        }
        return null
    }

    // ✅ 입력받은 파일 이름을 MainActivity로 전달
    private fun returnToMainActivity(pdfName: String, filePath: String) {
        val resultIntent = Intent()
        resultIntent.putExtra("pdfName", "$pdfName.pdf") // 파일 이름 전달
        resultIntent.putExtra("pdfPath", filePath) // 파일 경로 전달
        setResult(RESULT_OK, resultIntent)
        finish()
    }


    // 파일 선택 결과 처리

    //파일을 선택한 경우, resultCode는 RESULT_OK, 선택된 파일의 URI가 Intent.data에 포함됨.
    //파일 선택을 취소한 경우, resultCode는 RESULT_CANCELED, Intent.data는 null이됨.

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK && data != null) {
            val uri = data.data
            println("선택한 PDF URI: $uri") // ✅ 디버깅 로그 추가

            if (uri != null) {
                // 📌 Uri에 대한 영구적인 읽기 권한 요청
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // MainActivity로 URI 전달
                val resultIntent = Intent()
                resultIntent.data = uri
                setResult(RESULT_OK, resultIntent)
                finish() // AddActivity 종료
            }
        }
    }
}