package com.example.test_app

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface FileUploadService {
    //STT파일 업로드
    @Multipart
    @POST("stt/")
    fun uploadFile(
        @Header("Authorization") authToken: String, // 🔹 Bearer 토큰 추가
        @Part file: MultipartBody.Part
    ): Call<ResponseBody> // 서버 응답을 처리

    //STT 결과 받기
    @GET("stt/result/{task_id}/")
    fun getSttResult(
        @Header("Authorization") authToken: String,
        @Path("task_id") taskId: String
    ): Call<ResponseBody>

    //요약파일 업로드
    @Multipart
    @POST("summarize/textfile/")
    fun uploadTextFile(
        @Header("Authorization") authToken: String,
        @Part file: MultipartBody.Part
    ): Call<ResponseBody>

    //요약 결과 받기
    @GET("summarize/result/{task_id}/")
    fun getSummarizeResult(
        @Header("Authorization") authToken: String,
        @Path("task_id") taskId: String
    ): Call<ResponseBody>

}
