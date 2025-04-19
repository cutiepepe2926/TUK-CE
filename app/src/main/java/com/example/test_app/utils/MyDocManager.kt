package com.example.test_app.utils

import android.content.Context
import com.example.test_app.model.Stroke
import com.google.gson.Gson
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class MyDocData(
    val pdfFilePath: String,
    val strokes: List<Stroke>
)

class MyDocManager(private val context: Context) {
    private val gson = Gson()

    fun saveMyDoc(fileName: String, pdfFilePath: String, strokes: List<Stroke>) {
        val myDocFile = File(context.filesDir, fileName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(myDocFile))).use { zos ->
            // base.pdf 추가
            val pdfEntry = ZipEntry("base.pdf")
            zos.putNextEntry(pdfEntry)
            FileInputStream(pdfFilePath).use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()

            // strokes.json
            val strokesJson = gson.toJson(strokes)
            val strokesEntry = ZipEntry("strokes.json")
            zos.putNextEntry(strokesEntry)
            zos.write(strokesJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    fun loadMyDoc(file: File): MyDocData {
        var pdfFilePath = ""
        var strokes: List<Stroke> = emptyList()

        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "base.pdf" -> {
                        val outFile = File(context.filesDir, "temp_${file.nameWithoutExtension}.pdf")
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        pdfFilePath = outFile.absolutePath
                    }
                    "strokes.json" -> {
                        val strokesJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        strokes = gson.fromJson(strokesJson, Array<Stroke>::class.java).toList()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return MyDocData(pdfFilePath, strokes)
    }
}
