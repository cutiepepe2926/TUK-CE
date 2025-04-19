plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.test_app"
    compileSdk = 35


    //뷰 바인딩 설정
    viewBinding {
        enable = true
    }

    defaultConfig {
        applicationId = "com.example.test_app"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    // 필기 어플 라이브러리
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.language.id.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // PDF 렌더링 라이브러리 (예: Barteksc의 android-pdf-viewer)
    //implementation("com.github.mhiew:AndroidPdfViewer:3.1.0-beta.1")
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")
    // (빈 PDF 생성, PDF 합성 시 iText / PdfBox 등 추가 가능)
    implementation("com.itextpdf:itextpdf:5.0.6")
    // JSON 파싱 (Gson)
    implementation ("com.google.code.gson:gson:2.8.8")
    //BottomSheetDialog 라이브러리
    implementation ("com.google.android.material:material:1.10.0")


    // 명시적 버전으로 androidx 라이브러리 설정(OCR 설정)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.10.1") // compileS1dkVersion 34과 호환
    implementation("androidx.activity:activity:1.7.2") // compileSdkVersion 34과 호환
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1") // compileSdkVersion 34과 호환되는 생명주기


    // HTTP 통신 라이브러리
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.3")



    // 테스트 라이브러리
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // 라틴 문자 (영어 등) 인식
    implementation ("com.google.mlkit:text-recognition:16.0.1")

    // 한국어 문자 인식
    implementation ("com.google.mlkit:text-recognition-korean:16.0.1")

    // 이미지 크롭
    implementation ("com.github.yalantis:ucrop:2.2.10")

    // ML Kit 번역 라이브러리 추가
    implementation ("com.google.mlkit:translate:17.0.2")


}