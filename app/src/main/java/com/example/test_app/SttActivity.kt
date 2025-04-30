package com.example.test_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivitySttBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.Model
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream


class SttActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySttBinding
    private lateinit var tvResult: TextView
    private var resultText: String = "아직 인식된 텍스트가 없습니다."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySttBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOnlineStt.setOnClickListener {
            openOnlineFilePicker()
            resultText = "온라인 STT 실행됨"
        }
        binding.btnOfflineStt.setOnClickListener {
            openOfflineFilePicker()
            resultText = "오프라인 STT 실행됨"
        }
        binding.btnShowResult.setOnClickListener {
            tvResult.text = resultText
        }

    }

    // 🔹 파일 탐색기 열기 (MP3 파일 선택)
    private fun openOfflineFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/mpeg" // mp3 전용
        }
        offlineFilePickerLauncher.launch(intent)
    }

    private val offlineFilePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val selectedFileUri = result.data!!.data
                if (selectedFileUri != null) {
                    val wavFile = uriToFile(selectedFileUri) // 🔁 wav로 저장된 파일 경로 변환
                    if (wavFile != null) {
                        println("🎧 선택된 오프라인 MP3 파일 URI: $selectedFileUri")
                        //runOfflineStt(wavFile)
                    } else {
                        Toast.makeText(this, "파일 로드 실패", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }


    /*private fun runOfflineStt(wavFile: File) {
        Thread {
            try {
                val model = Model(this@SttActivity, "vosk-model-small-ko-0.22")
                val recognizer = Recognizer(model, 16000.0f)

                val inputStream = FileInputStream(wavFile)
                val buffer = ByteArray(4096)

                while (true) {
                    val read = inputStream.read(buffer)
                    if (read <= 0) break
                    recognizer.acceptWaveForm(buffer, read)
                }

                val resultJson = recognizer.finalResult
                val recognizedText = JSONObject(resultJson).getString("text")

                runOnUiThread {
                    resultText = recognizedText
                    Toast.makeText(this, "✅ 변환 완료!", Toast.LENGTH_SHORT).show()
                }

                inputStream.close()
                recognizer.close()
                model.close()

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "🚨 STT 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }*/





    // 온라인 버전
    // 🔹 파일 탐색기 열기 (MP3 파일 선택)
    private fun openOnlineFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*" // 🔹 모든 오디오 파일 형식 지원
        }
        onlinefilePickerLauncher.launch(intent)
    }

    // 🔹 파일 선택 결과 처리
    private val onlinefilePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val selectedFileUri = result.data!!.data
                if (selectedFileUri != null) {
                    println("✅ 선택된 온라인 MP3 파일 URI: $selectedFileUri")
                    uploadFile(selectedFileUri) // 🔹 선택한 파일을 서버로 업로드
                }
            } else {
                Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    // 🔹 파일 업로드 함수
    private fun uploadFile(fileUri: Uri) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken == null) {
            println("🚨 로그인 정보 없음: 토큰이 없습니다.")
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔹 Uri → 실제 파일 변환 (임시 파일 생성)
        val file = uriToFile(fileUri) ?: run {
            println("🚨 파일 변환 실패")
            return
        }

        val requestBody = RequestBody.create("audio/*".toMediaTypeOrNull(), file)
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)

        val call = RetrofitClient.fileUploadService.uploadFile("Bearer $accessToken", filePart) // ✅ 수정된 코드

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string() ?: "서버 응답 없음"
                    println("✅ 파일 업로드 성공! 서버 응답: $responseBody")
                    Toast.makeText(this@SttActivity, responseBody, Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류 발생"
                    println("🚨 파일 업로드 실패: $errorMessage")
                    Toast.makeText(this@SttActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }

            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("🚨 네트워크 오류: ${t.message}")
                Toast.makeText(this@SttActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 🔹 Uri → File 변환 함수 (파일을 임시로 복사하여 저장)
    private fun uriToFile(uri: Uri): File? {
        val tempFile = File(cacheDir, "temp_audio.mp3")
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}