package com.example.test_app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivitySttofflineBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SttofflineActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySttofflineBinding
    private val client = OkHttpClient.Builder()
        .connectTimeout(600, java.util.concurrent.TimeUnit.SECONDS)  // 서버 연결까지 대기 시간
        .writeTimeout(600, java.util.concurrent.TimeUnit.SECONDS)    // 요청 전송까지 대기 시간
        .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS)     // 응답 받을 때까지 대기 시간
        .build()
    private var selectedUri: Uri? = null

    private val serverUrl = "http://127.0.0.1:8000/upload" // Termux 서버 주소

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySttofflineBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSelectFile.setOnClickListener {
            selectAudioFile()
        }

        binding.btnSendFile.setOnClickListener {
            selectedUri?.let { uri ->
                sendFileToServer(uri)
            } ?: Toast.makeText(this, "파일을 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectAudioFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        filePickerLauncher.launch(intent)
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedUri = result.data!!.data
                Toast.makeText(this, "✅ 파일 선택 완료", Toast.LENGTH_SHORT).show()
                println("📂 [Android] 선택된 URI: $selectedUri")
            }
        }

    private fun sendFileToServer(uri: Uri) {
        showLoading(true)

        val file = copyUriToFile(uri) ?: run {
            showLoading(false)
            Toast.makeText(this, "파일 변환 실패", Toast.LENGTH_SHORT).show()
            return
        }

        println("📤 [Android] 서버로 전송할 파일 이름: ${file.name}")
        println("📤 [Android] 전송 대상 서버 URL: $serverUrl")

        val requestBody = file.asRequestBody("audio/*".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Part.createFormData("file", file.name, requestBody)

        val request = Request.Builder()
            .url(serverUrl)
            .post(
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(multipartBody)
                    .build()
            )
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    binding.tvResult.text = getString(R.string.error_server_connection, e.message)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "")
                    json.optString("stt_result", "⚠️ 결과 없음")
                } catch (e: Exception) {
                    "❌ 결과 파싱 실패"
                }

                runOnUiThread {
                    showLoading(false)
                    binding.tvResult.text = result
                }
            }
        })
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSendFile.isEnabled = !isLoading
        binding.btnSelectFile.isEnabled = !isLoading
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            // 🔍 파일 이름 추출
            val fileName = queryFileName(uri) ?: "temp_audio.mp3"  // 기본 이름

            val file = File(cacheDir, fileName)  // ✅ 원래 확장자 유지
            println("📁 [Android] 임시 파일 저장 경로: ${file.absolutePath}")

            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            println("✅ [Android] 파일 복사 완료: ${file.name}, 크기: ${file.length()} bytes")
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }
}