package com.example.test_app

data class LoginResponse(
    val access: String,  // 인증 토큰
    val refresh: String  // 재요청 토큰
)
