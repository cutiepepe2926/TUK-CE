package com.example.test_app

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ReadImageText {
    // 한국어 및 라틴 문자(영어) OCR을 위한 Recognizer
    private val recognizerKorean = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val recognizerLatin = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    /**
     * 이미지에서 텍스트를 추출하는 함수
     * @param image OCR을 수행할 Bitmap 이미지
     * @param callback OCR 결과를 비동기적으로 반환하는 콜백 함수
     */
    fun processImage(image: Bitmap, callback: (String) -> Unit) {
        val inputImage = InputImage.fromBitmap(image, 0)

        // 한국어 인식기 우선 실행
        recognizerKorean.process(inputImage)
            .addOnSuccessListener { text ->
                if (text.text.isNotEmpty()) {
                    callback(text.text) // 한국어 OCR 결과 반환
                } else {
                    // 한국어 결과가 없으면 영어 OCR 실행
                    processLatinText(inputImage, callback)
                }
            }
            .addOnFailureListener {
                // 한국어 인식이 실패하면 영어 OCR 실행
                processLatinText(inputImage, callback)
            }
    }

    /**
     * 영어 OCR 실행 (한국어가 없을 경우)
     */
    private fun processLatinText(image: InputImage, callback: (String) -> Unit) {
        recognizerLatin.process(image)
            .addOnSuccessListener { text ->
                callback(text.text) // 영어 OCR 결과 반환
            }
            .addOnFailureListener {
                callback("OCR 실패") // OCR 실패 시 메시지 반환
            }
    }

    // OCR이 끝난 후 메모리 해제
    /*fun close() {
        recognizerKorean.close()
        recognizerLatin.close()
    }*/
}