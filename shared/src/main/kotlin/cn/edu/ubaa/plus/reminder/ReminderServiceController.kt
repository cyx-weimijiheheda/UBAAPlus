package cn.edu.ubaa.plus.reminder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 后台提醒的开关入口。
 *
 * 不再使用常驻前台服务，也**不会**在状态栏留任何通知；只按课表排下一次检查的闹钟。
 */
object ReminderServiceController {
  fun sync(context: Context, loggedIn: Boolean, enabled: Boolean) {
    if (loggedIn && enabled && notificationsAllowed(context)) {
      start(context)
    } else {
      stop(context)
    }
  }

  fun start(context: Context) {
    ReminderScheduler.scheduleNext(context)
  }

  fun stop(context: Context) {
    ReminderScheduler.cancel(context)
  }

  fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }
}
