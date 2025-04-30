package com.example.test_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivitySttBinding
import com.example.test_app.utils.TokenManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream


class SttActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySttBinding
    private lateinit var tvTaskId: TextView
    private lateinit var scrollLayout: LinearLayout

    private var resultText: String = "아직 인식된 텍스트가 없습니다."

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySttBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scrollLayout = findViewById(R.id.scrollLayout)


        tvTaskId = findViewById(R.id.tvTaskId)


        binding.btnOnlineStt.setOnClickListener {
            openOnlineFilePicker()
            resultText = "온라인 STT 실행됨"
        }
        /*binding.btnOfflineStt.setOnClickListener {
            openOfflineFilePicker()
            resultText = "오프라인 STT 실행됨"
        }*/

        restoreTaskIdButtons()
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
                        Toast.makeText(this, "파일 로드 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }


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
                    println("✅ 선택된 온라인 음성 파일 URI: $selectedFileUri")
                    uploadFile(selectedFileUri) // 🔹 선택한 파일을 서버로 업로드
                }
            } else {
                Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    // 🔹 파일 업로드 함수
    private fun uploadFile(fileUri: Uri, retry: Boolean = false) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val file = uriToFile(fileUri) ?: run {
            println("🚨 파일 변환 실패")
            return
        }

        val requestBody = RequestBody.create("audio/*".toMediaTypeOrNull(), file)
        val filePart = MultipartBody.Part.createFormData("audio_file", file.name, requestBody)

        val call = RetrofitClient.fileUploadService.uploadFile("Bearer $accessToken", filePart)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string() ?: "서버 응답 없음"
                    resultText = responseBody
                    Toast.makeText(this@SttActivity, responseBody, Toast.LENGTH_SHORT).show()
                    println("✅ 파일 업로드 성공! 서버 응답: $responseBody")
                    try {
                        val json = JSONObject(responseBody)
                        val message = json.optString("message", "처리 완료")
                        val taskId = json.optString("task_id", "N/A")
                        saveTaskId(taskId) // ✅ task_id 리스트에 저장
                        resultText = message
                        tvTaskId.text = "Task ID: $taskId" // ✅ TextView에 표시

                        Toast.makeText(this@SttActivity, message, Toast.LENGTH_SHORT).show()

                        // ✅ 결과 확인 버튼 동적 생성
                        val resultButton = Button(this@SttActivity).apply {
                            text = "결과 확인: $taskId"
                            setOnClickListener {
                                retrySttResultRequest(taskId)
                                val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                                val accessToken = sharedPreferences.getString("access_token", null)

                                if (accessToken == null) {
                                    Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }

                                val call = RetrofitClient.fileUploadService.getSttResult("Bearer $accessToken", taskId)

                                call.enqueue(object : Callback<ResponseBody> {
                                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                                        if (response.isSuccessful) {
                                            val body = response.body()?.string()
                                            try {
                                                val json = JSONObject(body ?: "")
                                                val status = json.optString("status", "")

                                                val message = when (status) {
                                                    "processing" -> "🕓 처리 중입니다. 잠시만 기다려주세요."
                                                    "completed" -> json.optString("result", "결과 없음")
                                                    "failed" -> "❌ 오류 발생: ${json.optString("error", "알 수 없는 오류")}"
                                                    else -> "❓ 알 수 없는 상태: $status"
                                                }

                                                AlertDialog.Builder(this@SttActivity)
                                                    .setTitle("STT 결과")
                                                    .setMessage(message)
                                                    .setPositiveButton("확인", null)
                                                    .show()

                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                Toast.makeText(context, "결과 파싱 오류", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "결과 조회 실패", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                                        Toast.makeText(context, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                                    }
                                })
                            }
                        }

                        // ✅ ScrollView 내부 LinearLayout에 버튼 추가
                        scrollLayout.addView(resultButton)


                    } catch (e: Exception) {
                        e.printStackTrace()
                        // JSON 파싱 실패 시 전체 응답 문자열 표시
                        resultText = responseBody
                        Toast.makeText(this@SttActivity, "응답 파싱 오류", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    println("🚨 응답 실패: ${response.code()}")
                    if (response.code() == 401 && !retry) {
                        // 🔄 토큰 갱신 시도
                        TokenManager.refreshAccessToken(
                            context = this@SttActivity,
                            onSuccess = {
                                println("🔁 새로운 토큰으로 재시도 중")
                                uploadFile(fileUri, retry = true) // 재시도
                            },
                            onFailure = {
                                TokenManager.forceLogout(this@SttActivity)
                            }
                        )
                    } else {
                        val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류 발생"
                        Toast.makeText(this@SttActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
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

    // task_id 저장함수
    private fun saveTaskId(taskId: String) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val existingJson = sharedPreferences.getString("task_id_list", "[]")
        val taskIdList = Gson().fromJson(existingJson, MutableList::class.java) as MutableList<String>

        if (!taskIdList.contains(taskId)) {
            taskIdList.add(taskId)
            val newJson = Gson().toJson(taskIdList)
            sharedPreferences.edit().putString("task_id_list", newJson).apply()
        }
    }

    //task_id 복원
    private fun restoreTaskIdButtons() {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val taskIdJson = sharedPreferences.getString("task_id_list", "[]")
        val taskIdList = Gson().fromJson(taskIdJson, MutableList::class.java) as List<String>

        for (taskId in taskIdList) {
            val button = Button(this).apply {
                text = "결과 확인: $taskId"
                setOnClickListener {
                    Toast.makeText(this@SttActivity, "📥 결과 요청: $taskId", Toast.LENGTH_SHORT).show()
                    // 결과 요청 API 호출 가능
                }
            }
            scrollLayout.addView(button)
        }
    }

    private fun requestWithTokenRetry(task: (accessToken: String) -> Unit) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val accessToken = sharedPreferences.getString("access_token", null)
        val refreshToken = sharedPreferences.getString("refresh_token", null)

        if (accessToken == null || refreshToken == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            TokenManager.forceLogout(this)
            return
        }

        // 🟢 먼저 현재 access_token으로 시도
        task("Bearer $accessToken")
    }


    private fun retrySttResultRequest(taskId: String) {
        requestWithTokenRetry { accessToken ->
            val call = RetrofitClient.fileUploadService.getSttResult(accessToken, taskId)

            call.enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body()?.string() ?: "")
                        val status = json.optString("status", "")
                        val message = when (status) {
                            "processing" -> "🕓 처리 중입니다. 잠시만 기다려주세요."
                            "completed" -> json.optString("result", "결과 없음")
                            "failed" -> "❌ 오류 발생: ${json.optString("error", "알 수 없는 오류")}"
                            else -> "❓ 알 수 없는 상태: $status"
                        }

                        AlertDialog.Builder(this@SttActivity)
                            .setTitle("STT 결과")
                            .setMessage(message)
                            .setPositiveButton("확인", null)
                            .show()

                    } else if (response.code() == 401) {
                        // 🔁 access_token 만료 → refresh 시도 후 재시도
                        TokenManager.refreshAccessToken(
                            context = this@SttActivity,
                            onSuccess = { newToken ->
                                println("🔁 토큰 재발급 성공, 재요청 중")
                                retrySttResultRequest(taskId) // 다시 시도
                            },
                            onFailure = {
                                TokenManager.forceLogout(this@SttActivity)
                            }
                        )
                    } else {
                        Toast.makeText(this@SttActivity, "결과 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@SttActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }



}