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

class LoginActivity : AppCompatActivity() {

    private lateinit var binding : ActivityLoginBinding

    //토큰 저장용
    private lateinit var sharedPreferences: SharedPreferences

    private var isPasswordHidden = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        sharedPreferences = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val rememberPassword = binding.loginPassword.text.toString()
        val rememberCheck = binding.rememberPasswd

        if (rememberCheck.isChecked) {
            val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("saved_password", rememberPassword).apply()
        } else {
            val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("saved_password").apply()
        }

        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val savedPassword = prefs.getString("saved_password", "")
        if (!savedPassword.isNullOrEmpty()) {
            binding.loginPassword.setText(savedPassword)
            rememberCheck.isChecked = true // 체크 상태도 자동 설정
        }


        setContentView(binding.root)

        // 로그인 버튼 클릭 시 (일단 Toast 메시지)
        binding.btnLogin.setOnClickListener {
            val username = binding.loginId.text.toString().trim()
            val password = binding.loginPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 로그인 요청
            this::loginUser.invoke(username, password) // ✅ 올바른 참조 방식으로 변경
        }

        // 회원가입 버튼 클릭 시 회원가입 화면으로 이동
        binding.btnSignup.setOnClickListener {
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
        }

        binding.x.setOnClickListener {
            super.onBackPressed()
        }

        binding.hide.setOnClickListener {
            if (isPasswordHidden) {
                binding.loginPassword.inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.hide.setBackgroundTintList(ColorStateList.valueOf(Color.BLACK)) // 아이콘 색 변경
                binding.Text4.text = "보기"
            }
            else {
                binding.loginPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.hide.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#D8D8D8"))) // 원래 색으로
                binding.Text4.text = "숨김"
            }
            isPasswordHidden = !isPasswordHidden
        }

    }

    // ✅ 로그인 요청 함수
    private fun loginUser(username: String, password: String) {
        val call = RetrofitClient.authService.loginUser(username, password) // ✅ 수정된 코드

        call.enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful) {
                    val loginResponse = response.body()
                    if (loginResponse != null) {
                        // ✅ 토큰 저장
                        saveTokens(loginResponse.access, loginResponse.refresh)

                        println("✅ 로그인 성공! Access Token: ${loginResponse.access}")
                        println("✅ 로그인 성공! Refresh Token: ${loginResponse.refresh}") // 추가된 부분
                        Toast.makeText(this@LoginActivity, "로그인 성공!", Toast.LENGTH_SHORT).show()

                        // 로그인 성공 후 MainActivity로 이동
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    println("🚨 로그인 실패: ${response.errorBody()?.string()}")
                    Toast.makeText(this@LoginActivity, "로그인 실패! 이메일과 비밀번호를 확인하세요.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                println("🚨 네트워크 오류: ${t.message}")
                Toast.makeText(this@LoginActivity, "네트워크 오류 발생!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // ✅ 토큰 저장 함수
    private fun saveTokens(accessToken: String, refreshToken: String) {
        val editor = sharedPreferences.edit()
        editor.putString("access_token", accessToken)
        editor.putString("refresh_token", refreshToken)
        editor.apply()
    }
}