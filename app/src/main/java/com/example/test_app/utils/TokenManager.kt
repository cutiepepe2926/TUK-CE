package com.example.test_app.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.test_app.LoginActivity
import com.example.test_app.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

object TokenManager {

    fun refreshAccessToken(context: Context, onSuccess: (String) -> Unit, onFailure: () -> Unit) {
        val sharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val refreshToken = sharedPreferences.getString("refresh_token", null)

        if (refreshToken.isNullOrEmpty()) {
            onFailure()
            return
        }

        val requestJson = JSONObject()
        requestJson.put("refresh", refreshToken)

        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            requestJson.toString()
        )

        val call = RetrofitClient.authService.refreshAccessToken(requestBody)

        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    val body = response.body()?.string()
                    val newAccess = JSONObject(body ?: "").optString("access", null)

                    if (!newAccess.isNullOrEmpty()) {
                        sharedPreferences.edit().putString("access_token", newAccess).apply()
                        onSuccess(newAccess)
                    } else {
                        onFailure()
                    }
                } else {
                    onFailure()
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                onFailure()
            }
        })
    }

    fun forceLogout(context: Context) {
        val sharedPreferences = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
        Toast.makeText(context, "토큰이 만료되어 다시 로그인해주세요", Toast.LENGTH_SHORT).show()
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        context.startActivity(intent)
    }
}
