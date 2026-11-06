package cn.edu.ubaa.plus.reminder

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 登录态下每 5 分钟检查签到/签退与作业，并发出系统通知。 */
class ReminderForegroundService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var loopJob: Job? = null
  private val engine by lazy { ReminderPollEngine() }

  override fun onCreate() {
    super.onCreate()
    ReminderNotifications.ensureChannels(this)
    val notification = ReminderNotifications.buildServiceNotification(this)
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(
          ReminderNotifications.SERVICE_NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(ReminderNotifications.SERVICE_NOTIFICATION_ID, notification)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (LocalAuthSessionStore.get() == null) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (loopJob == null) {
      loopJob = scope.launch { pollLoop() }
    }
    return START_STICKY
  }

  private suspend fun pollLoop() {
    var tick = 0
    while (scope.isActive) {
      val userKey = LocalAuthSessionStore.get()?.username
      if (userKey.isNullOrBlank()) {
        stopSelf()
        return
      }
      runCatching { pollAttendance(userKey) }
      if (tick % 6 == 0) {
        runCatching { pollHomework(userKey) }
      }
      tick++
      delay(5 * 60 * 1000L)
    }
  }

  private suspend fun pollAttendance(userKey: String) {
    engine.collectAttendance().forEach { candidate ->
      if (ReminderSeenStore.markIfNew(userKey, candidate.key)) {
        ReminderNotifications.notifyAttendance(
            context = this,
            key = candidate.key,
            title = candidate.title,
            text = candidate.text,
            route = candidate.route,
        )
      }
    }
  }

  private suspend fun pollHomework(userKey: String) {
    engine.collectHomework(userKey).forEach { candidate ->
      if (ReminderSeenStore.markIfNew(userKey, candidate.key)) {
        ReminderNotifications.notifyHomework(
            context = this,
            key = candidate.key,
            title = candidate.title,
            text = candidate.text,
            route = candidate.route,
        )
      }
    }
  }

  override fun onDestroy() {
    loopJob?.cancel()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
