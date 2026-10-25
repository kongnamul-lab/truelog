plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.truelog"
    compileSdk = 35

    // Android Automotive 표준 차량 데이터(속도·기어)를 읽기 위한 라이브러리
    useLibrary("android.car")

    defaultConfig {
        applicationId = "app.truelog"
        minSdk = 28
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        buildConfigField("String", "VEHICLE_SDK_VERSION", "\"${libs.versions.vehicleSdk.get()}\"")
        // 휴대폰 리포트 보기 페이지 주소(임시 자리표시자). 익명 GitHub Pages에 report/report.html을 올리면 이 값만 바꾼다.
        buildConfigField("String", "REPORT_VIEWER_URL", "\"https://kongnamul-lab.github.io/truelog/report.html\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.zxing.core) // QR 코드 생성

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
}
