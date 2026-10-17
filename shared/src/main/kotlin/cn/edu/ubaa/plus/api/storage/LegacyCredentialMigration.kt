package cn.edu.ubaa.plus.api.storage

import android.content.Context

/**
 * 把旧版本明文写在 SharedPreferences 里的凭据迁移到加密存储。
 *
 * 旧版本通过 multiplatform-settings-no-arg 使用默认的 `${packageName}_preferences`。
 * 升级后这些值不再被读取，若不处理，用户会发现"密码没记住"，同时明文密码仍留在设备上。
 *
 * 这里逐项迁移后从旧文件删除：迁移成功则用户无感知，文件里也不再保留明文。
 */
object LegacyCredentialMigration {
  private val SENSITIVE_KEYS =
      listOf(
          "saved_username",
          "saved_password",
          "auth_access_token",
          "auth_refresh_token",
          "auth_access_token_expires_at",
          "auth_refresh_token_expires_at",
      )

  fun migrate(context: Context) {
    val legacy =
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    val present = SENSITIVE_KEYS.filter(legacy::contains)
    if (present.isEmpty()) return

    val secure = SecureSettings.get()
    val editor = legacy.edit()
    for (key in present) {
      when (val value = legacy.all[key]) {
        is String -> secure.putString(key, value)
        is Int -> secure.putInt(key, value)
        is Long -> secure.putLong(key, value)
        is Float -> secure.putFloat(key, value)
        is Boolean -> secure.putBoolean(key, value)
        else -> Unit
      }
      editor.remove(key)
    }
    editor.apply()
  }
}
