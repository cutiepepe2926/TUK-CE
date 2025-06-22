package com.example.test_app

import android.content.Context
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
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)  // 서버 연결까지 최대 30초 대기
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)    // 요청 데이터 전송까지 최대 30초 대기
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // 서버 응답까지 최대 60초 대기
        .build()

    // Flask 서버 주소 (Termux에서 구동 중인 서버)
    private val flaskUrl = "http://127.0.0.1:8000/translate"

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // ViewBinding 초기화 및 화면 설정
        binding = ActivityTranslateBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // PdfViewerActivity로부터 OCR 결과 문자열을 받아옴
        val ocrText = intent.getStringExtra("ocrText")

        if (!ocrText.isNullOrEmpty()) {

            binding.etInputText.setText(ocrText) // EditText에 OCR 결과 설정

        }

        // 복사 버튼 클릭 이벤트
        binding.btnCopy.setOnClickListener {
            val resultText = binding.tvResult.text.toString()

            if (resultText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("번역 결과", resultText)
                clipboard.setPrimaryClip(clip)

                android.widget.Toast.makeText(this, "복사되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(this, "복사할 내용이 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 번역 요청 버튼 클릭 시
        binding.btnSendToServer.setOnClickListener {

            // 입력된 텍스트 가져오기 (빈칸 제거)
            val inputText = binding.etInputText.text.toString().trim()

            // 입력 체크문
            if (inputText.isNotEmpty()) {

                showLoading(true) // 로딩 화면 표시

                sendToFlaskServer(inputText) // 서버 요청 실행
            }
            else {

                // 입력이 비어있을 때 메시지
                binding.tvResult.text = "입력된 문장이 없습니다."
            }
        }
    }



    // Flask 서버로 번역 요청 보내기
    private fun sendToFlaskServer(userInput: String) {

        val json = JSONObject()

        json.put("text", userInput) // JSON 형식으로 요청 바디 구성

        val mediaType = "application/json".toMediaTypeOrNull()

        // 문자열을 RequestBody로 변환
        val requestBody = json.toString().toRequestBody(mediaType)

        // POST 요청 구성
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

                // JSON 응답 파싱
                val result = try {

                    val jsonResponse = JSONObject(body ?: "{}")

                    // "result" 키의 값을 추출
                    jsonResponse.optString("result", "번역 결과 없음")

                } catch (e: Exception) {
                    "응답 파싱 오류"
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

        binding.btnSendToServer.isEnabled = !isLoading // 로딩 중에는 버튼 비활성화
    }
}