import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

// APK 文件名前缀：androidApp-* -> UBAAPlus-*
base { archivesName.set("UBAAPlus") }

android {
  namespace = "cn.edu.ubaa.plus.android"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "cn.edu.ubaa.plus"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = project.property("project.version.code").toString().toInt()
    versionName = project.property("project.version").toString()
  }

  val localProperties =
      Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
          localPropertiesFile.inputStream().use { load(it) }
        }
      }

  signingConfigs {
    create("release") {
      val keyPath = localProperties.getProperty("SIGNING_KEY") ?: System.getenv("SIGNING_KEY")
      storeFile = keyPath?.let { file(it) }
      storePassword =
          localProperties.getProperty("SIGNING_STORE_PASSWORD")
              ?: System.getenv("SIGNING_STORE_PASSWORD")
      keyAlias =
          localProperties.getProperty("SIGNING_KEY_ALIAS") ?: System.getenv("SIGNING_KEY_ALIAS")
      keyPassword =
          localProperties.getProperty("SIGNING_KEY_PASSWORD")
              ?: System.getenv("SIGNING_KEY_PASSWORD")
    }
  }

  // 仅 CI 发布时开启：生成 universal / x86 / arm64-v8a 三个独立 APK。
  splits {
    abi {
      isEnable = providers.gradleProperty("abiSplits").orNull == "true"
      reset()
      include("x86", "arm64-v8a")
      isUniversalApk = true
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      val releaseSigning = signingConfigs.getByName("release")
      val isReleaseTask =
          gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
      if (isReleaseTask && System.getenv("CI") == "true" && releaseSigning.storeFile == null) {
        throw GradleException("CI release build requires SIGNING_* environment variables")
      }
      // 本地没有配置正式签名时退回 debug 签名；CI 会强制要求正式签名。
      signingConfig =
          if (releaseSigning.storeFile != null) releaseSigning
          else signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

// AGP 9 的 VariantOutput 公共 API 不暴露 outputFileName；
// 在 assembleRelease 结束后把默认产物同步为 <arch>_<version>.apk。
val releaseVersion = project.property("project.version").toString()
val renameReleaseApks by
    tasks.registering(Sync::class) {
      notCompatibleWithConfigurationCache("APK rename captures the build script version")
      from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("*.apk")
        rename { name ->
          val abi = name.removePrefix("UBAAPlus-").removeSuffix("-release.apk")
          if (abi == name) name else "UBAAPlus-${abi}_${releaseVersion}.apk"
        }
      }
      into(layout.buildDirectory.dir("outputs/apk/release-renamed"))
    }

tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(renameReleaseApks) }

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

dependencies {
  implementation(projects.shared)
  implementation(libs.androidx.activity.compose)
  // 原先使用的 compose.preview / compose.uiTooling 访问器已废弃，改为直接声明模块
  val composeVersion = libs.versions.composeMultiplatform.get()
  implementation("org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
  debugImplementation("org.jetbrains.compose.ui:ui-tooling:$composeVersion")
}
