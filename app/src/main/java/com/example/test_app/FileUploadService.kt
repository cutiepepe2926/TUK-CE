package com.example.test_app

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface FileUploadService {
    @Multipart
    @POST("stt/")
    fun uploadFile(
        @Header("Authorization") authToken: String, // 🔹 Bearer 토큰 추가
        @Part file: MultipartBody.Part
    ): Call<ResponseBody> // 서버 응답을 처리
}
