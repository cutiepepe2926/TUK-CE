package com.example.test_app.network

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.test_app.LoginActivity
import com.example.test_app.RetrofitClient
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

class TokenAuthenticator(private val context: Context) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // 무한 루프 방지

        val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null) ?: return null

        // 🔄 토큰 갱신 요청 (blocking 방식)
        val refreshResponse = runBlocking {
            val json = JSONObject().put("refresh", refreshToken)
            val body = json.toString().toRequestBody("application/json".toMediaType())
            RetrofitClient.authService.refreshAccessToken(body).execute()
        }

        if (refreshResponse.isSuccessful) {
            val body = refreshResponse.body()?.string()
            val newAccessToken = JSONObject(body ?: "").optString("access", null)

            if (!newAccessToken.isNullOrEmpty()) {
                // ✅ 새로운 access_token 저장
                prefs.edit().putString("access_token", newAccessToken).apply()

                // ✅ 원래 요청에 새 토큰 붙여 재시도
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
            }
        }

        // ❌ 실패 시 로그아웃 처리
        prefs.edit().clear().apply()
        Toast.makeText(context, "토큰 만료. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)

        return null
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
