package cn.edu.ubaa.plus

import cn.edu.ubaa.plus.shared.BuildConfig

/**
 * 构建期常量。
 *
 * 原先由 BuildKonfig Gradle 插件生成；该插件与 AGP 9 的内置 Kotlin 不兼容，改为使用 AGP 的
 * `buildConfigField`，这里做一层转发，保持调用方（AppInfo / ApiClient / UpdateService）不变。
 */
object BuildKonfig {
  val VERSION: String = BuildConfig.APP_VERSION
  val API_ENDPOINT: String = BuildConfig.API_ENDPOINT
}
