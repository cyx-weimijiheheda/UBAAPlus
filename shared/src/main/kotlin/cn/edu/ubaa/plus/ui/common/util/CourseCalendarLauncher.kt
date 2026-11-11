package cn.edu.ubaa.plus.ui.common.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import cn.edu.ubaa.plus.model.format.parseUbaaDateTime
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

@OptIn(ExperimentalTime::class)
fun addCourseToCalendar(
    context: Context,
    title: String,
    location: String?,
    description: String,
    date: LocalDate?,
    beginTime: String?,
    endTime: String?,
): Boolean {
  if (date == null) return false
  val start = parseUbaaTime(beginTime)
  val end = parseUbaaTime(endTime)
  val zone = TimeZone.currentSystemDefault()
  val allDay = start == null
  val beginMillis =
      if (allDay) {
        date.atTime(0, 0).toInstant(zone).toEpochMilliseconds()
      } else {
        date.atTime(start.hour, start.minute).toInstant(zone).toEpochMilliseconds()
      }
  val endMillis =
      when {
        allDay -> beginMillis + 24 * 60 * 60 * 1000L
        end != null -> date.atTime(end.hour, end.minute).toInstant(zone).toEpochMilliseconds()
        else -> beginMillis + 60 * 60 * 1000L
      }

  val intent =
      Intent(Intent.ACTION_INSERT)
          .setData(CalendarContract.Events.CONTENT_URI)
          .putExtra(CalendarContract.Events.TITLE, title)
          .putExtra(CalendarContract.Events.EVENT_LOCATION, location.orEmpty())
          .putExtra(CalendarContract.Events.DESCRIPTION, description)
          .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
          .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, maxOf(endMillis, beginMillis + 60_000L))
          .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
          .putExtra(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  return try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }
}

/**
 * 把作业截止时间写进日历。
 *
 * 截止时间只有一个时刻，按「提前一小时提醒」的 30 分钟事件塞进日历，标题带上课程名便于识别。
 */
@OptIn(ExperimentalTime::class)
fun addAssignmentDeadlineToCalendar(
    context: Context,
    title: String,
    courseName: String,
    dueTime: String?,
): Boolean {
  val due = parseUbaaDateTime(dueTime) ?: return false
  val zone = TimeZone.currentSystemDefault()
  val dueMillis = due.toInstant(zone).toEpochMilliseconds()
  val beginMillis = (dueMillis - 60 * 60 * 1000L).coerceAtLeast(0L)
  val eventTitle =
      listOf(courseName.takeIf { it.isNotBlank() }, title).filterNotNull().joinToString(" · ")
  val intent =
      Intent(Intent.ACTION_INSERT)
          .setData(CalendarContract.Events.CONTENT_URI)
          .putExtra(CalendarContract.Events.TITLE, "作业截止：$eventTitle")
          .putExtra(CalendarContract.Events.DESCRIPTION, "UBAAPlus 记于作业详情")
          .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
          .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dueMillis)
          .putExtra(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
          .putExtra(CalendarContract.Events.HAS_ALARM, 1)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  return try {
    context.startActivity(intent)
    true
  } catch (_: ActivityNotFoundException) {
    false
  } catch (_: SecurityException) {
    false
  }
}
