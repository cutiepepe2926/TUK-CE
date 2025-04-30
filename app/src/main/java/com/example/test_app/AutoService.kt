package com.example.test_app

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

// 인터페이스
interface AuthService {
    // 회원가입 요청
    @POST("users/signup/")
    fun registerUser(@Body request: SignupRequest): Call<SignupResponse> // ✅ 변경된 응답 타입


    // ✅ 로그인 요청 (x-www-form-urlencoded)
    @FormUrlEncoded
    @POST("users/login/")
    fun loginUser(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse> // 서버 응답을 받을 데이터 클래스 필요

    // access Token 새로 요청
    @POST("users/token/refresh/")
    fun refreshAccessToken(@Body body: RequestBody): Call<ResponseBody>

}
