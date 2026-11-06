package cn.edu.ubaa.plus.ui.screens.schedule

import cn.edu.ubaa.plus.model.dto.Week
import cn.edu.ubaa.plus.model.format.formatUbaaMonthDay
import cn.edu.ubaa.plus.model.format.formatUbaaWeekdayLabel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

internal data class ScheduleHeaderDayLabel(
    val weekdayLabel: String,
    val dateLabel: String? = null,
)

internal fun defaultScheduleHeaderDayLabels(): List<ScheduleHeaderDayLabel> =
    (1..7).map { day ->
      ScheduleHeaderDayLabel(weekdayLabel = formatUbaaWeekdayLabel(day).orEmpty())
    }

internal fun Week.headerDayLabels(): List<ScheduleHeaderDayLabel> {
  val parsedStart =
      parseWeekBoundaryDate(startDate)
          ?: parseWeekBoundaryDate(endDate)?.plus(DatePeriod(days = -6))
  val defaults = defaultScheduleHeaderDayLabels()
  if (parsedStart == null) return defaults

  return defaults.mapIndexed { index, default ->
    ScheduleHeaderDayLabel(
        weekdayLabel = default.weekdayLabel,
        dateLabel = formatUbaaMonthDay(parsedStart.plus(DatePeriod(days = index)).toString()),
    )
  }
}

private fun parseWeekBoundaryDate(raw: String?): LocalDate? {
  val normalized = raw?.trim().orEmpty()
  if (normalized.isEmpty()) return null

  runCatching { LocalDate.parse(normalized) }
      .getOrNull()
      ?.let {
        return it
      }

  val match = FLEXIBLE_DATE_PATTERN.find(normalized) ?: return null
  val (yearText, monthText, dayText) = match.destructured
  return runCatching { LocalDate(yearText.toInt(), monthText.toInt(), dayText.toInt()) }.getOrNull()
}

private val FLEXIBLE_DATE_PATTERN = Regex("""(\d{4})\D+(\d{1,2})\D+(\d{1,2})""")
