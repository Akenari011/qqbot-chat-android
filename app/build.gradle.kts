// 设计参考 Telegram for Android (GPL-2.0)
// 本文件为 :app 模块构建脚本。框架选型：Kotlin + Jetpack Compose + Material 3
// （TG Android 为 Java + XML 自定义 View + AppCompat；此处以声明式 UI 对标其视觉规范）。
// 来自 TG 的常量：无（构建脚本不含视觉常量）。
// 注意：根 build.gradle.kts 未声明 org.jetbrains.kotlin.plugin.compose 且本任务禁止改动根文件，
// 因此这里显式写出 2.0.21 版本（与根 org.jetbrains.kotlin.android 版本一致）。
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

// 正式签名：仅当服务器本地 signing.properties 存在时启用；否则回退 debug 签名。
// 密钥文件与密码不进仓库（只在春川构建机 ~/android-build/ 下）。
import java.util.Properties

val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = signingProps.getProperty("QQBOT_STORE_FILE")?.let {
    rootProject.file(it).exists()
} ?: false

android {
    namespace = "org.example.qqbotchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.example.qqbotchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("QQBOT_STORE_FILE"))
                storePassword = signingProps.getProperty("QQBOT_STORE_PASSWORD")
                keyAlias = signingProps.getProperty("QQBOT_KEY_ALIAS")
                keyPassword = signingProps.getProperty("QQBOT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 有正式 keystore 时用它，否则回退 debug 签名（保证 APK 始终可安装）。
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // :core 提供 ChatMessage / Conversation / Credentials / MessageParser（及后续的
    // QQBotApi / GatewayClient / ConversationStore）。
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
