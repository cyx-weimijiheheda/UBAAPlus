import java.util.Properties

plugins {
  // AGP 9 起内置 Kotlin 支持，不再需要（也不能）应用 org.jetbrains.kotlin.android
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

val composeVersion = libs.versions.composeMultiplatform.get()
val composeMaterial3Version = "1.9.0"
val composeMaterialIconsExtendedVersion = "1.7.3"

// 读取根目录下的 .env 文件以配置编译时常量
val env =
    Properties().apply {
      val envFile = rootProject.file(".env")
      if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
      }
    }

// 优先从 .env 或环境变量获取 API 完整地址
val apiEndpoint: String =
    env.getProperty("API_ENDPOINT") ?: System.getenv("API_ENDPOINT") ?: "https://ubaa.mofrp.top"

tasks.withType<Test>().configureEach {
  val sample =
      providers
          .gradleProperty("graduateScheduleSample")
          .orElse(providers.environmentVariable("UBAA_GRADUATE_SCHEDULE_SAMPLE"))
  if (sample.isPresent) {
    inputs.file(sample).withPropertyName("graduateScheduleSample")
    environment("UBAA_GRADUATE_SCHEDULE_SAMPLE", sample.get())
  }
  val gsmisSample =
      providers
          .gradleProperty("gsmisScheduleSampleDir")
          .orElse(providers.environmentVariable("UBAA_GSMIS_SCHEDULE_SAMPLE_DIR"))
  if (gsmisSample.isPresent) {
    inputs.dir(gsmisSample).withPropertyName("gsmisScheduleSampleDir")
    systemProperty("gsmisSampleDir", gsmisSample.get())
  }
}

android {
  namespace = "cn.edu.ubaa.plus.shared"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()

    // 等价于原 BuildKonfig 插件生成的字段，见 cn.edu.ubaa.plus.BuildKonfig
    buildConfigField("String", "APP_VERSION", "\"${project.property("project.version")}\"")
    buildConfigField("String", "API_ENDPOINT", "\"$apiEndpoint\"")
  }

  buildFeatures { buildConfig = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

dependencies {
  // 前后端共用契约（DTO、上游客户端）
  implementation(projects.core)

  // Compose UI
  implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
  implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
  implementation("org.jetbrains.compose.material:material:$composeVersion")
  implementation("org.jetbrains.compose.material3:material3:$composeMaterial3Version")
  implementation("org.jetbrains.compose.ui:ui:$composeVersion")
  implementation(
      "org.jetbrains.compose.material:material-icons-extended:$composeMaterialIconsExtendedVersion"
  )
  implementation("org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewmodelCompose)
  implementation(libs.androidx.lifecycle.runtimeCompose)
  implementation(libs.androidx.lifecycle.process)

  // 业务与网络
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.datetime)
  implementation(libs.kotlinx.coroutinesCore)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.client.logging)
  implementation(libs.ktor.client.auth)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.coil3.compose)
  implementation(libs.coil3.network.ktor)
  implementation(libs.multiplatform.settings)
  implementation(libs.multiplatform.settings.no.arg)
  // 智慧教室扫码签到
  implementation(libs.zxing.android.embedded)

  // src/debug 下的测试夹具 TestSettings 需要 MapSettings（仅存在于 -test 构件），只进 debug 产物
  debugImplementation(libs.multiplatform.settings.test)

  // Android 单元测试跑在 JUnit4 上，需要 kotlin-test-junit 才能解析 kotlin.test.* 注解
  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.junit)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.multiplatform.settings.test)
}
