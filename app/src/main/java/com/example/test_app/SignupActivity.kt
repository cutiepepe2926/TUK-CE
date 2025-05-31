package com.example.test_app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivitySignupBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.graphics.toColorInt

// 회원가입 화면을 담당하는 액티비티
@Suppress("DEPRECATION")
class SignupActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySignupBinding // ActivitySignup 바인딩 객체

    private var isPasswordHidden = true // 비밀번호 숨김 여부 변수

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignupBinding.inflate(layoutInflater)

        setContentView(binding.root)


        // 회원가입 버튼 클릭 시
        binding.btnSignup.setOnClickListener {
            val username = binding.signupId.text.toString().trim() // 아이디 입력값
            val email = binding.signupEmail.text.toString().trim() // 이메일 입력값
            val password = binding.signupPassword.text.toString().trim() // 비밀번호 입력값

            // 입력값 유효성 검사
            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "모든 정보를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 서버로 회원가입 요청
            registerUser(username, email, password)
        }


        // 로그인 화면으로 이동 버튼 클릭 시
        binding.backtologin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // 현재 화면 종료
        }

        // 뒤로가기 버튼 (X 아이콘)
        binding.x.setOnClickListener {
            super.onBackPressed()
        }

        // 비밀번호 보기/숨김 버튼
        binding.hide.setOnClickListener {

            // 비밀번호 보이게 설정
            if (isPasswordHidden) {
                binding.signupPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.hide.setBackgroundTintList(ColorStateList.valueOf(Color.BLACK)) // 아이콘 색 변경
                binding.Text4.text = "보기"
            }

            else {
                // 비밀번호 숨김 설정
                binding.signupPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.hide.setBackgroundTintList(ColorStateList.valueOf("#D8D8D8".toColorInt())) // 원래 색으로
                binding.Text4.text = "숨김"
            }
            isPasswordHidden = !isPasswordHidden // 상태 반전
        }
    }

    // 회원가입 요청 함수 (POST 방식)
    private fun registerUser(username: String, email: String, password: String) {
        val request = SignupRequest(username, email, password) // JSON 변환용 데이터 생성
        val call = RetrofitClient.authService.registerUser(request) // Retrofit으로 API 요청


        // 비동기 네트워크 요청 처리
        call.enqueue(object : Callback<SignupResponse> {

            // 서버 응답 수신
            override fun onResponse(call: Call<SignupResponse>, response: Response<SignupResponse>) {
                if (response.isSuccessful) {

                    // 서버에서 보낸 메시지 출력
                    val message = response.body()?.message ?: "회원가입 성공!"
                    println("서버 응답: $message")
                    Toast.makeText(this@SignupActivity, message, Toast.LENGTH_SHORT).show()

                    // 로그인 화면으로 이동
                    val intent = Intent(this@SignupActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }

                else {
                    // 오류 응답 처리
                    println("🚨 회원가입 실패: ${response.errorBody()?.string()}")
                    Toast.makeText(this@SignupActivity, "회원가입 실패!", Toast.LENGTH_SHORT).show()
                }
            }

            // 네트워크 오류 등 요청 실패 시
            override fun onFailure(call: Call<SignupResponse>, t: Throwable) {
                println("🚨 네트워크 오류: ${t.message}")
                Toast.makeText(this@SignupActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
            }
        })
    }

}