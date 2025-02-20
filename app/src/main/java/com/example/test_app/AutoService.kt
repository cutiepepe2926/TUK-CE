package com.example.test_app

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

// 인터페이스
interface AuthService {
    // 회원가입 요청
    @POST("accounts/register/")
    fun registerUser(@Body request: SignupRequest): Call<SignupResponse> // ✅ 변경된 응답 타입


    // ✅ 로그인 요청 (x-www-form-urlencoded)
    @FormUrlEncoded
    @POST("accounts/login/")
    fun loginUser(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse> // 서버 응답을 받을 데이터 클래스 필요
}
