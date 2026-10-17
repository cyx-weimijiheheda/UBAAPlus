plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.kotlinSerialization)
}

kotlin { jvmToolchain(21) }

// 可选本地样本：GsmisScheduleSampleTest 等通过系统属性/gradle 属性读取，默认跳过
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

// 纯 JVM 库：存放前后端共用的 DTO、API 契约与上游客户端。
// Android 端（shared）与后端（server）都依赖本模块。
// 注意：只能放不依赖 Android API 的代码——不要从这里引用 Android 专有类，
// 也不要放 storage 相关实现（multiplatform-settings-no-arg 在纯 JVM 下会解析到
// java.util.prefs，Android 没有该包，运行期会崩溃）。
dependencies {
  api(libs.kotlinx.serialization.json)
  api(libs.kotlinx.datetime)
  api(libs.ktor.client.core)
  implementation(libs.kotlinx.coroutinesCore)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.client.logging)

  testImplementation(libs.kotlin.testJunit)
  testImplementation(libs.junit)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.kotlinx.coroutines.test)
}
