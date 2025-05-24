package com.example.test_app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivitySignupBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SignupActivity : AppCompatActivity() {

    private lateinit var binding : ActivitySignupBinding
    private var isPasswordHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignupBinding.inflate(layoutInflater)

        setContentView(binding.root)


        // 회원가입 버튼 클릭 시
        binding.btnSignup.setOnClickListener {
            val username = binding.signupId.text.toString().trim()
            val email = binding.signupEmail.text.toString().trim()
            val password = binding.signupPassword.text.toString().trim()

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

        binding.x.setOnClickListener {
            super.onBackPressed()
        }
    }

    // ✅ 회원가입 요청 함수 (POST 방식)
    private fun registerUser(username: String, email: String, password: String) {
        val request = SignupRequest(username, email, password) // JSON 변환용 데이터 생성
        val call = RetrofitClient.authService.registerUser(request) // ✅ AuthService 사용


        call.enqueue(object : Callback<SignupResponse> { // ✅ 응답 타입을 SignupResponse로 변경
            override fun onResponse(call: Call<SignupResponse>, response: Response<SignupResponse>) {
                if (response.isSuccessful) {
                    val message = response.body()?.message ?: "회원가입 성공!"
                    println("✅ 서버 응답: $message")
                    Toast.makeText(this@SignupActivity, message, Toast.LENGTH_SHORT).show()

                    // 로그인 화면으로 이동
                    val intent = Intent(this@SignupActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    println("🚨 회원가입 실패: ${response.errorBody()?.string()}")
                    Toast.makeText(this@SignupActivity, "회원가입 실패!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<SignupResponse>, t: Throwable) {
                println("🚨 네트워크 오류: ${t.message}")
                Toast.makeText(this@SignupActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
            }
        })
    }

}