package com.example.test_app

// 로그인 요청에 대한 서버의 응답을 담는 데이터 클래스
data class LoginResponse(
    val access: String,  // 인증 토큰 (JWT - 주로 API 요청 시 Authorization 헤더에 사용됨)
    val refresh: String  // 재요청 토큰 (access 토큰 만료 시 새로운 access 토큰을 받기 위해 사용)
)
