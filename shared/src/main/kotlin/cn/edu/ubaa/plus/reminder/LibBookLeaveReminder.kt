package cn.edu.ubaa.plus.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import cn.edu.ubaa.plus.api.feature.LibBookApi
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.screens.libbook.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.datetime.*

/** 独立的暂离闹钟；只在确认仍暂离后提醒，不把本地旧状态直接当作通知依据。 */
internal object LibBookLeaveReminder {
  private const val REQUEST = 42002

  private fun pending(context: Context) =
      PendingIntent.getBroadcast(
          context,
          REQUEST,
          Intent(context, LibBookLeaveAlarmReceiver::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

  private fun notificationKey(owner: String, id: String) = "libbook:return:$owner:$id"

  private fun cancelNotice(context: Context, owner: String, id: String) {
    NotificationManagerCompat.from(context)
        .cancel(notificationKey(owner, id).hashCode() and 0x7FFFFFFF)
  }

  fun recordLeave(context: Context, owner: String, booking: LibBookBookingDto) {
    LibBookLeaveStore.record(owner, booking)
    scheduleNext(context)
  }

  fun close(context: Context, owner: String, id: String) {
    LibBookLeaveStore.remove(owner, id)
    cancelNotice(context, owner, id)
    scheduleNext(context)
  }

  fun syncBookings(
      context: Context,
      owner: String,
      bookings: List<LibBookBookingDto>,
      now: LocalDateTime,
  ): List<LibBookBookingDto> {
    val before = LibBookLeaveStore.records(owner).map { it.booking.id }.toSet()
    val result = LibBookLeaveStore.reconcile(owner, bookings, now)
    val after = LibBookLeaveStore.records(owner).map { it.booking.id }.toSet()
    (before - after).forEach { cancelNotice(context, owner, it) }
    scheduleNext(context)
    return result
  }

  fun cancel(context: Context) {
    context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
  }

  fun scheduleNext(context: Context, retry: Boolean = false) {
    val owner = ScheduleStore.account()
    if (
        owner == null ||
            !AppPreferencesStore.state.value.backgroundRemindersEnabled ||
            !ReminderServiceController.notificationsAllowed(context)
    ) {
      cancel(context)
      return
    }
    val now = Clock.System.now()
    val target =
        LibBookLeaveStore.records(owner)
            .filter { !it.notified }
            .mapNotNull { libBookReturnDeadline(it.booking)?.toInstant(LIB_BOOK_ZONE) }
            .filter { it > now }
            .map { (it - 10.minutes).coerceAtLeast(now + if (retry) 1.minutes else 1.seconds) }
            .minOrNull()
    if (target == null) {
      cancel(context)
      return
    }
    val alarm = context.getSystemService(AlarmManager::class.java) ?: return
    runCatching {
      if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms())
          alarm.setExactAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              target.toEpochMilliseconds(),
              pending(context),
          )
      else
          alarm.setAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              target.toEpochMilliseconds(),
              pending(context),
          )
    }
  }

  suspend fun check(context: Context, api: LibBookApi = LibBookApi()) {
    val owner = ScheduleStore.account() ?: return
    if (
        !AppPreferencesStore.state.value.backgroundRemindersEnabled ||
            !ReminderServiceController.notificationsAllowed(context)
    )
        return
    // A partial or failed page is not evidence of a return. Query only; never submit seat actions.
    val fresh = mutableListOf<LibBookBookingDto>()
    for (page in 1..5) {
      val response = api.getBookings(page, 100).getOrNull() ?: return
      fresh += response.bookings
      val ids = LibBookLeaveStore.records(owner).map { it.booking.id }.toSet()
      if (
          fresh.map { it.id }.containsAll(ids) ||
              response.bookings.size < 100 ||
              fresh.size >= response.total
      )
          break
    }
    if (owner != ScheduleStore.account()) return
    val now = Clock.System.now().toLocalDateTime(LIB_BOOK_ZONE)
    syncBookings(context, owner, fresh, now)
    LibBookLeaveStore.claimDue(owner, fresh, now).forEach { booking ->
      ReminderNotifications.postNow(
          context,
          notificationKey(owner, booking.id),
          ReminderNotifications.LIBBOOK_CHANNEL,
          "图书馆暂离即将到期",
          "${booking.nameMerge.ifBlank { booking.seatNo }}\n${libBookReturnHint(booking, now)}",
          AppNotificationRoute.LibBookBookings,
      )
    }
  }
}

class LibBookLeaveAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val app = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
      try {
        withTimeout(8_000) { LibBookLeaveReminder.check(app) }
      } catch (_: Exception) {
        /* 未核实仍暂离就不提醒；期限内稍后重试。 */
      } finally {
        try {
          LibBookLeaveReminder.scheduleNext(app, retry = true)
        } finally {
          pending.finish()
        }
      }
    }
  }
}
