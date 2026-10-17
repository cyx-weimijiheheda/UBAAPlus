plugins {
  // 定义所有子项目通用的插件，并设置 apply false
  // 这样做是为了避免插件在每个子项目的类加载器中被多次加载，从而优化构建性能。
  // 子项目会按需应用这些插件。

  // Android 应用程序插件，用于构建最终的 APK
  alias(libs.plugins.androidApplication) apply false
  // Android 库插件，用于构建可重用的 Android 库
  alias(libs.plugins.androidLibrary) apply false
  // Compose Multiplatform 核心插件，支持 Compose UI
  alias(libs.plugins.composeMultiplatform) apply false
  // Compose 编译器插件
  alias(libs.plugins.composeCompiler) apply false
  // Kotlin JVM 插件，用于纯 Java/Kotlin 后端或工具库
  alias(libs.plugins.kotlinJvm) apply false
  // Kotlin Android 插件，用于 Android 模块
  alias(libs.plugins.kotlinAndroid) apply false
  // Kotlin 序列化插件
  alias(libs.plugins.kotlinSerialization) apply false
  // Kover 插件，用于测试覆盖率报告
  alias(libs.plugins.kover)
  // Spotless 插件，用于统一 Kotlin/Gradle Kotlin DSL 代码格式
  alias(libs.plugins.spotless)
}

spotless {
  lineEndings = com.diffplug.spotless.LineEnding.UNIX

  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**", "tmp/**")
    ktfmt()
  }

  kotlinGradle {
    target("*.gradle.kts", "**/*.gradle.kts")
    targetExclude("**/build/**", "tmp/**")
    ktfmt()
  }
}
