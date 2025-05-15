package com.example.test_app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivityMainBinding
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
    private lateinit var binding: ActivityTranslateBinding
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)  // 서버 연결까지 대기 시간
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)    // 요청 전송까지 대기 시간
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // 응답 받을 때까지 대기 시간
        .build()
    private val flaskUrl = "http://127.0.0.1:8000/translate"
    // Termux 서버 주소

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTranslateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendToServer.setOnClickListener {
            val inputText = binding.etInputText.text.toString().trim()
            if (inputText.isNotEmpty()) {
                showLoading(true)
                sendToFlaskServer(inputText)
            } else {
                binding.tvResult.text = "❗ 입력된 문장이 없습니다."
            }
        }
    }



    private fun sendToFlaskServer(userInput: String) {
        val json = JSONObject()
        json.put("text", userInput)

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(flaskUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val result = try {
                    val jsonResponse = JSONObject(body ?: "{}")
                    jsonResponse.optString("result", "⚠️ 번역 결과 없음")
                } catch (e: Exception) {
                    "❌ 응답 파싱 오류"
                }

                runOnUiThread {
                    showLoading(false) // ✅ 응답이 왔으니 무조건 로딩 종료
                    binding.tvResult.text = result
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false) // ✅ 실패해도 반드시 로딩 종료
                    binding.tvResult.text = "🚨 서버 연결 실패: ${e.message}"
                }
            }
        })
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loadingText.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendToServer.isEnabled = !isLoading
    }
}