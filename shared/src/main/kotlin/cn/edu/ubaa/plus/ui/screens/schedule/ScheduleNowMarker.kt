package cn.edu.ubaa.plus.ui.screens.schedule

import cn.edu.ubaa.plus.model.dto.Week
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

internal data class ScheduleNowMarker(val dayIndex: Int, val rowOffset: Float)

/** 仅本周当天绘制；课内线性定位，课间停在下一节边界，首节前与末节后隐藏。 */
internal fun scheduleNowMarker(week: Week, now: LocalDateTime): ScheduleNowMarker? {
  val start =
      parseWeekBoundaryDate(week.startDate)
          ?: parseWeekBoundaryDate(week.endDate)?.plus(DatePeriod(days = -6))
          ?: return null
  val day = (0..6).firstOrNull { start.plus(DatePeriod(days = it)) == now.date } ?: return null
  val minute = now.hour * 60 + now.minute + now.second / 60f
  val slots = sectionSlots()
  slots.forEachIndexed { index, slot ->
    fun minutes(time: String) =
        time.substringBefore(":").toInt() * 60 + time.substringAfter(":").toInt()
    val begin = minutes(slot.start)
    val end = minutes(slot.end)
    if (minute < begin) return if (index == 0) null else ScheduleNowMarker(day, index.toFloat())
    if (minute <= end) return ScheduleNowMarker(day, index + (minute - begin) / (end - begin))
  }
  return null
}
