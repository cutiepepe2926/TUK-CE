package com.example.test_app

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// RetrofitClient 객체
object RetrofitClient {

    // 기본 주소 설정
    private const val BASE_URL = "https://www.omniwrite.r-e.kr:8443/api/"

    // Retrofit 객체 생성
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // JSON 변환 자동 처리
            .build()
    }

    // 로그인 서비스 (AuthService)
    val authService: AuthService by lazy {
        retrofit.create(AuthService::class.java)
    }


    // 파일 업로드 서비스 (FileUploadService)
    val fileUploadService: FileUploadService by lazy {
        retrofit.create(FileUploadService::class.java)
    }
}
