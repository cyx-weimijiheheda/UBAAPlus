package cn.edu.ubaa.plus.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.edu.ubaa.plus.repository.ScheduleStore
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 按课表把下一次检查排到 AlarmManager。
 *
 * 不再常驻后台，因此没有那条去不掉的通知。每次排定的时刻 = 下一个课程事件（开课/签到/签退）到点的时间； 没有课表或时间太远时，退化为每 [FALLBACK_INTERVAL]
 * 查一次（作业只能在轮询里发现）。
 */
object ReminderScheduler {
  private const val REQUEST_CODE = 42001
  private val FALLBACK_INTERVAL = 30.minutes

  fun scheduleNext(context: Context) {
    val nowInstant = Clock.System.now()
    val now = nowInstant.toLocalDateTime(ReminderScheduleBuilder.zone)
    val semesters =
        runCatching { ScheduleStore.account()?.let { ScheduleStore.read(it) } }
            .getOrNull()
            .orEmpty()

    val upcoming =
        ReminderScheduleBuilder.merge(
                ReminderScheduleBuilder.timetableEvents(semesters, now),
                // 博雅课程需要联网才知道，这里只按已缓存的数据排程，其余交给轮询兜底。
                emptyList(),
            )
            .map { it.fireAt.toInstant(ReminderScheduleBuilder.zone) }
            .filter { it > nowInstant }
            .minOrNull()

    val untilNext =
        upcoming?.let { it - nowInstant }?.coerceAtMost(FALLBACK_INTERVAL) ?: FALLBACK_INTERVAL
    schedule(context, untilNext.coerceAtLeast(MIN_DELAY))
  }

  private fun schedule(context: Context, delay: Duration) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    val triggerAt = Clock.System.now().plus(delay).toEpochMilliseconds()
    val pending = pendingIntent(context)
    runCatching {
      // 非精确闹钟即可：课程提醒提前 10-20 分钟，几分钟的误差可以接受，也避开精确闹钟权限。
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      } else {
        manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
      }
    }
  }

  fun cancel(context: Context) {
    val manager = context.getSystemService(AlarmManager::class.java) ?: return
    runCatching { manager.cancel(pendingIntent(context)) }
  }

  private fun pendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, ReminderAlarmReceiver::class.java)
    return PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private val MIN_DELAY = 30.seconds
}
