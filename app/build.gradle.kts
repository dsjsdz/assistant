import java.util.Properties

var keyProperties = Properties().apply {
  val propFile = file("key.properties")
  if (propFile.exists()) {
    propFile.inputStream().use { load(it) }
  }
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)

  id("com.google.dagger.hilt.android")
  kotlin("kapt")
}

android {
  namespace = "com.awish.assistant"
  compileSdk = libs.versions.compile.sdk.get().toInt()

  defaultConfig {
    applicationId = "com.awish.assistant"
    minSdk = 24
    @Suppress("OldTargetApi") // 保留你原有注释用途
    targetSdk = 34
    versionCode = 4
    versionName = "1.2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // signing configs
  signingConfigs {
    create("release") {
      keyAlias = keyProperties["keyAlias"] as String?
      keyPassword = keyProperties["keyPassword"] as String?
      storeFile = (keyProperties["storeFile"] as String?)?.let { file(it) }
      storePassword = keyProperties["storePassword"] as String?
    }
  }

  buildTypes {
    getByName("release") {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.3"
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
  // add gson
  implementation(libs.gson)

  implementation(libs.material)

  // 核心 Hilt 依赖
  implementation(libs.hilt.android)
  kapt(libs.hilt.android.compiler)

  // Hilt 对 Jetpack 集成的支持
  implementation(libs.androidx.hilt.navigation.compose)
  kapt(libs.androidx.hilt.compiler)
  implementation(libs.androidx.material.icons.extended)
  implementation(libs.material3)

  // https://developer.android.com/develop/ui/compose/setup#kotlin_1
  implementation(libs.coil.compose) // 版本可以升级查看最新
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.appcompat)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  // https://github.com/licheedev/Modbus4Android
  implementation("com.github.licheedev:Modbus4Android:3.0.0") {
    exclude(group = "commons-logging", module = "commons-logging")
  }

  // rx全家桶
  implementation(libs.rxjava)
  implementation(libs.rxlifecycle.android)
  implementation("com.trello.rxlifecycle2:rxlifecycle-components:2.1.0") {
    exclude(group = "com.android.support", module = "support-compat")
  }

  // kotlin 协程
  implementation(libs.jetbrains.kotlinx.coroutines.android)

}
