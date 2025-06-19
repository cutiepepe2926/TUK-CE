package com.example.test_app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SummaryResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary_result)

        val resultText = intent.getStringExtra("summary_result") ?: "결과 없음"
        val textView = findViewById<TextView>(R.id.tvSummaryResult)
        textView.text = resultText
    }
}