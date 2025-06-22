package com.example.test_app.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.test_app.LoginActivity
import com.example.test_app.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.core.content.edit

// 토큰 갱신 및 로그아웃 처리 담당
object TokenManager {

    // access 토큰 자동 갱신 함수
    fun refreshAccessToken(context: Context, onSuccess: (String) -> Unit, onFailure: () -> Unit) {

        // refresh token 가져오기
        val sharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val refreshToken = sharedPreferences.getString("refresh_token", null)

        // refresh 토큰이 없으면 실패 처리
        if (refreshToken.isNullOrEmpty()) {
            onFailure()
            return
        }

        // JSON 바디 생성
        val requestJson = JSONObject()
        requestJson.put("refresh", refreshToken)

        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        // Retrofit으로 서버에 갱신 요청
        val call = RetrofitClient.authService.refreshAccessToken(requestBody)

        // 응답 처리
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                
                // 응답 수신 성공 시
                if (response.isSuccessful) {
                    val body = response.body()?.string()

                    // // 새 access 토큰 추출
                    val newAccess = JSONObject(body ?: "").optString("access", null.toString())

                    if (!newAccess.isNullOrEmpty()) {
                        sharedPreferences.edit { putString("access_token", newAccess) }
                        onSuccess(newAccess)
                    } else {
                        onFailure()
                    }
                } else {
                    onFailure()
                }
            }

            // 실패 시 처리 함수
            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                onFailure()
            }
        })
    }

    // 2. 강제 로그아웃 처리 함수
    fun forceLogout(context: Context) {

        // 토큰 정보 초기화
        val sharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit { clear() }

        Toast.makeText(context, "토큰이 만료되어 다시 로그인해주세요", Toast.LENGTH_SHORT).show()


        // 로그인 화면으로 이동
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}
