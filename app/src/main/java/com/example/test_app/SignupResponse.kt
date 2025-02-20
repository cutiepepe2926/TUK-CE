package com.example.test_app

data class SignupResponse(
    val message: String // 서버에서 반환하는 JSON의 필드명과 맞춰야 함. 안했더니 오류생겼음
)
