package cn.edu.ubaa.plus.reminder

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
  const val ATTENDANCE_CHANNEL = "ubaa_attendance_reminders"
  const val HOMEWORK_CHANNEL = "ubaa_homework_reminders"
  const val LIBBOOK_CHANNEL = "ubaa_libbook_results"

  fun ensureChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return

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

    // 预约/签到结果属于一次性反馈，提示音比提醒类低一档，但失败同样需要被看到。
    val libBookChannel =
        NotificationChannel(
                LIBBOOK_CHANNEL,
                "图书馆座位结果",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            .apply {
              description = "座位预约、签到、暂离、签退与取消的结果"
              enableVibration(true)
            }
    manager.createNotificationChannel(libBookChannel)
  }

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
    postNow(
        context = context,
        key = key,
        channel = channel,
        title = title,
        text = text,
        route = route,
        succeeded = null,
    )
  }

  /**
   * 直接投递一条通知，供前台服务之外的场景（预约结果、扫码签到结果）复用。
   *
   * [succeeded] 只在图书馆操作里有意义：失败时补一条文字提示并优先展示。
   */
  fun postNow(
      context: Context,
      key: String,
      channel: String,
      title: String,
      text: String,
      route: AppNotificationRoute,
      succeeded: Boolean? = null,
  ) {
    val notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ubaa)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(appPendingIntent(context, route))
            .setAutoCancel(true)
            .setCategory(
                if (succeeded == false) NotificationCompat.CATEGORY_ERROR
                else NotificationCompat.CATEGORY_REMINDER
            )
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
