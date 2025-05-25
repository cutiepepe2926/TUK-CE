package com.example.test_app

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

// 사용자 인증 관련 API를 정의 (Retrofit 라이브러리 기준)
interface AuthService {
    // 1. 회원가입 요청
    @POST("users/signup/")
    fun registerUser(@Body request: SignupRequest): Call<SignupResponse>
    // - @Body: SignupRequest 객체를 JSON 형태로 변환하여 서버에 전송
    // - 서버 응답은 SignupResponse 형태로 받음


    // 2. 로그인 요청 (x-www-form-urlencoded)
    @FormUrlEncoded
    @POST("users/login/")
    fun loginUser(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<LoginResponse>
    // - @FormUrlEncoded: 키-값 쌍을 form-data 형식으로 서버에 전송
    // - 서버는 access, refresh 토큰이 포함된 LoginResponse 객체를 반환

    // 3. access Token 새로 요청
    @POST("users/token/refresh/")
    fun refreshAccessToken(@Body body: RequestBody): Call<ResponseBody>
    // - POST 요청으로 JSON 형태의 { "refresh": "..." } 데이터 전송
    // - 서버는 새로운 access 토큰을 반환 (ResponseBody 형태로 받음)

}
