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
  id("com.apollographql.apollo") version "4.3.1"
}

android {
  namespace = "com.awish.assistant"
  compileSdk = libs.versions.compile.sdk.get().toInt()

  defaultConfig {
    applicationId = "com.awish.assistant"
    minSdk = 24
    @Suppress("OldTargetApi") // 保留你原有注释用途
    targetSdk = 34
    versionCode = 5
    versionName = "1.2.1"

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
    buildConfig = true
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

apollo {
  service("service") {
    srcDir("src/main/graphql")
    packageName.set("com.awish.generated")

    // https://www.apollographql.com/docs/kotlin/advanced/plugin-recipes#downloading-a-schema
    introspection {
      endpointUrl.set(keyProperties["apolloEndpoint"] as String?)
      schemaFile.set(file("app/src/main/graphql/graphql.schema.graphqls"))

      headers.putAll(
        providers.provider {
          mapOf(
            "Authorization" to "Bearer ${keyProperties["apollpToken"] as String?}",
            "Appid" to keyProperties["apollpAppid"] as String?
          )
        }
      )
    }
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

  // [apollo](https://www.apollographql.com/docs/kotlin#contributing)
  implementation(libs.apollo.runtime)
  // Optional: if you want to use the normalized cache
  implementation("com.apollographql.apollo:apollo-normalized-cache-sqlite:4.3.1")
  // Optional: if you just want the generated models and parsers and write your own HTTP code/cache code, you can remove apollo-runtime
  // and use apollo-api instead
  implementation("com.apollographql.apollo:apollo-api:4.3.1")

}
