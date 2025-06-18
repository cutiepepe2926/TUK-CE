package com.example.test_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.test_app.databinding.ActivitySttBinding
import com.example.test_app.utils.TokenManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class testActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySttBinding
    private lateinit var tvTaskId: TextView
    private lateinit var scrollLayout: LinearLayout



    private val client = OkHttpClient.Builder()
        .connectTimeout(600, java.util.concurrent.TimeUnit.SECONDS)  // 서버 연결까지 대기 시간
        .writeTimeout(600, java.util.concurrent.TimeUnit.SECONDS)    // 요청 전송까지 대기 시간
        .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS)     // 응답 받을 때까지 대기 시간
        .build()

    private var selectedUri: Uri? = null

    private val serverUrl = "http://127.0.0.1:8000/upload" // Termux 서버 주소

    private var resultText: String = "아직 인식된 텍스트가 없습니다."

    // 네트워크 상태 확인 함수
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySttBinding.inflate(layoutInflater)
        setContentView(binding.root)

        scrollLayout = findViewById(R.id.scrollLayout)
        tvTaskId = findViewById(R.id.tvTaskId)




        // 파일 선택 버튼
        binding.btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            filePickerLauncher.launch(intent)
        }

        // 파일 전송 버튼
        // 클릭 시 자동 분기
        binding.btnSendFile.setOnClickListener {
            if (isNetworkAvailable()) {
                // 온라인 업로드
                selectedUri?.let { uri ->
                    uploadFile(uri)
                } ?: Toast.makeText(this, "파일을 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
                resultText = "📡 온라인 STT 실행됨"
            } else {
                selectedUri?.let { uri ->
                    sendFileToOfflineServer(uri)
                } ?: Toast.makeText(this, "파일을 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
                resultText = "📴 오프라인 STT 실행됨"
            }
        }

        restoreTaskIdButtons()
    }

    // 파일 선택 결과
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                selectedUri = result.data!!.data
                Toast.makeText(this, "✅ 파일 선택 완료", Toast.LENGTH_SHORT).show()
                println("📂 [Android] 선택된 URI: $selectedUri")
            }
        }



    // 오프라인 서버로 전송
    private fun sendFileToOfflineServer(uri: Uri) {
        // 기존 SttofflineActivity 코드 복붙 (copyUriToFile(), OkHttp 요청 등)
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

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    showLoading(false)
                    binding.tvResult.text = getString(R.string.error_server_connection, e.message)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
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
        //binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        //binding.btnSendFile.isEnabled = !isLoading
        //binding.btnSelectFile.isEnabled = !isLoading
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

        val requestBody = file.asRequestBody("audio/*".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("audio_file", file.name, requestBody)

        val call = RetrofitClient.fileUploadService.uploadFile("Bearer $accessToken", filePart)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()?.string() ?: "서버 응답 없음"
                    resultText = responseBody
                    Toast.makeText(this@testActivity, responseBody, Toast.LENGTH_SHORT).show()
                    println("✅ 파일 업로드 성공! 서버 응답: $responseBody")
                    try {
                        val json = JSONObject(responseBody)
                        val message = json.optString("message", "처리 완료")
                        val taskId = json.optString("task_id", "N/A")
                        saveTaskId(taskId) // ✅ task_id 리스트에 저장
                        resultText = message
                        tvTaskId.text = getString(R.string.task_id_format, taskId) // taskId : $taskID

                        Toast.makeText(this@testActivity, message, Toast.LENGTH_SHORT).show()

                        // ✅ 결과 확인 버튼 동적 생성
                        val resultButton = Button(this@testActivity).apply {
                            text = getString(R.string.result_check_format, taskId) // 결과 확인 : %taskId

                            setOnClickListener {
                                retrySttResultRequest(taskId)
                                val sharedAuthPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                                val accessSTTtoken = sharedAuthPreferences.getString("access_token", null)

                                if (accessSTTtoken == null) {
                                    Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    return@setOnClickListener
                                }

                                val sttCall = RetrofitClient.fileUploadService.getSttResult("Bearer $accessSTTtoken", taskId)

                                sttCall.enqueue(object : Callback<ResponseBody> {
                                    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                                        if (response.isSuccessful) {
                                            val body = response.body()?.string()
                                            try {
                                                val sttJson = JSONObject(body ?: "")
                                                val status = sttJson.optString("status", "")

                                                val sttMessage = when (status) {
                                                    "processing" -> "🕓 처리 중입니다. 잠시만 기다려주세요."
                                                    "completed" -> sttJson.optString("result", "결과 없음")
                                                    "failed" -> "❌ 오류 발생: ${sttJson.optString("error", "알 수 없는 오류")}"
                                                    else -> "❓ 알 수 없는 상태: $status"
                                                }

                                                AlertDialog.Builder(this@testActivity)
                                                    .setTitle("STT 결과")
                                                    .setMessage(sttMessage)
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
                        Toast.makeText(this@testActivity, "응답 파싱 오류", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    println("🚨 응답 실패: ${response.code()}")
                    if (response.code() == 401 && !retry) {
                        // 🔄 토큰 갱신 시도
                        TokenManager.refreshAccessToken(
                            context = this@testActivity,
                            onSuccess = {
                                println("🔁 새로운 토큰으로 재시도 중")
                                uploadFile(fileUri, retry = true) // 재시도
                            },
                            onFailure = {
                                TokenManager.forceLogout(this@testActivity)
                            }
                        )
                    } else {
                        val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류 발생"
                        Toast.makeText(this@testActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("🚨 네트워크 오류: ${t.message}")
                Toast.makeText(this@testActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
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
        //val taskIdList = Gson().fromJson(existingJson, MutableList::class.java) as MutableList<String>
        val type = object : TypeToken<MutableList<String>>() {}.type
        val taskIdList: MutableList<String> = Gson().fromJson(existingJson, type)

        if (!taskIdList.contains(taskId)) {
            taskIdList.add(taskId)
            val newJson = Gson().toJson(taskIdList)
            sharedPreferences.edit { putString("task_id_list", newJson) }
        }
    }

    //task_id 복원
    private fun restoreTaskIdButtons() {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val taskIdJson = sharedPreferences.getString("task_id_list", "[]")
        //val taskIdList = Gson().fromJson(taskIdJson, MutableList::class.java) as List<String>
        val type = object : TypeToken<List<String>>() {}.type
        val taskIdList: List<String> = Gson().fromJson(taskIdJson, type)

        for (taskId in taskIdList) {
            val button = Button(this).apply {
                text = getString(R.string.result_check_format, taskId)
                setOnClickListener {
                    Toast.makeText(this@testActivity, "📥 결과 요청: $taskId", Toast.LENGTH_SHORT).show()
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
                        val message = when (val status = json.optString("status", "")) {
                            "processing" -> "🕓 처리 중입니다. 잠시만 기다려주세요."
                            "completed" -> json.optString("result", "결과 없음")
                            "failed" -> "❌ 오류 발생: ${json.optString("error", "알 수 없는 오류")}"
                            else -> "❓ 알 수 없는 상태: $status"
                        }

                        AlertDialog.Builder(this@testActivity)
                            .setTitle("STT 결과")
                            .setMessage(message)
                            .setPositiveButton("확인", null)
                            .show()

                    } else if (response.code() == 401) {
                        // 🔁 access_token 만료 → refresh 시도 후 재시도
                        TokenManager.refreshAccessToken(
                            context = this@testActivity,
                            onSuccess = {
                                println("🔁 토큰 재발급 성공, 재요청 중")
                                retrySttResultRequest(taskId) // 다시 시도
                            },
                            onFailure = {
                                TokenManager.forceLogout(this@testActivity)
                            }
                        )
                    } else {
                        Toast.makeText(this@testActivity, "결과 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@testActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}