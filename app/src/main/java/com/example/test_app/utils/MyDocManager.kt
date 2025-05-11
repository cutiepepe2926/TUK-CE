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
    val pdfFilePath: String,
    val strokes: List<Stroke>,
    val annotations: List<TextAnnotation>
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

            /* ② strokes.json */
            zos.putNextEntry(ZipEntry("strokes.json"))
            zos.write(gson.toJson(strokes).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            /* ③ annotations.json */
            zos.putNextEntry(ZipEntry("annotations.json"))
            zos.write(gson.toJson(annotations).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
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
                    "base.pdf" -> {                          // PDF 추출
                        val outFile = File(
                            context.filesDir,
                            "temp_${file.nameWithoutExtension}.pdf"
                        )
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                        pdfPath = outFile.absolutePath
                    }
                    "strokes.json" -> {                      // 필기 로드
                        val json = zis.bufferedReader().readText()
                        strokes = gson.fromJson(json, Array<Stroke>::class.java).toList()
                    }
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
        return MyDocData(pdfPath, strokes, annotations)
    }
}
