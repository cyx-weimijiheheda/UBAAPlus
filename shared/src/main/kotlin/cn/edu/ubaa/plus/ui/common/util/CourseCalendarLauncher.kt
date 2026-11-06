package cn.edu.ubaa.plus.ui.common.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
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
