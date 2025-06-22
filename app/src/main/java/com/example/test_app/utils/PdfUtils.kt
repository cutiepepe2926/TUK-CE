package com.example.test_app.utils

import android.content.Context
import android.net.Uri
import com.example.test_app.model.Note
import java.io.File
import java.io.FileOutputStream

object PdfUtils {

    private var noteIdCounter = 0L

    // 1) 새 파일(빈 PDF) 생성 -> mydoc
    fun createBlankNote(context: Context, title: String): Note {

        // 1-1. 빈 PDF 생성 (예: iText 사용 or 간단히 1페이지짜리 PDF)
        val blankPdf = File(context.filesDir, "blank_${System.currentTimeMillis()}.pdf")
        createSinglePagePdf(blankPdf) // 아래에서 구현

        // 1-2. .mydoc 생성
        val myDocName = "note_${System.currentTimeMillis()}.mydoc"
        MyDocManager(context).saveMyDoc(
            fileName = myDocName,
            pdfFilePath = blankPdf.absolutePath,
            strokes = emptyList(),
            annotations = emptyList()
        )
        //→ 방금 만든 PDF를 .mydoc 형식으로 저장 (필기, 주석은 없음)

        // 1-3. Note 객체 생성
        noteIdCounter++
        return Note(
            id = noteIdCounter,
            title = title,
            myDocPath = File(context.filesDir, myDocName).absolutePath
        )
        //→ 노트 정보 (id, 제목, .mydoc 경로)를 담은 Note 객체 반환
    }

    // 2) 외부 PDF -> mydoc
    fun createNoteFromPdf(context: Context, uri: Uri, title: String): Note {
        val copiedPdf = File(context.filesDir, "imported_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(copiedPdf).use { output ->
                input.copyTo(output)
            }
        }

        val myDocName = "note_${System.currentTimeMillis()}.mydoc"
        MyDocManager(context).saveMyDoc(
            fileName = myDocName,
            pdfFilePath = copiedPdf.absolutePath,
            strokes = emptyList(),
            annotations = emptyList()
        )

        noteIdCounter++
        return Note(
            id = noteIdCounter,
            title = title,
            myDocPath = File(context.filesDir, myDocName).absolutePath
        )
    }

    // 간단히 1페이지짜리 PDF 생성 (iText5, iText7, PdfBox 등 라이브러리 사용 가능)
    private fun createSinglePagePdf(outputFile: File) {
        // iText5 예시
        // implementation 'com.itextpdf:itextg:5.5.13.3'
        try {
            val document = com.itextpdf.text.Document()
            val fos = FileOutputStream(outputFile)
            val writer = com.itextpdf.text.pdf.PdfWriter.getInstance(document, fos)
            document.open()
            document.add(com.itextpdf.text.Paragraph(" ")) // 빈 페이지
            document.close()
            writer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

