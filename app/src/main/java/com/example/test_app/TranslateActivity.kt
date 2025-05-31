package com.example.test_app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivityTranslateBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class TranslateActivity : AppCompatActivity() {

    // ViewBinding 변수 선언
    private lateinit var binding: ActivityTranslateBinding

    // OkHttpClient 설정 (연결/쓰기/읽기 타임아웃 지정)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)  // 서버 연결까지 대기 시간
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)    // 요청 전송까지 대기 시간
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // 응답 받을 때까지 대기 시간
        .build()

    // Flask 서버 주소 (Termux에서 구동 중인 서버)
    private val flaskUrl = "http://127.0.0.1:8000/translate"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewBinding 초기화 및 화면 설정
        binding = ActivityTranslateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // PdfViwerActivity.kt에서 넘어온 번역할 OCR 텍스트 결과
        val ocrText = intent.getStringExtra("ocrText")
        if (!ocrText.isNullOrEmpty()) {
            binding.etInputText.setText(ocrText)
        }

        // 번역 요청 버튼 클릭 시
        binding.btnSendToServer.setOnClickListener {

            // 입력 텍스트 가져오기 (기능 통합으로 인해서 OCR한 텍스트를 가져와야함.
            val inputText = binding.etInputText.text.toString().trim()

            // 입력 테스트 유무 체크
            if (inputText.isNotEmpty()) {
                showLoading(true)
                sendToFlaskServer(inputText)
            } else {
                binding.tvResult.text = "❗ 입력된 문장이 없습니다."
            }
        }
    }

    // Flask 서버로 번역 요청 보내기
    private fun sendToFlaskServer(userInput: String) {
        val json = JSONObject()
        json.put("text", userInput) // JSON 형태로 데이터 구성

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = json.toString().toRequestBody(mediaType)

        // 요청 방식 지정
        val request = Request.Builder()
            .url(flaskUrl)
            .post(requestBody)
            .build()

        // 비동기 요청 전송
        client.newCall(request).enqueue(object : Callback {

            // 서버 응답 수신 시 호출되는 콜백 함수
            override fun onResponse(call: Call, response: Response) {
                // 반환된 메세지 변수로 받기
                val body = response.body?.string()

                // result 키 값 추출
                val result = try {
                    val jsonResponse = JSONObject(body ?: "{}")
                    jsonResponse.optString("result", "⚠️ 번역 결과 없음")
                } catch (e: Exception) {
                    "❌ 응답 파싱 오류"
                }

                // UI 스레드에서 결과 표시
                runOnUiThread {
                    showLoading(false) // 로딩 종료
                    binding.tvResult.text = result // 결과 출력
                }
            }

            // 서버 요청 실패 시 호출되는 콜백 함수
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false) // 로딩 종료

                    // 에러 메시지를 문자열 리소스로 출력
                    binding.tvResult.text = getString(R.string.error_server_connection, e.message)
                }
            }
        })
    }

    // 로딩 중 UI 요소 표시/숨김 설정 함수
    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendToServer.isEnabled = !isLoading
    }
}