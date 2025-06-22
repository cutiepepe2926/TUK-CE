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

    private lateinit var binding: ActivitySummarizeBinding
    private lateinit var tvTaskId: TextView
    private lateinit var scrollLayout: LinearLayout
    private lateinit var profileBinding: ProfilePopupBinding // 프로필 팝업 xml 바인딩
    private var profilePopupWindow: PopupWindow? = null // 프로필 팝업 창 확인용

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
                profilePopupWindow?.dismiss() //팝업해제 후 로그인 액티비티로 이동
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
            finish() // 현재 액티비티 종료
        }

        // 좌측 네비게이션 휴지통 클릭 시 휴지통 페이지 이동 (휴지통 페이지 작성 필요)
        val btnTrash = binding.sideMenu.findViewById<View>(R.id.btnTrash)
        btnTrash.setOnClickListener {

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

        scrollLayout = binding.scrollLayout


        tvTaskId = binding.tvTaskId

        binding.btnfilesummarize.setOnClickListener {
            openOnlineFilePicker()
        }

        restoreTaskIdButtons()
    }

    // 텍스트 파일 탐색기 열기 (txt 파일 선택)
    private fun openOnlineFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"  // ✅ PDF 파일만 선택 가능
        }
        textfilePickerLauncher.launch(intent)
    }

    //파일 선택 결과 처리
    private val textfilePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val selectedFileUri = result.data!!.data
                if (selectedFileUri != null) {
                    println("✅ 선택된 텍스트 파일 URI: $selectedFileUri")
                    showPageInputDialog(selectedFileUri)
                //uploadFile(selectedFileUri) // 🔹 선택한 파일을 서버로 업로드
                }
            } else {
                Toast.makeText(this, "파일 선택이 취소되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    // 시작 페이지 마지막 페이지 입력 받는 함수
    private fun showPageInputDialog(fileUri: Uri) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_page_input, null)
        val startPageEditText = dialogView.findViewById<EditText>(R.id.etStartPage)
        val endPageEditText = dialogView.findViewById<EditText>(R.id.etEndPage)

        AlertDialog.Builder(this)
            .setTitle("페이지 범위 입력")
            .setView(dialogView)
            .setPositiveButton("확인") { _, _ ->
                val startPage = startPageEditText.text.toString().trim()
                val endPage = endPageEditText.text.toString().trim()
                if (startPage.isNotEmpty() && endPage.isNotEmpty()) {
                    uploadFile(fileUri, startPage, endPage)
                } else {
                    Toast.makeText(this, "시작과 종료 페이지를 모두 입력하세요.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }


    // 파일 업로드 함수
    private fun uploadFile(fileUri: Uri, startPage: String, endPage: String, retry: Boolean = false) {
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
                    println("✅ 파일 업로드 성공! 서버 응답: $responseBody")
                    try {
                        val json = JSONObject(responseBody)
                        val taskId = json.optString("task_id", "N/A")
                        saveSummaryTaskId(taskId)
                        //task_id 리스트에 저장
                        //resultText= message
                        tvTaskId.text = getString(R.string.task_id_label, taskId)


                        Toast.makeText(this@SummarizeActivity, taskId, Toast.LENGTH_SHORT).show()

                        // ✅ 결과 확인 버튼 동적 생성
                        val resultButton = Button(this@SummarizeActivity).apply {
                            text = getString(R.string.summary_result_button, taskId)

                            setOnClickListener {
                                retrySummaryResultRequest(taskId)
                            }

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

                        // ✅ ScrollView 내부 LinearLayout에 버튼 추가
                        scrollLayout.addView(resultButton)


                    } catch (e: Exception) {
                        e.printStackTrace()
                        // JSON 파싱 실패 시 전체 응답 문자열 표시
                        resultText = responseBody
                        Toast.makeText(this@SummarizeActivity, "응답 파싱 오류", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    println("🚨 응답 실패: ${response.code()}")
                    if (response.code() == 401 && !retry) {
                        // 🔄 토큰 갱신 시도
                        TokenManager.refreshAccessToken(
                            context = this@SummarizeActivity,
                            onSuccess = {
                                println("🔁 새로운 토큰으로 재시도 중")
                                uploadFile(fileUri, startPage, endPage ,retry = true) // 재시도
                            },
                            onFailure = {
                                TokenManager.forceLogout(this@SummarizeActivity)
                            }
                        )
                    } else {
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

    // summarize 결과 삭제
    private fun deleteTaskId(taskId: String, button: Button) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val existingJson = sharedPreferences.getString("summary_task_id_list", "[]")
        val type = object : TypeToken<MutableList<String>>() {}.type
        val taskIdList: MutableList<String> = Gson().fromJson(existingJson, type)

        if (taskIdList.contains(taskId)) {
            taskIdList.remove(taskId)
            val newJson = Gson().toJson(taskIdList)
            sharedPreferences.edit { putString("summary_task_id_list", newJson) }

            // UI에서 버튼 제거
            scrollLayout.removeView(button)

            Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }


    // 🔹 Uri → File 변환 함수 (파일을 임시로 복사하여 저장)
    private fun uriToFile(uri: Uri): File? {
        val tempFile = File(cacheDir, "temp_upload.pdf")
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
    private fun saveSummaryTaskId(taskId: String) {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val existingJson = sharedPreferences.getString("summary_task_id_list", "[]")
        val type = object : TypeToken<MutableList<String>>() {}.type
        val taskIdList: MutableList<String> = Gson().fromJson(existingJson, type)


        if (!taskIdList.contains(taskId)) {
            taskIdList.add(taskId)
            val newJson = Gson().toJson(taskIdList)
            sharedPreferences.edit { putString("summary_task_id_list", newJson) }
        }
    }

    //task_id 복원
    private fun restoreTaskIdButtons() {
        val sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val taskIdJson = sharedPreferences.getString("summary_task_id_list", "[]")
        val taskIdList = Gson().fromJson(taskIdJson, MutableList::class.java) as List<*>

        // 버튼 생성 전에 기존 뷰 클리어
        scrollLayout.removeAllViews()

        for (taskId in taskIdList) {
            val button = Button(this).apply {
                text = getString(R.string.summary_result_button, taskId)

                setOnClickListener {
                    Toast.makeText(this@SummarizeActivity, "📥 결과 요청: $taskId", Toast.LENGTH_SHORT).show()
                    retrySummaryResultRequest(taskId.toString())
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

    private fun retrySummaryResultRequest(taskId: String) {
        requestWithTokenRetry { accessToken ->
            val call = RetrofitClient.fileUploadService.getSummarizeResult(accessToken, taskId)

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

//                        AlertDialog.Builder(this@SummarizeActivity)
//                            .setTitle("요약 결과")
//                            .setMessage(message)
//                            .setPositiveButton("확인", null)
//                            .show()

                        val intent = Intent(this@SummarizeActivity, SummaryResultActivity::class.java)
                        intent.putExtra("summary_result", message)
                        startActivity(intent)


                    } else if (response.code() == 401) {
                        // 🔁 access_token 만료 → refresh 시도 후 재시도
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
                    } else {
                        Toast.makeText(this@SummarizeActivity, "결과 조회 실패", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    Toast.makeText(this@SummarizeActivity, "네트워크 오류: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

}