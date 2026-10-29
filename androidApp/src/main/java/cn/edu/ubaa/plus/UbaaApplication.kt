package cn.edu.ubaa.plus

import android.app.Application
import cn.edu.ubaa.plus.api.storage.LegacyCredentialMigration
import cn.edu.ubaa.plus.api.storage.SecureSettings
import cn.edu.ubaa.plus.ui.common.components.UbaaToast

/**
 * 应用入口。
 *
 * 需要在进程启动时尽早初始化加密存储——凭据与 token 的读取都依赖它。 随后把旧版本明文保存的凭据迁移进加密存储，用户无需重新登录。
 */
class UbaaApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    SecureSettings.initialize(this)
    UbaaToast.initialize(this)
    LegacyCredentialMigration.migrate(this)
    AppForegroundMonitor.start()
  }
}
