package com.example.test_app.utils

import android.os.Environment
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.itextpdf.text.Document
import com.itextpdf.text.Image
import com.itextpdf.text.pdf.PdfWriter
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    /*
     * mydoc에서 base.pdf + strokes를 합쳐서 새 PDF로 저장
     */
    fun export(context: Context, myDocPath: String, outputFileName: String) {
        val myDocData = MyDocManager(context).loadMyDoc(File(myDocPath))

        // 1) base.pdf를 PDFView처럼 렌더링 → 여기서는 첫 페이지만 예시
        val basePdfFile = File(myDocData.pdfFilePath)
        val renderedBitmap = PdfRenderUtils.renderFirstPageToBitmap(context, basePdfFile)

        // 2) renderedBitmap 위에 strokes를 다시 그리기
        val canvas = Canvas(renderedBitmap)
        StrokeRenderUtils.drawStrokesOnCanvas(canvas, myDocData.strokes)

        // 3) renderedBitmap을 새 PDF로 저장 (iText 사용)
        // 변경: 파일을 Download 폴더에 저장하도록 경로를 변경합니다.
        val exportDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val exportFile = File(exportDir, outputFileName)

        // FileOutputStream을 use 블록으로 감싸서 자동 종료 처리
        FileOutputStream(exportFile).use { fos ->
            val document = Document()
            PdfWriter.getInstance(document, fos)
            document.open()
            val image = Image.getInstance(renderedBitmapToBytes(renderedBitmap))
            // image.scaleToFit(document.pageSize.width, document.pageSize.height) // 필요시 조정
            document.add(image)
            document.close()
        }
    }

    // Bitmap을 ByteArray로 변환하는 함수
    private fun renderedBitmapToBytes(bitmap: Bitmap): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }
}

/**
 * (개념 예시) PDF 첫 페이지를 Bitmap으로 렌더링하는 유틸
 * 실제 구현은 PdfiumAndroid 라이브러리 등 사용 필요
 */
object PdfRenderUtils {
    fun renderFirstPageToBitmap(context: Context, pdfFile: File): Bitmap {
        // TODO: PdfiumAndroid 사용해서 첫 페이지 bitmap 추출
        // 여기서는 임시로 1080x1920 흰색 Bitmap 반환
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        // "PDF page rendered" 가정
        return bitmap
    }
}

/**
 * (개념 예시) Canvas 위에 Stroke들 다시 그리기
 */
object StrokeRenderUtils {
    fun drawStrokesOnCanvas(canvas: Canvas, strokes: List<com.example.test_app.model.Stroke>) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
        }
        for (stroke in strokes) {
            paint.color = stroke.color
            paint.strokeWidth = stroke.width
            val points = stroke.points
            for (i in 0 until points.size - 1) {
                val start = points[i]
                val end = points[i + 1]
                canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            }
        }
    }
}