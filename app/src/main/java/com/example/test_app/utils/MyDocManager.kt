package com.example.test_app.utils

import android.content.Context
import com.example.test_app.model.Stroke
import com.example.test_app.model.TextAnnotation
import com.google.gson.Gson
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/* .mydoc 내부 구성
   ├─ base.pdf           ← 원본(또는 현재) PDF
   ├─ strokes.json       ← 필기 Stroke 목록
   └─ annotations.json   ← OCR 텍스트 어노테이션 목록
*/

data class MyDocData(
    val pdfFilePath: String, // 압축 해제된 PDF 파일 경로
    val strokes: List<Stroke>, // 복원된 필기 목록
    val annotations: List<TextAnnotation> // 복원된 텍스트 어노테이션 목록
)

class MyDocManager(private val context: Context) {
    private val gson = Gson()

    /* ---------------- 저장 ---------------- */
    fun saveMyDoc(fileName: String, pdfFilePath: String, strokes: List<Stroke>, annotations: List<TextAnnotation>) {
        val myDocFile = File(context.filesDir, fileName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(myDocFile))).use { zos ->

            /* ① base.pdf */
            zos.putNextEntry(ZipEntry("base.pdf"))
            FileInputStream(pdfFilePath).use { it.copyTo(zos) }
            zos.closeEntry()
            //→ PDF 파일을 복사해서 ZIP 안에 "base.pdf"로 저장

            /* ② strokes.json */
            zos.putNextEntry(ZipEntry("strokes.json"))
            zos.write(gson.toJson(strokes).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            //→ 필기 정보(Stroke 리스트)를 JSON 문자열로 변환해 저장

            /* ③ annotations.json */
            zos.putNextEntry(ZipEntry("annotations.json"))
            zos.write(gson.toJson(annotations).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            //→ OCR/텍스트 주석 데이터(TextAnnotation)도 JSON으로 저장
        }
    }

    /* ---------------- 로드 ---------------- */
    fun loadMyDoc(file: File): MyDocData {
        var pdfPath      = ""
        var strokes      : List<Stroke>         = emptyList()
        var annotations  : List<TextAnnotation> = emptyList()

        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when (entry.name) {

                    //→ 압축에서 PDF를 꺼내고 임시 PDF 파일로 저장
                    "base.pdf" -> {                          // PDF 추출
                        val outFile = File(
                            context.filesDir,
                            "temp_${file.nameWithoutExtension}.pdf"
                        )
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                        pdfPath = outFile.absolutePath
                    }

                    //→ 필기 데이터(JSON)를 Stroke 객체 리스트로 변환
                    "strokes.json" -> {                      // 필기 로드
                        val json = zis.bufferedReader().readText()
                        strokes = gson.fromJson(json, Array<Stroke>::class.java).toList()
                    }

                    //→ OCR/주석 데이터 복원
                    "annotations.json" -> {                  // 어노테이션 로드
                        val json = zis.bufferedReader().readText()
                        annotations = gson.fromJson(
                            json,
                            Array<TextAnnotation>::class.java
                        ).toList()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        //→ 로드된 PDF 경로, 필기, 텍스트 어노테이션을 하나의 객체로 반환
        return MyDocData(pdfPath, strokes, annotations)
    }
}
