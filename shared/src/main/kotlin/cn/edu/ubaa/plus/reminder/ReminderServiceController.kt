package cn.edu.ubaa.plus.reminder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object ReminderServiceController {
  fun sync(context: Context, loggedIn: Boolean, enabled: Boolean) {
    if (loggedIn && enabled && notificationsAllowed(context)) {
      start(context)
    } else {
      stop(context)
    }
  }

  fun start(context: Context) {
    runCatching {
      ContextCompat.startForegroundService(
          context,
          Intent(context, ReminderForegroundService::class.java),
      )
    }
  }

  fun stop(context: Context) {
    runCatching { context.stopService(Intent(context, ReminderForegroundService::class.java)) }
  }

  fun notificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 33) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }
}
