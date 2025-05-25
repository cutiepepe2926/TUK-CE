package com.example.test_app

// 회원가입 요청 시 서버에 전송할 데이터를 담는 데이터 클래스
data class SignupRequest(
    val username: String,
    val email: String,
    val password: String
)
