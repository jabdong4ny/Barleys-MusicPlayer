import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.legacy.kapt) // Hilt kapt를 위해 필요 (AGP 9 built-in Kotlin)
    alias(libs.plugins.hilt.android)
}

// 릴리스 서명 정보: CI는 환경 변수로, 로컬은 루트의 keystore.properties(커밋 금지)로 넘깁니다.
// 둘 다 없으면 release 빌드는 서명되지 않은 APK로 나옵니다.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(propertyKey)

val releaseStoreFile = signingValue("storeFile", "RELEASE_KEYSTORE_PATH")

android {
    namespace = "com.barley.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.barley.musicplayer"
        minSdk = 26
        targetSdk = 36
        // CI는 태그(v1.2.3)에서 계산한 값을 -PappVersionCode / -PappVersionName 으로 넘깁니다.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = project.findProperty("appVersionName") as String? ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "RELEASE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/LICENSE*"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // 백그라운드 재생 (MediaSessionService + 알림 컨트롤)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // YouTube 검색/스트림 추출 (비공식 — YouTube 공식 API가 아님)
    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)

    // 썸네일 이미지 로딩
    implementation(libs.coil.compose)

    // 최근 검색어 저장
    implementation(libs.androidx.datastore.preferences)
}
