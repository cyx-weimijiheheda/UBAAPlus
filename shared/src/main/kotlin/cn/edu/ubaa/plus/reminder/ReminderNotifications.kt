package cn.edu.ubaa.plus.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cn.edu.ubaa.plus.shared.R

object ReminderNotifications {
  const val SERVICE_CHANNEL = "ubaa_reminder_service"
  const val ATTENDANCE_CHANNEL = "ubaa_attendance_reminders"
  const val HOMEWORK_CHANNEL = "ubaa_homework_reminders"
  const val SERVICE_NOTIFICATION_ID = 41001

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return

    val serviceChannel =
        NotificationChannel(
                SERVICE_CHANNEL,
                "后台提醒服务",
                NotificationManager.IMPORTANCE_LOW,
            )
            .apply {
              description = "UBAAPlus 后台签到与作业提醒服务"
              setShowBadge(false)
              setSound(null, null)
            }
    manager.createNotificationChannel(serviceChannel)

    val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val audioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    val attendanceChannel =
        NotificationChannel(
                ATTENDANCE_CHANNEL,
                "签到提醒",
                NotificationManager.IMPORTANCE_HIGH,
            )
            .apply {
              description = "课程开课、签到与签退提醒"
              enableVibration(true)
              vibrationPattern = longArrayOf(0, 250, 150, 250)
              setSound(sound, audioAttributes)
            }
    manager.createNotificationChannel(attendanceChannel)

    val homeworkChannel =
        NotificationChannel(
                HOMEWORK_CHANNEL,
                "作业提醒",
                NotificationManager.IMPORTANCE_HIGH,
            )
            .apply {
              description = "新作业发布和截止前 24 小时提醒"
              enableVibration(true)
              vibrationPattern = longArrayOf(0, 200, 120, 200)
              setSound(sound, audioAttributes)
            }
    manager.createNotificationChannel(homeworkChannel)
  }

  fun buildServiceNotification(context: Context): Notification =
      NotificationCompat.Builder(context, SERVICE_CHANNEL)
          .setSmallIcon(R.drawable.ic_stat_ubaa)
          .setContentTitle("UBAAPlus 后台提醒已开启")
          .setContentText("将在课程开始、签到与作业截止前提醒；可在设置中关闭")
          .setContentIntent(appPendingIntent(context, AppNotificationRoute.Home))
          .setOngoing(true)
          .setSilent(true)
          .setPriority(NotificationCompat.PRIORITY_LOW)
          .build()

  /** 课程开课、签到、签退提醒。 */
  fun notifyCourse(
      context: Context,
      key: String,
      title: String,
      text: String,
      route: AppNotificationRoute,
  ) {
    post(context, key, ATTENDANCE_CHANNEL, title, text, route)
  }

  fun notifyHomework(
      context: Context,
      key: String,
      title: String,
      text: String,
      route: AppNotificationRoute,
  ) {
    post(context, key, HOMEWORK_CHANNEL, title, text, route)
  }

  private fun post(
      context: Context,
      key: String,
      channel: String,
      title: String,
      text: String,
      route: AppNotificationRoute,
  ) {
    val notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ubaa)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(appPendingIntent(context, route))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()
    runCatching {
      NotificationManagerCompat.from(context).notify(key.hashCode() and 0x7FFFFFFF, notification)
    }
  }

  private fun appPendingIntent(
      context: Context,
      route: AppNotificationRoute,
  ): PendingIntent? {
    val launch =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
          flags =
              Intent.FLAG_ACTIVITY_NEW_TASK or
                  Intent.FLAG_ACTIVITY_CLEAR_TOP or
                  Intent.FLAG_ACTIVITY_SINGLE_TOP
          ReminderIntentExtras.putRoute(this, route)
        } ?: return null
    return PendingIntent.getActivity(
        context,
        route.hashCode(),
        launch,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
