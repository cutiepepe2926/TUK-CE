package com.example.test_app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import com.example.test_app.databinding.ActivityTranslateBinding
import com.example.test_app.databinding.ProfilePopupBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import android.os.Handler
import okhttp3.FormBody
import com.example.test_app.utils.TokenManager

class TranslateActivity : AppCompatActivity() {

    // ViewBinding 변수 선언
    private lateinit var binding: ActivityTranslateBinding

    // OkHttpClient 설정 (연결/쓰기/읽기 타임아웃 지정)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)  // 서버 연결까지 최대 30초 대기
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)    // 요청 데이터 전송까지 최대 30초 대기
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // 서버 응답까지 최대 60초 대기
        .build()

    // 프로필 팝업 xml 바인딩
    private lateinit var profileBinding: ProfilePopupBinding

    // 프로필 팝업 창 확인용
    private var profilePopupWindow: PopupWindow? = null

    // Flask 서버 주소 (Termux에서 구동 중인 서버)
    private val flaskUrl = "http://127.0.0.1:8000/translate"

    // 온라인 번역 서버 URL
    private val onlineBaseUrl = "https://www.omniwrite.r-e.kr:8443"
    private val onlinePostUrl = "$onlineBaseUrl/api/translation/"
    private val onlineResultUrlPrefix = "$onlineBaseUrl/api/translation/result/"

    // 온라인 결과 폴링 관련 설정
    private val pollMaxAttempts = 20              // 최대 재시도 횟수(예: 20회)
    private val pollIntervalMs = 1500L           // 재시도 간격(1.5초)

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // ViewBinding 초기화 및 화면 설정
        binding = ActivityTranslateBinding.inflate(layoutInflater)

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
            if (this::class.java == TranslateActivity::class.java) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
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
            val intent = Intent(this, SummarizeActivity::class.java)
            startActivity(intent)
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
            val intent = Intent(this, SummarizeActivity::class.java)
            startActivity(intent)
        }

        // 좌측 네비게이션 하단 영어 번역 클릭 시 번역 페이지 이동
        val btnTranslateUnder = binding.sideMenu.findViewById<View>(R.id.btnTranslate_under)

        btnTranslateUnder.setOnClickListener {
            if (this::class.java == TranslateActivity::class.java) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

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

                Toast.makeText(this, "복사되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "복사할 내용이 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 번역 요청 버튼 클릭 시
        binding.btnSendToServer.setOnClickListener {

            // 입력된 텍스트 가져오기 (빈칸 제거)
            val inputText = binding.etInputText.text.toString().trim()

            // 입력 체크문
            if (inputText.isNotEmpty()) {

                showLoading(true) // 로딩 화면 표시

                if (isNetworkAvailable()) {
                    // 온라인: 원격 서버에 번역 요청
                    sendToOnlineServer(inputText)
                } else {
                    // 오프라인: 기존 로컬 Flask 서버로 요청
                    sendToFlaskServer(inputText)
                }

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

    // 네트워크 연결 상태 확인 함수
    private fun isNetworkAvailable(): Boolean {
        // ConnectivityManager를 통해 현재 활성 네트워크의 기능(capabilities)을 확인
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        // 실제 인터넷 연결 가능한지 여부 판정
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 토큰 확인 후 작업 실행(없으면 로그아웃 유도, 401이면 갱신 후 재시도)
    // STT에서 쓰던 패턴을 번역에도 그대로 사용
    private fun requestWithTokenRetry(task: (accessTokenHeader: String) -> Unit) {
        val sp = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val access = sp.getString("access_token", null)
        val refresh = sp.getString("refresh_token", null)

        if (access == null || refresh == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            TokenManager.forceLogout(this)  // 로그인 화면으로 유도
            return
        }

        task("Bearer $access") // 정상 토큰으로 우선 시도
    }

    // 온라인 서버로 번역 요청(POST) → task_id 수신 후 결과 폴링(GET)
    // ── 온라인 번역 업로드: x-www-form-urlencoded + Authorization: Bearer <access> ──
    private fun sendToOnlineServer(userInput: String) {
        // 먼저 토큰 확인/적용
        requestWithTokenRetry { accessHeader ->

            // 서버가 요구한 x-www-form-urlencoded 바디 구성
            // src_lang=en, tgt_lang=ko, text=<번역할 문장>
            val formBody = FormBody.Builder()
                .add("src_lang", "en")
                .add("tgt_lang", "ko")
                .add("text", userInput)
                .build()

            // POST 요청 생성(토큰 헤더 포함)
            val request = Request.Builder()
                .url(onlinePostUrl)
                .addHeader("Authorization", accessHeader) // ★ 토큰 추가
                .post(formBody)
                .build()

            // 비동기 요청 전송
            client.newCall(request).enqueue(object : okhttp3.Callback {

                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    runOnUiThread {
                        showLoading(false)
                        binding.tvResult.text = "온라인 서버 연결 실패: ${e.message ?: "알 수 없는 오류"}"
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val bodyStr = response.body?.string()

                    // 401 등의 인증 에러 처리: 토큰 갱신 후 재시도
                    if (response.code == 401) {
                        TokenManager.refreshAccessToken(
                            context = this@TranslateActivity,
                            onSuccess = {
                                // 갱신 성공 → 재시도
                                sendToOnlineServer(userInput)
                            },
                            onFailure = {
                                runOnUiThread {
                                    showLoading(false)
                                    TokenManager.forceLogout(this@TranslateActivity)
                                }
                            }
                        )
                        return
                    }

                    if (!response.isSuccessful) {
                        runOnUiThread {
                            showLoading(false)
                            binding.tvResult.text = "요청 실패(${response.code}): ${bodyStr ?: "서버 응답 없음"}"
                        }
                        return
                    }

                    // 정상 응답: { "task_id": "..." }
                    val taskId = try {
                        val json = org.json.JSONObject(bodyStr ?: "{}")
                        json.optString("task_id", "")
                    } catch (e: Exception) {
                        ""
                    }

                    if (taskId.isBlank()) {
                        runOnUiThread {
                            showLoading(false)
                            binding.tvResult.text = "task_id를 받지 못했습니다. 응답: ${bodyStr ?: "없음"}"
                        }
                        return
                    }

                    // UI 갱신 후 결과 폴링 시작
                    runOnUiThread {
                        binding.tvResult.text = "작업 접수됨..."
                    }
                    fetchOnlineResult(taskId, attempt = 0) // 결과 폴링 시작
                }
            })
        }
    }


    // task_id로 결과 조회(GET) + 폴링
    // ── task_id로 결과 조회(GET) 후 status 확인 → 완료되면 result만 표시 ──
    private fun fetchOnlineResult(taskId: String, attempt: Int) {

        requestWithTokenRetry { accessHeader ->

            val request = Request.Builder()
                .url(onlineResultUrlPrefix + taskId)
                .addHeader("Authorization", accessHeader) // ★ 토큰 포함
                .get()
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {

                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    runOnUiThread {
                        showLoading(false)
                        binding.tvResult.text = "결과 조회 실패: ${e.message ?: "알 수 없는 오류"}"
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val bodyStr = response.body?.string()

                    // 401이면 토큰 갱신 후 재시도
                    if (response.code == 401) {
                        TokenManager.refreshAccessToken(
                            context = this@TranslateActivity,
                            onSuccess = { fetchOnlineResult(taskId, attempt) },
                            onFailure = {
                                runOnUiThread {
                                    showLoading(false)
                                    TokenManager.forceLogout(this@TranslateActivity)
                                }
                            }
                        )
                        return
                    }

                    if (!response.isSuccessful) {
                        runOnUiThread {
                            showLoading(false)
                            binding.tvResult.text = "결과 조회 실패(${response.code}): ${bodyStr ?: "서버 응답 없음"}"
                        }
                        return
                    }

                    // 예상 응답:
                    // {
                    //   "status": "completed" | "processing" | "failed",
                    //   "progress": 100,
                    //   "result": "찰리와 초콜릿 공장"
                    // }
                    val (status, resultText) = try {
                        val json = org.json.JSONObject(bodyStr ?: "{}")
                        val s = json.optString("status", "")
                        val r = json.optString("result", "")
                        s to r
                    } catch (e: Exception) {
                        "failed" to ""
                    }

                    // 처리 중 → 폴링 재시도
                    if (status == "processing") {
                        if (attempt + 1 >= pollMaxAttempts) {
                            runOnUiThread {
                                showLoading(false)
                                binding.tvResult.text = "처리가 지연되고 있습니다. 나중에 다시 확인해주세요. (task_id: $taskId)"
                            }
                        } else {
                            Handler(Looper.getMainLooper()).postDelayed({
                                fetchOnlineResult(taskId, attempt + 1)
                            }, pollIntervalMs)
                        }
                        return
                    }

                    // 완료 → result만 출력
                    if (status == "completed") {
                        runOnUiThread {
                            showLoading(false)
                            //result만 화면에 표시
                            binding.tvResult.text = if (resultText.isNotBlank()) resultText else "결과 없음"
                        }
                        return
                    }

                    // 실패/알수없음
                    runOnUiThread {
                        showLoading(false)
                        binding.tvResult.text = "오류가 발생했습니다. (status: $status)"
                    }
                }
            })
        }
    }
}