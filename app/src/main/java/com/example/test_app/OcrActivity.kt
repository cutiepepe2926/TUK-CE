package com.example.test_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.test_app.databinding.ActivityOcrBinding
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class OcrActivity : AppCompatActivity() {

    private lateinit var binding : ActivityOcrBinding

    private val STORAGEPERMISSION = Manifest.permission.READ_MEDIA_IMAGES

    private lateinit var btnAddImage: Button
    private lateinit var btnProcessImage: Button
    private lateinit var btnTranslate: Button // 📌 번역 버튼 추가
    private lateinit var ivImage: ImageView
    private lateinit var tvImageText: TextView // OCR 결과
    private lateinit var tvTranslatedText: TextView // 📌 번역 결과 표시할 TextView

    private lateinit var readImageText: ReadImageText
    private lateinit var translator: Translator // 📌 번역기 객체

    private val imageChose = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            ivImage.setImageURI(it.data?.data)
        }
    }

    // 이미지 선택
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            startCropActivity(it) // 선택된 이미지를 크롭 액티비티로 전달
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        // 시스템에서 연결된 네트워크 객체 가져오기
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 현재 활성 네트워크가 없으면 false 반환
        val network = connectivityManager.activeNetwork ?: return false
        // 활성 네트워크의 세부 기능 가져오기
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // 해당 네트워크가 인터넷 연결 기능을 가지고 있으면 true
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 크롭 화면 실행
    private fun startCropActivity(uri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "croppedImage_${System.currentTimeMillis()}.jpg"))
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setCompressionQuality(100)
        }
        val cropIntent = UCrop.of(uri, destinationUri)
            .withOptions(options)
            .withAspectRatio(1f, 1f)
            .getIntent(this)

        cropActivityResult.launch(cropIntent)
    }

    // 크롭된 이미지 처리
    private fun processCroppedImage(uri: Uri) {
        ivImage.setImageDrawable(null) // 기존 이미지 제거
        ivImage.setImageURI(uri) // 크롭된 이미지 표시
    }

    // 크롭 결과
    private val cropActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val resultUri = UCrop.getOutput(result.data!!)
                if (resultUri != null) {
                    processCroppedImage(resultUri) // 크롭된 이미지를 처리
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        binding = ActivityOcrBinding.inflate(layoutInflater)

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        btnAddImage = findViewById(R.id.btnAdd)
        btnProcessImage = findViewById(R.id.btnProp)
        btnTranslate = findViewById(R.id.btnTranslate) // 📌 번역 버튼
        ivImage = findViewById(R.id.ivSource)
        tvImageText = findViewById(R.id.tvResult)

        // 📌 번역 결과 표시할 TextView 추가
        tvTranslatedText = findViewById(R.id.tvTranslatedResult) // 번역 결과
        //tvTranslatedText = TextView(this)
        tvTranslatedText.text = "번역된 텍스트:"
        tvTranslatedText.textSize = 18f


        // OCR 객체 초기화
        readImageText = ReadImageText()

        btnAddImage.setOnClickListener {
            imagePicker.launch("image/*") // 이미지 선택
        }


        // Make OCR 버튼 클릭 리스너
        btnProcessImage.setOnClickListener {
            val bitmap = (ivImage.drawable as? BitmapDrawable)?.bitmap
            bitmap?.let {
                lifecycleScope.launch {
                    readImageText.processImage(it) { result ->
                        tvImageText.text = result // OCR 결과 표시
                    }
                }
            }
        }
        // 📌 번역 버튼 클릭 리스너 추가
        btnTranslate.setOnClickListener {
//
//            if (isNetworkAvailable(this)) {
//                // 온라인 번역기 사용
//            } else {
//                // 오프라인 ML Kit 번역기 사용
//            }
//
//
//            val textToTranslate = tvImageText.text.toString()
//            if (textToTranslate.isNotEmpty()) {
//                translateText(textToTranslate)
//            }
//            if (textToTranslate.isNotEmpty()) {
//
//            }
        }

        // 📌 번역기 초기화
        initializeTranslator()

    }

    // 📌 번역기 설정 함수 (한국어 → 영어)
    private fun initializeTranslator() {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH) // ✅ 원본: 영어
            .setTargetLanguage(TranslateLanguage.KOREAN) // ✅ 번역 대상: 한국어
            .build()

        translator = Translation.getClient(options)

        // 📌 번역 모델 다운로드 (오프라인 사용 가능하도록)
        translator.downloadModelIfNeeded()
            .addOnSuccessListener { println("✅ 번역 모델 다운로드 완료!") }
            .addOnFailureListener { println("🚨 번역 모델 다운로드 실패: ${it.message}") }
    }



    override fun onResume() {
        super.onResume()

        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, STORAGEPERMISSION)

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, STORAGEPERMISSION)) {
                ActivityCompat.requestPermissions(this, arrayOf(STORAGEPERMISSION), 0)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        } else {
            readImageText = ReadImageText() // ML Kit을 사용하는 ReadImageText 초기화
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        readImageText.close() // OCR 인식기 종료하여 메모리 해제
        translator.close() // 번역기 리소스 해제
    }
}