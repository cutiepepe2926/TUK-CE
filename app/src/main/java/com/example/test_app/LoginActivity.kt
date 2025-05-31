package com.example.test_app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.test_app.databinding.ActivityLoginBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.content.edit
import androidx.core.graphics.toColorInt

//로그인 화면을 담당하는 액티비티
@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var binding : ActivityLoginBinding // ActivityLogin 바인딩 선언

    private lateinit var sharedPreferences: SharedPreferences // 토큰 저장용 (auth_prefs)

    private var isPasswordHidden = false // 비밀번호 표시 상태 토글 변수


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)

        // 토큰 저장용 (auth_prefs)
        sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

        // 비밀번호 저장 체크 여부 처리
        val rememberPassword = binding.loginPassword.text.toString()
        val rememberCheck = binding.rememberPasswd

        if (rememberCheck.isChecked) {
            // 체크되어 있으면 비밀번호 저장
            val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("saved_password", rememberPassword) }
        } else {
            // 체크 안되어 있으면 비밀번호 제거
            val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            prefs.edit { remove("saved_password") }
        }

        //// 저장된 비밀번호 자동 입력
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val savedPassword = prefs.getString("saved_password", "")
        if (!savedPassword.isNullOrEmpty()) {
            binding.loginPassword.setText(savedPassword)
            rememberCheck.isChecked = true // 체크 상태도 자동 설정
        }


        setContentView(binding.root)

        // 로그인 버튼 클릭 시
        binding.btnLogin.setOnClickListener {
            val username = binding.loginId.text.toString().trim()
            val password = binding.loginPassword.text.toString().trim()

            // 유효성 검사
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 로그인 요청 함수 호출
            this::loginUser.invoke(username, password)
        }

        // 회원가입 버튼 클릭 시 회원가입 화면으로 이동
        binding.btnSignup.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }

        // 뒤로가기 버튼 (X 아이콘)
        binding.x.setOnClickListener {
            super.onBackPressed()
        }

        // 비밀번호 보기/숨김 버튼
        binding.hide.setOnClickListener {

            // 비밀번호 보이게 설정
            if (isPasswordHidden) {
                binding.loginPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.hide.setBackgroundTintList(ColorStateList.valueOf(Color.BLACK)) // 아이콘 색 변경
                binding.Text4.text = "보기"
            }

            else {
                // 비밀번호 숨김 설정
                binding.loginPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.hide.setBackgroundTintList(ColorStateList.valueOf("#D8D8D8".toColorInt())) // 원래 색으로
                binding.Text4.text = "숨김"
            }
            isPasswordHidden = !isPasswordHidden // 상태 반전
        }

    }

    // 로그인 요청 함수
    private fun loginUser(username: String, password: String) {
        val call = RetrofitClient.authService.loginUser(username, password) // Retrofit 함수 호출

        call.enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {

                if (response.isSuccessful) {

                    val loginResponse = response.body()

                    if (loginResponse != null) {

                        // 로그인 성공 시 access/refresh 토큰 저장
                        saveTokens(loginResponse.access, loginResponse.refresh)

                        // 응답 검사 체크
                        println("로그인 성공! Access Token: ${loginResponse.access}")
                        println("로그인 성공! Refresh Token: ${loginResponse.refresh}")
                        Toast.makeText(this@LoginActivity, "로그인 성공!", Toast.LENGTH_SHORT).show()

                        // 로그인 성공 후 MainActivity로 이동
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    // 로그인 실패 시 에러 출력
                    println("🚨 로그인 실패: ${response.errorBody()?.string()}")
                    Toast.makeText(this@LoginActivity, "로그인 실패! 이메일과 비밀번호를 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                // 네트워크 오류 발생 시 출력
                println("🚨 네트워크 오류: ${t.message}")
                Toast.makeText(this@LoginActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // 토큰을 SharedPreferences에 저장하는 함수
    private fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
        }
    }
}