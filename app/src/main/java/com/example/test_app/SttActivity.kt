package com.example.test_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
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
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import com.example.test_app.databinding.ProfilePopupBinding
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import java.util.UUID


// 오프라인 STT 결과 저장용 데이터 클래스
data class OfflineSttResult(val id: String, val fileName: String, val result: String)

// 온라인 STT 결과 저장용 데이터 클래스
data class OnlineSttResult(val taskId: String, val fileName: String)


class SttActivity : AppCompatActivity() {

    // ViewBinding 선언
    private lateinit var binding: ActivitySttBinding

    // task_id 출력용 텍스트뷰
    private lateinit var tvTaskId: TextView

    // 결과 버튼 동적 추가 레이아웃
    private lateinit var scrollLayout: LinearLayout

    // 프로필 팝업 ViewBinding
    private lateinit var profileBinding: ProfilePopupBinding // 프로필 팝업 xml 바인딩

    // 팝업 윈도우 객체
    private var profilePopupWindow: PopupWindow? = null

    // OkHttpClient 설정 (네트워크 연결 타임아웃 설정 - 오프라인 STT 서버와 통신용)
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  // 서버 연결까지 최대 대기시간 10분
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // 요청 전송 대기시간 10분
        .readTimeout(600, java.util.concurrent.TimeUnit.SECONDS)     // 응답 대기시간 10분
        .build()

    // 선택한 음성 파일의 Uri 저장 변수
    private var selectedUri: Uri? = null

    // 오프라인 STT 서버 주소 (로컬 Termux 서버)
    private val serverUrl = "http://127.0.0.1:8000/upload"

    // 결과 기본 텍스트
    private var resultText: String = "아직 인식된 텍스트가 없습니다."

    // 음성 파일 이름
    private var selectedFileName: String = ""


    // 네트워크 상태 확인 함수
    private fun isNetworkAvailable(): Boolean {
//        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val activeNetwork = connectivityManager.activeNetworkInfo
//        return activeNetwork != null && activeNetwork.isConnected

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 활성 네트워크 확인
        val network = connectivityManager.activeNetwork ?: return false

        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // 인터넷 가능 여부 확인
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // ViewBinding 초기화
        binding = ActivitySttBinding.inflate(layoutInflater)

        setContentView(binding.root)

        //migrateLegacyOnlineSttDataIfNeeded()

        // 동적 버튼 추가용 레이아웃 바인딩
        scrollLayout = binding.scrollLayout

        // taskId 표시할 TextView 바인딩
        tvTaskId = binding.tvTaskId

        // 왼쪽 상단 버튼 클릭 시 네비게이션 표시
        binding.btnLeftSideNavigator.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // 우측 상단 프로필 버튼 클릭 시 프로필 팝업 표시
        binding.btnProfile.setOnClickListener {
            // 이미 떠 있으면 닫기
            if (profilePopupWindow?.isShowing == true) {
                profilePopupWindow?.dismiss()
                return@setOnClickListener
            }
            // ViewBinding으로 레이아웃 inflate
            profileBinding = ProfilePopupBinding.inflate(layoutInflater)

            // 사용자 ID 프로필 창에 출력
            val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val userId = sharedPreferences.getString("user_id", "Unknown")
            profileBinding.userIdText.text = userId

            // 팝업 뷰 생성
            profilePopupWindow = PopupWindow(
                profileBinding.root,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )

            // 팝업 뷰 스타일 세팅
            profilePopupWindow?.elevation = 10f

            profilePopupWindow?.isOutsideTouchable = true

            profilePopupWindow?.isFocusable = true

            // X 버튼 동작
            profileBinding.btnClose.setOnClickListener {
                profilePopupWindow?.dismiss()
            }

            // 로그아웃 버튼 동작
            profileBinding.btnLogout.setOnClickListener {

                Toast.makeText(this, "로그아웃 되었습니다", Toast.LENGTH_SHORT).show()

                // 사용자 보안 정보 제거
                sharedPreferences.edit {
                    remove("access_token")
                        .remove("refresh_token")
                        .remove("user_id")
                }

                //팝업해제 후 로그인 액티비티로 이동
                profilePopupWindow?.dismiss()

                val intent = Intent(this, LoginActivity::class.java)

                startActivity(intent)

                finish()
            }

            // 팝업 표시 위치 (버튼 아래 또는 화면 오른쪽 상단 등)
            profilePopupWindow?.showAsDropDown(binding.btnProfile, -150, 20) // x, y 오프셋 조절

        }

        // 좌측 네비게이션 문서 클릭 시 메인 화면 문서 페이지 이동
        val btnDocument = binding.sideMenu.findViewById<View>(R.id.btnDocument)
        btnDocument.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 영어 번역 클릭 시 번역 페이지 이동
        val btnTranslate = binding.sideMenu.findViewById<View>(R.id.btnTranslate)
        btnTranslate.setOnClickListener {
            val intent = Intent(this, TranslateActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 음성 텍스트 클릭 시 음성 텍스트 페이지 이동
        val btnSTT = binding.sideMenu.findViewById<View>(R.id.btnSTT)
        btnSTT.setOnClickListener {
            if (this::class.java == SttActivity::class.java) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        // 좌측 네비게이션 텍스트 요약 클릭 시 요약 페이지 이동
        val btnSummarize = binding.sideMenu.findViewById<View>(R.id.btnSummarize)
        btnSummarize.setOnClickListener {
            val intent = Intent(this, SummarizeActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 좌측 네비게이션 하단 문서 생성(노트) 클릭 시 노트 추가 팝업 출력하기
        val btnWrite = binding.sideMenu.findViewById<View>(R.id.btnWrite)
        btnWrite.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 좌측 네비게이션 하단 음성 텍스트(마이크) 클릭 시 음성 텍스트 페이지 이동
        val btnSTTUnder = binding.sideMenu.findViewById<View>(R.id.btnSTT_under)
        btnSTTUnder.setOnClickListener {
            if (this::class.java == SttActivity::class.java) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        // 좌측 네비게이션 하단 텍스트 요약 클릭 시 요약 페이지 이동
        val btnSummarizeUnder = binding.sideMenu.findViewById<View>(R.id.btnSummarize_under)
        btnSummarizeUnder.setOnClickListener {
            val intent = Intent(this, SummarizeActivity::class.java)
            startActivity(intent)
            finish()
        }

        // 좌측 네비게이션 하단 영어 번역 클릭 시 번역 페이지 이동
        val btnTranslateUnder = binding.sideMenu.findViewById<View>(R.id.btnTranslate_under)
        btnTranslateUnder.setOnClickListener {
            val intent = Intent(this, TranslateActivity::class.java)
            startActivity(intent)
        }


        // 파일 선택 후 서버로 전송
        binding.btnSelectFile.setOnClickListener {

            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*" // 오디오 파일만 선택 가능
            }

            // 파일 선택 시작
            filePickerLauncher.launch(intent)
        }

        // 온라인 STT 결과 복원 추가
        restoreTaskIdButtons()

        // 오프라인 STT 결과 복원 추가
        restoreOfflineSttResults()
    }

//    private fun migrateLegacyOnlineSttDataIfNeeded() {
//        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
//        val taskIdJson = sharedPreferences.getString("task_id_list", "[]") ?: "[]"
//
//        try {
//            // 먼저 새 구조로 파싱 시도 (이미 새 구조라면 아무것도 안 함)
//            val newType = object : TypeToken<MutableList<OnlineSttResult>>() {}.type
//            val parsed = Gson().fromJson<MutableList<OnlineSttResult>>(taskIdJson, newType)
//            return  // 새 구조로 이미 되어 있으면 변환 불필요
//        } catch (e: Exception) {
//            // 구버전 데이터로 추정
//            val legacyType = object : TypeToken<MutableList<String>>() {}.type
//            val legacyList: MutableList<String> = Gson().fromJson(taskIdJson, legacyType)
//
//            // 구버전 task_id 리스트를 새 구조로 변환
//            val newList = legacyList.map { taskId ->
//                OnlineSttResult(taskId, fileName = "이름없음")
//            }.toMutableList()
//
//            // 변환된 새 구조로 SharedPreferences 덮어쓰기
//            val newJson = Gson().toJson(newList)
//            sharedPreferences.edit { putString("task_id_list", newJson) }
//        }
//    }


    // 파일 선택 결과를 처리하는 ActivityResult 콜백 등록
    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {

                selectedUri = result.data!!.data

                //val fileName = queryFileName(selectedUri!!) ?: "알 수 없는 파일"

                Toast.makeText(this, "파일 선택 완료", Toast.LENGTH_SHORT).show()

                selectedFileName = queryFileName(selectedUri!!) ?: "알 수 없는 파일"

                // 네트워크 연결 여부 확인
                if (isNetworkAvailable()) {

                    // 네트워크 연결됨 → 온라인 STT 실행
                    selectedUri?.let { uri ->

                        // 온라인 서버로 업로드 시작
                        uploadFileOnline(uri, selectedFileName)

                    } ?: Toast.makeText(this, "파일을 먼저 선택하세요.", Toast.LENGTH_SHORT).show()

                    resultText = "온라인 STT 실행됨"
                }

                else {

                    // 네트워크 없음 → 오프라인 STT 실행
                    selectedUri?.let { uri ->

                        // 오프라인 STT 로컬 서버 업로드 시작
                        uploadFileToOffline(uri)

                    } ?: Toast.makeText(this, "파일을 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
                    resultText = "오프라인 STT 실행됨"
                }
            }
        }


//    private fun showLoading(isLoading: Boolean) {
//        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
//        binding.btnSelectFile.isEnabled = !isLoading
//    }

    // 파일을 앱 내부 임시 저장소로 복사하는 함수 (오프라인 서버 전송을 위해 복사본 생성)
    private fun copyUriToFile(uri: Uri): File? {
        return try {

            // 선택한 파일 InputStream 열기
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            // 파일 이름 추출(파일명 유지)
            // 이름 못가져오면 기본 이름 사용
            val fileName = queryFileName(uri) ?: "temp_audio.mp3"

            val file = File(cacheDir, fileName)  //원래 확장자 유지

            println("[Android] 임시 파일 저장 경로: ${file.absolutePath}")

            val outputStream = FileOutputStream(file)

            // 파일 복사
            inputStream.copyTo(outputStream)

            inputStream.close()

            outputStream.close()

            println("[Android] 파일 복사 완료: ${file.name}, 크기: ${file.length()} bytes")
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 오프라인 서버로 파일 업로드 (Termux 내부 Whisper 서버로 전송)
    private fun uploadFileToOffline(uri: Uri) {
        //showLoading(true)

        val file = copyUriToFile(uri) ?: run {
            //showLoading(false)
            Toast.makeText(this, "파일 변환 실패", Toast.LENGTH_SHORT).show()
            return
        }

        println("[Android] 서버로 전송할 파일 이름: ${file.name}")
        println("[Android] 전송 대상 서버 URL: $serverUrl")

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

        // 오프라인 서버에 비동기 전송 (OkHttp 사용)
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread {
                    //showLoading(false)
                    binding.tvResult.text = getString(R.string.error_server_connection, e.message)
                }
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val result = try {

                    // 서버 응답 받아오기
                    val body = response.body?.string()

                    // JSON 파싱
                    val json = JSONObject(body ?: "")

                    json.optString("stt_result", "⚠️ 결과 없음")

                } catch (e: Exception) {
                    "❌ 결과 파싱 실패"
                }

                // UI 스레드에서 결과 표시
                runOnUiThread {

                    //showLoading(false)

                    binding.tvResult.text = result

                    // 오프라인 결과 저장
                    saveOfflineSttResult(result)
                }
            }
        })
    }

    // 오프라인 STT 결과를 SharedPreferences에 저장
    private fun saveOfflineSttResult(result: String) {

        val sharedPreferences = getSharedPreferences("offline_stt_prefs", Context.MODE_PRIVATE)

        val existingJson = sharedPreferences.getString("offline_result_list", "[]")

        val type = object : TypeToken<MutableList<OfflineSttResult>>() {}.type

        val resultList: MutableList<OfflineSttResult> = Gson().fromJson(existingJson, type)

        // 새 결과 생성 (UUID로 고유 ID 부여)
        val newResult = OfflineSttResult(
            id = UUID.randomUUID().toString(),
            fileName = "파일명_${System.currentTimeMillis()}",
            result = result
        )

        // 결과 리스트에 추가
        resultList.add(newResult)

        // 다시 JSON 문자열로 변환
        val newJson = Gson().toJson(resultList)

        // SharedPreferences 저장
        sharedPreferences.edit { putString("offline_result_list", newJson) }

        // 버튼 즉시 생성하여 화면에 표시
        addOfflineResultButton(newResult)
    }

    // 앱 실행시 SharedPreferences에 저장된 오프라인 결과 복원
    private fun restoreOfflineSttResults() {

        val sharedPreferences = getSharedPreferences("offline_stt_prefs", Context.MODE_PRIVATE)

        val existingJson = sharedPreferences.getString("offline_result_list", "[]")

        val type = object : TypeToken<MutableList<OfflineSttResult>>() {}.type

        val resultList: MutableList<OfflineSttResult> = Gson().fromJson(existingJson, type)

        // 저장된 결과 각각에 대해 버튼 생성
        for (result in resultList) {
            addOfflineResultButton(result)
        }
    }

    // 오프라인 결과 버튼을 동적으로 생성하는 함수
    private fun addOfflineResultButton(result: OfflineSttResult) {

        val button = Button(this).apply {

            val shortId = result.id.take(6)

            text = getString(R.string.offline_result_label, shortId)

            // 그냥 누르면 결과 확인
            setOnClickListener {
//                AlertDialog.Builder(this@SttActivity)
//                    .setTitle("오프라인 STT 결과")
//                    .setMessage(result.result)
//                    .setPositiveButton("확인", null)
//                    .show()
                val intent = Intent(this@SttActivity, SttResultActivity::class.java)
                intent.putExtra("stt_result", result.result)
                startActivity(intent)
            }

            // 길게 누르면 삭제 확인 다이얼로그
            setOnLongClickListener {
                AlertDialog.Builder(this@SttActivity)
                    .setTitle("결과 삭제")
                    .setMessage("해당 결과를 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        deleteOfflineSttResult(result.id, this)
                    }
                    .setNegativeButton("취소", null)
                    .show()
                true
            }
        }

        // 화면에 버튼 추가
        scrollLayout.addView(button)
    }

    // 오프라인 STT 결과 삭제 함수 (로컬 저장 및 UI에서 삭제)
    private fun deleteOfflineSttResult(id: String, button: Button) {

        val sharedPreferences = getSharedPreferences("offline_stt_prefs", Context.MODE_PRIVATE)

        val existingJson = sharedPreferences.getString("offline_result_list", "[]")

        val type = object : TypeToken<MutableList<OfflineSttResult>>() {}.type

        val resultList: MutableList<OfflineSttResult> = Gson().fromJson(existingJson, type)

        // 리스트에서 해당 id 결과 삭제
        resultList.removeAll { it.id == id }

        val newJson = Gson().toJson(resultList)

        sharedPreferences.edit { putString("offline_result_list", newJson) }

        // 버튼 제거
        scrollLayout.removeView(button)

        Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // 선택한 Uri에서 파일 이름 추출
    private fun queryFileName(uri: Uri): String? {

        val cursor = contentResolver.query(uri, null, null, null, null)

        cursor?.use {

            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)

            if (cursor.moveToFirst() && nameIndex != -1) {

                // 파일명 반환
                return cursor.getString(nameIndex)
            }
        }

        // 실패 시 null 반환
        return null
    }

    // 온라인 STT 서버로 파일 업로드 (서버는 Retrofit 사용)
    private fun uploadFileOnline(fileUri: Uri, fileName: String, retry: Boolean = false) {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val accessToken = sharedPreferences.getString("access_token", null)

        // 토큰 없을 시 로그인 요구
        if (accessToken == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // Uri를 File로 변환
        val file = uriToFile(fileUri) ?: run {
            println("파일 변환 실패")
            return
        }

        // 파일 요청 바디 생성
        val requestBody = file.asRequestBody("audio/*".toMediaTypeOrNull())

        val filePart = MultipartBody.Part.createFormData("audio_file", file.name, requestBody)

        val call = RetrofitClient.fileUploadService.uploadFile("Bearer $accessToken", filePart)

        // 비동기 서버 요청 (Retrofit 사용)
        call.enqueue(object : Callback<ResponseBody> {

            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {

                // 서버 응답 성공시
                if (response.isSuccessful) {

                    val responseBody = response.body()?.string() ?: "서버 응답 없음"

                    // 서버 응답 전체 저장
                    resultText = responseBody

                    //Toast.makeText(this@SttActivity, responseBody, Toast.LENGTH_SHORT).show()

                    println("파일 업로드 성공! 서버 응답: $responseBody")

                    try {

                        // 응답 파싱
                        val json = JSONObject(responseBody)

                        val message = json.optString("message", "처리 완료")

                        val taskId = json.optString("task_id", "N/A")

                        // task_id 저장 (SharedPreferences)
                        saveTaskId(taskId, fileName)

                        // 결과 메세지 업데이트
                        resultText = message

                        // 화면에 task_id 표시
                        tvTaskId.text = getString(R.string.task_id_format, taskId) // taskId : $taskID

                        //Toast.makeText(this@SttActivity, message, Toast.LENGTH_SHORT).show()

                        // 결과 확인 버튼 동적 생성
                        val resultButton = Button(this@SttActivity).apply {
                            text = getString(R.string.result_check_filename_format, fileName)

                            setOnClickListener {

                                // 결과 재요청 수행
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
                                                    "processing" -> "처리 중입니다. 잠시만 기다려주세요."
                                                    "completed" -> sttJson.optString("result", "결과 없음")
                                                    "failed" -> "오류 발생: ${sttJson.optString("error", "알 수 없는 오류")}"
                                                    else -> "알 수 없는 상태: $status"
                                                }

//                                                AlertDialog.Builder(this@SttActivity)
//                                                    .setTitle("STT 결과")
//                                                    .setMessage(sttMessage)
//                                                    .setPositiveButton("확인", null)
//                                                    .show()
                                                val intent = Intent(this@SttActivity, SttResultActivity::class.java)
                                                intent.putExtra("stt_result", sttMessage)
                                                startActivity(intent)

                                            } catch (e: Exception) { // JSON 파싱 실패 처리
                                                e.printStackTrace()
                                                Toast.makeText(context, "결과 파싱 오류", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        else {
                                            Toast.makeText(context, "결과 조회 실패", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    override fun onFailure(call: Call<ResponseBody>, t: Throwable) {  // 네트워크 통신 실패 시
                                        Toast.makeText(context, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                                    }
                                })
                            }

                            setOnLongClickListener {
                                AlertDialog.Builder(this@SttActivity)
                                    .setTitle("결과 삭제")
                                    .setMessage("해당 결과를 삭제하시겠습니까?")
                                    .setPositiveButton("삭제") { _, _ ->
                                        deleteTaskId(taskId, this)
                                    }
                                    .setNegativeButton("취소", null)
                                    .show()
                                true
                            }

                        }

                        // 결과 버튼 화면에 추가
                        scrollLayout.addView(resultButton)


                    } catch (e: Exception) { // JSON 파싱 실패 시 전체 문자열 표시

                        e.printStackTrace()

                        // JSON 파싱 실패 시 전체 응답 문자열 표시
                        resultText = responseBody

                        Toast.makeText(this@SttActivity, "응답 파싱 오류", Toast.LENGTH_SHORT).show()
                    }
                }

                else { // 서버 응답 실패 시
                    println("응답 실패: ${response.code()}")

                    // 토큰 만료 시 재발급 시도
                    if (response.code() == 401 && !retry) {

                        // 토큰 갱신 시도
                        TokenManager.refreshAccessToken(

                            context = this@SttActivity,

                            onSuccess = {
                                println("새로운 토큰으로 재시도 중")
                                uploadFileOnline(fileUri, selectedFileName, retry = true)
                            },

                            onFailure = {
                                TokenManager.forceLogout(this@SttActivity)
                            }
                        )
                    }

                    else {

                        val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류 발생"

                        Toast.makeText(this@SttActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("네트워크 오류: ${t.message}")
                Toast.makeText(this@SttActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
            }
        })
    }


    // Uri → File 변환 함수 (온라인 서버 업로드용으로 임시 파일 생성)
    private fun uriToFile(uri: Uri): File? {

        // 앱 캐시 디렉토리에 임시 파일 생성
        val tempFile = File(cacheDir, "temp_audio.mp3")

        return try {

            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val outputStream = FileOutputStream(tempFile)

            inputStream.copyTo(outputStream)

            inputStream.close()

            outputStream.close()

            tempFile
        }

        catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 온라인 STT 결과 task_id 저장 (SharedPreferences 이용)
    private fun saveTaskId(taskId: String, fileName: String) {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val existingJson = sharedPreferences.getString("task_id_list", "[]")

        val type = object : TypeToken<MutableList<OnlineSttResult>>() {}.type

        val taskIdList: MutableList<OnlineSttResult> = Gson().fromJson(existingJson, type)

        // 중복 검사 (taskId 기준으로)
        if (taskIdList.none { it.taskId == taskId }) {

            taskIdList.add(OnlineSttResult(taskId, fileName))

            val newJson = Gson().toJson(taskIdList)

            sharedPreferences.edit { putString("task_id_list", newJson) }
        }
    }

    // 앱 재실행시 기존 task_id 버튼 복원
    private fun restoreTaskIdButtons() {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val taskIdJson = sharedPreferences.getString("task_id_list", "[]")

        val type = object : TypeToken<MutableList<OnlineSttResult>>() {}.type

        val taskIdList: List<OnlineSttResult> = Gson().fromJson(taskIdJson, type)

        // 저장된 모든 task_id에 대해 버튼 생성
        for (task in taskIdList) {
            val button = Button(this).apply {
                text = getString(R.string.result_check_filename_format, task.fileName)

                setOnClickListener {
                    Toast.makeText(this@SttActivity, "결과 요청: $taskId", Toast.LENGTH_SHORT).show()
                    
                    //결과 요청
                    retrySttResultRequest(task.taskId)
                }

                setOnLongClickListener {
                    AlertDialog.Builder(this@SttActivity)
                        .setTitle("결과 삭제")
                        .setMessage("해당 결과를 삭제하시겠습니까?")
                        .setPositiveButton("삭제") { _, _ ->
                            deleteTaskId(task.taskId, this)
                        }
                        .setNegativeButton("취소", null)
                        .show()
                    true
                }

            }
            scrollLayout.addView(button)
        }
    }

    private fun deleteTaskId(taskId: String, button: Button) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val taskIdJson = sharedPreferences.getString("task_id_list", "[]")
        val type = object : TypeToken<MutableList<OnlineSttResult>>() {}.type
        val taskIdList: MutableList<OnlineSttResult> = Gson().fromJson(taskIdJson, type)

        // 리스트에서 taskId 제거
        taskIdList.removeAll { it.taskId == taskId }

        // 다시 저장
        val newJson = Gson().toJson(taskIdList)
        sharedPreferences.edit { putString("task_id_list", newJson) }

        // 버튼 삭제
        scrollLayout.removeView(button)

        Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
    }


    // 토큰 만료 대비 재시도 로직 (고차함수로 task 실행)
    private fun requestWithTokenRetry(task: (accessToken: String) -> Unit) {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val accessToken = sharedPreferences.getString("access_token", null)

        val refreshToken = sharedPreferences.getString("refresh_token", null)

        // 토큰 누락 시 로그아웃
        if (accessToken == null || refreshToken == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            TokenManager.forceLogout(this)
            return
        }

        // access_token 전달하여 작업 수행
        task("Bearer $accessToken")
    }

    // STT 결과 재요청 함수 (서버로 task_id 기반으로 결과 재조회)
    private fun retrySttResultRequest(taskId: String) {

        // 토큰 확인 후 실행
        requestWithTokenRetry { accessToken ->

            val call = RetrofitClient.fileUploadService.getSttResult(accessToken, taskId)

            // 비동기 요청
            call.enqueue(object : Callback<ResponseBody> {

                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {

                    if (response.isSuccessful) {

                        val json = JSONObject(response.body()?.string() ?: "")

                        val message = when (val status = json.optString("status", "")) {
                            "processing" -> "처리 중입니다. 잠시만 기다려주세요."
                            "completed" -> json.optString("result", "결과 없음")
                            "failed" -> "오류 발생: ${json.optString("error", "알 수 없는 오류")}"
                            else -> "알 수 없는 상태: $status"
                        }

                        // 결과 다이얼로그 표시
//                        AlertDialog.Builder(this@SttActivity)
//                            .setTitle("STT 결과")
//                            .setMessage(message)
//                            .setPositiveButton("확인", null)
//                            .show()
                        val intent = Intent(this@SttActivity, SttResultActivity::class.java)
                        intent.putExtra("stt_result", message)
                        startActivity(intent)

                    }

                    // 토큰 만료 시 재발급 시도
                    else if (response.code() == 401) {
                        // access_token 만료 → refresh 시도 후 재시도
                        TokenManager.refreshAccessToken(
                            context = this@SttActivity,
                            onSuccess = {
                                println("토큰 재발급 성공, 재요청 중")
                                retrySttResultRequest(taskId) // 다시 시도
                            },
                            onFailure = {
                                TokenManager.forceLogout(this@SttActivity)
                            }
                        )
                    }

                    else {
                        Toast.makeText(this@SttActivity, "결과 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                // 네트워크 요청 자체 실패
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@SttActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
}