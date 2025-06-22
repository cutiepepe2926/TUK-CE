package com.example.test_app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.test_app.databinding.ActivitySummarizeBinding
import com.example.test_app.databinding.ProfilePopupBinding
import com.example.test_app.utils.TokenManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.edit
import okhttp3.RequestBody.Companion.asRequestBody

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
class SummarizeActivity : AppCompatActivity() {

    // ViewBinding 변수
    private lateinit var binding: ActivitySummarizeBinding

    // Task ID 표시용 텍스트뷰
    private lateinit var tvTaskId: TextView

    //동적 버튼들이 들어갈 레이아웃
    private lateinit var scrollLayout: LinearLayout

    // 프로필 팝업 xml 바인딩
    private lateinit var profileBinding: ProfilePopupBinding

    // 프로필 팝업 창 확인용
    private var profilePopupWindow: PopupWindow? = null

    // 결과 텍스트 초기값
    private var resultText: String = "아직 요약된 텍스트가 없습니다."

    override fun onCreate(savedInstanceState: Bundle?) {

        binding = ActivitySummarizeBinding.inflate(layoutInflater)

        super.onCreate(savedInstanceState)

        setContentView(binding.root)


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
            val intent = Intent(this, SttActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 텍스트 요약 클릭 시 요약 페이지 이동
        val btnSummarize = binding.sideMenu.findViewById<View>(R.id.btnSummarize)

        btnSummarize.setOnClickListener {
            if (this::class.java == SummarizeActivity::class.java) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
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
            val intent = Intent(this, SttActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 하단 텍스트 요약 클릭 시 요약 페이지 이동
        val btnSummarizeUnder = binding.sideMenu.findViewById<View>(R.id.btnSummarize_under)

        btnSummarizeUnder.setOnClickListener {
            if (this::class.java == SummarizeActivity::class.java) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        // 좌측 네비게이션 하단 영어 번역 클릭 시 번역 페이지 이동
        val btnTranslateUnder = binding.sideMenu.findViewById<View>(R.id.btnTranslate_under)

        btnTranslateUnder.setOnClickListener {
            val intent = Intent(this, TranslateActivity::class.java)
            startActivity(intent)
        }

        // 동적 버튼이 들어갈 레이아웃
        scrollLayout = binding.scrollLayout

        // taskId 표시할 TextView
        tvTaskId = binding.tvTaskId

        // 파일 선택 버튼 클릭 시
        binding.btnfilesummarize.setOnClickListener {
            openOnlineFilePicker()
        }

        // 기존 저장된 taskId 버튼 복원
        restoreTaskIdButtons()
    }

    // 텍스트 파일 탐색기 열기 (PDF 파일 선택)
    private fun openOnlineFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"  // PDF 파일만 선택 가능
        }
        // 런처 실행
        pdfFilePickerLauncher.launch(intent)
    }

    //파일 선택 결과 처리
    private val pdfFilePickerLauncher =
        
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                
                val selectedFileUri = result.data!!.data
                
                if (selectedFileUri != null) {
                    showPageInputDialog(selectedFileUri)
                }
            } 
            
            else {
                Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    // 시작/끝 페이지 입력 Dialog 표시
    private fun showPageInputDialog(fileUri: Uri) {

        val fileName = queryFileName(fileUri) ?: "알 수 없는 파일"

        // 다이얼
        val dialogView = layoutInflater.inflate(R.layout.dialog_page_input, null)
        
        // 시작 페이지
        val startPageEditText = dialogView.findViewById<EditText>(R.id.etStartPage)
        
        // 종료 페이지
        val endPageEditText = dialogView.findViewById<EditText>(R.id.etEndPage)

        AlertDialog.Builder(this)
            .setTitle("페이지 범위 입력")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->

                val startPage = startPageEditText.text.toString().trim()

                val endPage = endPageEditText.text.toString().trim()

                if (startPage.isNotEmpty() && endPage.isNotEmpty()) {

                    // 서버 업로드 시작
                    uploadFile(fileUri, fileName, startPage, endPage)
                }

                else {
                    Toast.makeText(this, "시작과 종료 페이지를 모두 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }


    // 파일 업로드 함수
    private fun uploadFile(fileUri: Uri, fileName: String, startPage: String, endPage: String, retry: Boolean = false) {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val accessToken = sharedPreferences.getString("access_token", null)

        if (accessToken == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val file = uriToFile(fileUri)

        if (file == null || !file.exists()) {
            Toast.makeText(this, "파일 변환 실패", Toast.LENGTH_SHORT).show()
            return
        }


        // 파일 및 페이지 정보 multipart로 구성
        val requestBody = file.asRequestBody("application/pdf".toMediaTypeOrNull())

        val filePart = MultipartBody.Part.createFormData("file", file.name, requestBody)

        val startPageBody = startPage.toRequestBody("text/plain".toMediaTypeOrNull())

        val endPageBody = endPage.toRequestBody("text/plain".toMediaTypeOrNull())

        val call = RetrofitClient.fileUploadService.uploadPdfFileWithPageRange("Bearer $accessToken", filePart, startPageBody, endPageBody)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {

                    val responseBody = response.body()?.string() ?: "서버 응답 없음"

                    resultText = responseBody

                    Toast.makeText(this@SummarizeActivity, responseBody, Toast.LENGTH_SHORT).show()

                    println("파일 업로드 성공! 서버 응답: $responseBody")

                    try {

                        val json = JSONObject(responseBody)

                        val taskId = json.optString("task_id", "N/A")

                        saveSummaryTaskId(taskId) // task_id 저장

                        tvTaskId.text = getString(R.string.task_id_label, taskId)


                        Toast.makeText(this@SummarizeActivity, taskId, Toast.LENGTH_SHORT).show()

                        // 결과 확인 버튼 동적 생성
                        val resultButton = Button(this@SummarizeActivity).apply {
                            text = getString(R.string.summary_result_button_filename, fileName)

                            // 단순 클릭 시 결과 확인
                            setOnClickListener {
                                retrySummaryResultRequest(taskId)
                            }

                            // 길게 클릭 시 삭제 팝업창 출력
                            setOnLongClickListener {
                                AlertDialog.Builder(this@SummarizeActivity)
                                    .setTitle("결과 삭제")
                                    .setMessage("해당 결과를 삭제하시겠습니까?")
                                    .setPositiveButton("예") { _, _ ->
                                        deleteTaskId(taskId, this)
                                    }
                                    .setNegativeButton("아니오", null)
                                    .show()
                                true
                            }
                        }

                        // ScrollView 내부 LinearLayout에 버튼 추가
                        scrollLayout.addView(resultButton)


                    } catch (e: Exception) {
                        e.printStackTrace()

                        // JSON 파싱 실패 시 전체 응답 문자열 표시
                        resultText = responseBody

                        Toast.makeText(this@SummarizeActivity, "응답 파싱 오류", Toast.LENGTH_SHORT).show()
                    }
                }

                else {

                    println("응답 실패: ${response.code()}")

                    if (response.code() == 401 && !retry) {

                        // 토큰 갱신 시도
                        TokenManager.refreshAccessToken(

                            context = this@SummarizeActivity,

                            onSuccess = {
                                println("새로운 토큰으로 재시도 중")

                                // 재시도
                                uploadFile(fileUri, fileName, startPage, endPage, retry = true)

                            },

                            onFailure = {
                                TokenManager.forceLogout(this@SummarizeActivity)
                            }
                        )
                    }
                    else {
                        val errorMessage = response.errorBody()?.string() ?: "알 수 없는 오류 발생"
                        Toast.makeText(this@SummarizeActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                Toast.makeText(this@SummarizeActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
            }

        })
        
    }

    // task_id 삭제 (SharedPreferences 및 화면 버튼 제거)
    private fun deleteTaskId(taskId: String, button: Button) {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        // 기존 저장된 task_id 리스트 가져오기
        val existingJson = sharedPreferences.getString("summary_task_id_list", "[]")

        val type = object : TypeToken<MutableList<String>>() {}.type

        // JSON 문자열을 리스트로 변환
        val taskIdList: MutableList<String> = Gson().fromJson(existingJson, type)

        // 해당 task_id가 리스트에 있을 경우
        if (taskIdList.contains(taskId)) {

            // 리스트에서 삭제
            taskIdList.remove(taskId)

            val newJson = Gson().toJson(taskIdList)

            // SharedPreferences에 저장
            sharedPreferences.edit { putString("summary_task_id_list", newJson) }

            // UI에서 버튼 제거
            scrollLayout.removeView(button)

            Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }


    // Uri → File로 변환 (실제 업로드할 파일 생성)
    private fun uriToFile(uri: Uri): File? {

        // 앱 임시 폴더에 저장할 파일 생성
        val tempFile = File(cacheDir, "temp_upload.pdf")

        return try {

            // 선택한 파일 InputStream 열기
            val inputStream = contentResolver.openInputStream(uri) ?: return null

            val outputStream = FileOutputStream(tempFile)

            // 새 파일로 복사
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
    private fun saveSummaryTaskId(taskId: String) {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        // 기존 저장된 리스트 가져오기
        val existingJson = sharedPreferences.getString("summary_task_id_list", "[]")

        val type = object : TypeToken<MutableList<String>>() {}.type

        val taskIdList: MutableList<String> = Gson().fromJson(existingJson, type)

        // 중복 저장 방지
        if (!taskIdList.contains(taskId)) {

            taskIdList.add(taskId)

            val newJson = Gson().toJson(taskIdList)

            sharedPreferences.edit { putString("summary_task_id_list", newJson) }
        }
    }

    // 앱 시작 시 기존 저장된 task_id 버튼 복원
    private fun restoreTaskIdButtons() {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val taskIdJson = sharedPreferences.getString("summary_task_id_list", "[]")

        val taskIdList = Gson().fromJson(taskIdJson, MutableList::class.java) as List<*>

        // 버튼 생성 전에 기존 뷰 클리어
        scrollLayout.removeAllViews()

        for (taskId in taskIdList) {
            val button = Button(this).apply {
                text = getString(R.string.summary_result_button, taskId)

                //summary_result_button_filename
                setOnClickListener {

                    Toast.makeText(this@SummarizeActivity, "📥 결과 요청: $taskId", Toast.LENGTH_SHORT).show()

                    // 결과 재요청
                    retrySummaryResultRequest(taskId.toString())
                }

                setOnLongClickListener {
                    AlertDialog.Builder(this@SummarizeActivity)
                        .setTitle("결과 삭제")
                        .setMessage("해당 결과를 삭제하시겠습니까?")
                        .setPositiveButton("예") { _, _ ->
                            deleteTaskId(taskId.toString(), this)
                        }
                        .setNegativeButton("아니오", null)
                        .show()
                    true
                }
            }

            // 버튼 추가
            scrollLayout.addView(button)
        }
    }

    // 서버에 결과 요청 (task_id 기반)
    private fun requestWithTokenRetry(task: (accessToken: String) -> Unit) {

        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        val accessToken = sharedPreferences.getString("access_token", null)

        val refreshToken = sharedPreferences.getString("refresh_token", null)

        if (accessToken == null || refreshToken == null) {

            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()

            // 토큰 없는 경우 로그아웃 처리
            TokenManager.forceLogout(this)

            return
        }

        // 먼저 현재 access_token으로 시도
        task("Bearer $accessToken")
    }

    // 서버에 결과 요청 (task_id 기반)
    private fun retrySummaryResultRequest(taskId: String) {
        requestWithTokenRetry { accessToken ->

            val call = RetrofitClient.fileUploadService.getSummarizeResult(accessToken, taskId)

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

                        // 결과를 SummaryResultActivity로 넘겨서 출력
                        val intent = Intent(this@SummarizeActivity, SummaryResultActivity::class.java)

                        intent.putExtra("summary_result", message)

                        startActivity(intent)


                    }

                    // 토큰 만료시 갱신 시도
                    else if (response.code() == 401) {

                        // access_token 만료 → refresh 시도 후 재시도
                        TokenManager.refreshAccessToken(

                            context = this@SummarizeActivity,

                            onSuccess = { newToken ->
                                println("🔁 토큰 재발급 성공, 재요청 중")
                                retrySummaryResultRequest(taskId) // 다시 시도
                            },

                            onFailure = {
                                TokenManager.forceLogout(this@SummarizeActivity)
                            }
                        )
                    }

                    else {
                        Toast.makeText(this@SummarizeActivity, "결과 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@SummarizeActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    // 선택한 Uri에서 파일 이름 추출
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