package cn.edu.ubaa.plus.api.storage

import android.content.Context
import com.russhwolf.settings.Settings

/**
 * 基于 Android Keystore 的加密 Settings 单例。
 *
 * 凭据（学号密码）和 token 若走 multiplatform-settings-no-arg 的默认实现，在 Android 上 就是**未加密**的
 * SharedPreferences；设备被 root 或从备份中提取即可读出明文密码。
 *
 * 这里改用 [KeystoreSettings]：主密钥保存在 Android Keystore（不可导出）， 值用 AES-256-GCM 加密、键名用确定性加密。
 */
object SecureSettings {
  private const val PREFS_NAME = "ubaa_secure_prefs"

  @Volatile private var appContext: Context? = null
  @Volatile private var cached: Settings? = null

  /** 必须在 Application.onCreate 中调用一次。 */
  fun initialize(context: Context) {
    appContext = context.applicationContext
  }

  fun get(): Settings {
    cached?.let {
      return it
    }
    return synchronized(this) { cached ?: create().also { cached = it } }
  }

  private fun create(): Settings {
    val context =
        appContext
            ?: error(
                "SecureSettings 尚未初始化。请在 Application.onCreate 中调用 " +
                    "SecureSettings.initialize(this)。"
            )
    return KeystoreSettings(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))
  }
}
