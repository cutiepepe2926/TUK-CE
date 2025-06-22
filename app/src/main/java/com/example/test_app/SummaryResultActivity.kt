package com.example.test_app

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SummaryResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_summary_result)

        // 이전 Activity에서 전달된 데이터 가져오기
        val resultText = intent.getStringExtra("summary_result") ?: "결과 없음"

        val textView = findViewById<TextView>(R.id.tvSummaryResult)

        textView.text = resultText // TextView에 결과 텍스트 출력


        // 복사 버튼 처리
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("요약 결과", textView.text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(this, "복사되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}