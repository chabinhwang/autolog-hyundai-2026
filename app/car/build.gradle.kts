import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// 서명키와 암호는 Git에 포함하지 않는 로컬 파일에서만 읽는다.
val releaseSigningFile = rootProject.file("release-signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}

android {
    namespace = "com.autolog.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.autolog.app"
        // 공모전·Pleos 배포 요구사항: API 28 이상
        minSdk = 28
        // 공식 Vehicle SDK 샘플 앱 기준(compileSdk/targetSdk 35). Pleos_Connect_v2.1 AVD는 Android 14
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"https://autolog-api.aihub.io.kr\"")
        // P(주차) 유지 시간이 지나면 운행 종료로 확정 (car-app-rules.md: 3분)
        buildConfigField("int", "PARK_CONFIRM_SECONDS", "180")
    }

    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 에뮬레이터 → 호스트 로컬 서버. 시연·검증 시간을 줄이려고 종료 확정을 30초로 줄인다.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("int", "PARK_CONFIRM_SECONDS", "30")
        }
        create("remoteDebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".remote"
            versionNameSuffix = "-remote"
            matchingFallbacks += listOf("debug")
            // 운영 서버 검증은 별도 앱 데이터·기기 토큰을 사용한다.
            buildConfigField("String", "API_BASE_URL", "\"https://autolog-api.aihub.io.kr\"")
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // android.util.Log 등 프레임워크 호출이 JVM 단위 테스트에서 예외 대신 기본값을 돌려주게 한다.
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.fused.location)
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.speech.to.text)
    implementation(libs.pleos.text.to.speech)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
