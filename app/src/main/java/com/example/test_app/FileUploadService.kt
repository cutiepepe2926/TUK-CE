package com.example.test_app

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

// 파일 업로드/결과 요청 처리
interface FileUploadService {

    // 1. 음성(STT) 파일 업로드
    @Multipart
    @POST("stt/")
    fun uploadFile(
        @Header("Authorization") authToken: String, // Bearer access 토큰
        @Part file: MultipartBody.Part // 음성 파일 (ex: mp3, wav)
    ): Call<ResponseBody>

    // 2. STT 결과 조회
    @GET("stt/result/{task_id}/")
    fun getSttResult(
        @Header("Authorization") authToken: String, // Bearer access 토큰
        @Path("task_id") taskId: String // 업로드 시 받은 task_id
    ): Call<ResponseBody>

    // 3. PDF 파일 요약 요청 (문서 요약)
    @Multipart
    @POST("summarize/pdf/")
    fun uploadPdfFileWithPageRange(
        @Header("Authorization") authToken: String, // Bearer access 토큰
        @Part file: MultipartBody.Part, // PDF 파일
        @Part("start_page") startPage: RequestBody, // 시작 페이지 (문자열로 전송)
        @Part("end_page") endPage: RequestBody // 종료 페이지 (문자열로 전송)
    ): Call<ResponseBody>

    // 4. 요약 결과 조회
    @GET("summarize/result/{task_id}/")
    fun getSummarizeResult(
        @Header("Authorization") authToken: String, // Bearer access 토큰
        @Path("task_id") taskId: String  // 업로드 시 받은 task_id
    ): Call<ResponseBody>

}
