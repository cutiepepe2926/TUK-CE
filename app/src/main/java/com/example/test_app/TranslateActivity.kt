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