@file:OptIn(kotlin.time.ExperimentalTime::class)

package cn.edu.ubaa.plus.model.format

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 全应用统一的日期时间显示格式：
 * - 完整：`2026年10月30日 星期五`、`2026年10月30日 星期五 14:00`、`2026年10月30日 星期五 14:00-16:00`
 * - 紧凑：`10月30日 周五`
 *
 * 解析不了的值原样返回，避免把后端返回的可用文案弄丢。
 */
fun formatUbaaDate(raw: String?): String {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return ""
  val date = parseUbaaDate(text) ?: return text
  return formatUbaaDate(date)
}

fun formatUbaaDate(date: LocalDate): String =
    "${date.year}年${date.month.ordinal + 1}月${date.day}日 ${ubaFullWeekdayLabel(date.dayOfWeek)}"

fun formatUbaaDate(dateTime: LocalDateTime): String = formatUbaaDate(dateTime.date)

fun formatUbaaDateTime(raw: String?): String {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return ""
  val date = parseUbaaDate(text) ?: return text
  val time = parseUbaaTime(text)
  return if (time != null) "${formatUbaaDate(date)} ${formatUbaaTime(time)}"
  else formatUbaaDate(date)
}

fun formatUbaaDateTime(dateTime: LocalDateTime): String =
    "${formatUbaaDate(dateTime.date)} ${formatUbaaTime(dateTime.time)}"

fun formatUbaaTime(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

fun formatUbaaDateRange(start: LocalDateTime, end: LocalDateTime): String {
  if (start.date == end.date) {
    return "${formatUbaaDate(start.date)} ${formatUbaaTime(start.time)}-${formatUbaaTime(end.time)}"
  }
  return "${formatUbaaDateTime(start)} - ${formatUbaaDateTime(end)}"
}

fun formatUbaaDateRange(startRaw: String?, endRaw: String?): String {
  val start = startRaw?.trim().orEmpty()
  val end = endRaw?.trim().orEmpty()
  val startDateTime = parseUbaaDateTime(start)
  val endDateTime = parseUbaaDateTime(end)
  return when {
    startDateTime != null && endDateTime != null -> formatUbaaDateRange(startDateTime, endDateTime)
    startDateTime != null -> formatUbaaDateTime(startDateTime)
    endDateTime != null -> formatUbaaDateTime(endDateTime)
    start.isNotEmpty() && end.isNotEmpty() -> "$start - $end"
    start.isNotEmpty() -> start
    else -> end
  }
}

fun formatUbaaDateWithTimeRange(
    dateRaw: String?,
    startTime: String?,
    endTime: String?,
): String {
  val date = parseUbaaDate(dateRaw)
  if (date == null) {
    return listOfNotNull(dateRaw?.trim(), startTime?.trim(), endTime?.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
  }
  val start = parseUbaaTime(startTime)
  val end = parseUbaaTime(endTime)
  val timeText =
      when {
        start != null && end != null -> "${formatUbaaTime(start)}-${formatUbaaTime(end)}"
        start != null -> formatUbaaTime(start)
        else -> ""
      }
  return listOf(formatUbaaDate(date), timeText).filter { it.isNotEmpty() }.joinToString(" ")
}

/** 处理后端已经拼好的“日期 + 时间段”文本，例如 `2026-04-01 14:00-15:35`。 */
fun formatUbaaFlexibleRange(raw: String?): String {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return ""

  val matches = ubaDatePattern.findAll(text).toList()
  if (matches.isEmpty()) return text

  if (matches.size >= 2) {
    val first = matches[0]
    val second = matches[1]
    if (first.value != second.value) {
      val start = text.substring(first.range.first, second.range.first).trim().trimEnd('-', ' ')
      val end = text.substring(second.range.first).trim()
      return "${formatUbaaDateTime(start)} - ${formatUbaaDateTime(end)}"
    }
  }

  val date = parseUbaaDate(matches.first().value) ?: return text
  val tail = text.substring(matches.first().range.last + 1).trim()
  if (tail.isEmpty()) return formatUbaaDate(date)

  val timeRange = extractUbaaTimeRangeText(tail)
  if (timeRange == null) return "${formatUbaaDate(date)} $tail"
  return "${formatUbaaDate(date)} $timeRange"
}

fun formatUbaaCompactDate(raw: String?): String {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return ""
  val date = parseUbaaDate(text) ?: return text
  return formatUbaaCompactDate(date)
}

fun formatUbaaCompactDate(date: LocalDate): String =
    "${date.month.ordinal + 1}月${date.day}日 ${ubaCompactWeekdayLabel(date.dayOfWeek)}"

fun formatUbaaCompactDateTime(raw: String?): String {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return ""
  val date = parseUbaaDate(text) ?: return text
  val time = parseUbaaTime(text)
  return if (time != null) "${formatUbaaCompactDate(date)} ${formatUbaaTime(time)}"
  else formatUbaaCompactDate(date)
}

fun formatUbaaCompactDateTime(dateTime: LocalDateTime): String =
    "${formatUbaaCompactDate(dateTime.date)} ${formatUbaaTime(dateTime.time)}"

fun formatUbaaCompactTimeRange(
    dateRaw: String?,
    startTime: String?,
    endTime: String?,
): String {
  val date = parseUbaaDate(dateRaw)
  if (date == null) {
    return listOfNotNull(dateRaw?.trim(), startTime?.trim(), endTime?.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" ")
  }
  val start = parseUbaaTime(startTime)
  val end = parseUbaaTime(endTime)
  val timeText =
      when {
        start != null && end != null -> "${formatUbaaTime(start)}-${formatUbaaTime(end)}"
        start != null -> formatUbaaTime(start)
        else -> ""
      }
  return listOf(formatUbaaCompactDate(date), timeText).filter { it.isNotEmpty() }.joinToString(" ")
}

fun formatUbaaMonthDay(raw: String?): String {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return ""
  val date = parseUbaaDate(text) ?: return text
  return "${date.month.ordinal + 1}月${date.day}日"
}

fun parseUbaaDate(raw: String?): LocalDate? {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return null
  val match = ubaDatePattern.find(text) ?: return null
  val (year, month, day) = match.destructured
  return runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

fun parseUbaaDateTime(raw: String?): LocalDateTime? {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return null
  val instant =
      runCatching {
            if (text.endsWith("Z") || ubaOffsetPattern.containsMatchIn(text)) {
              Instant.parse(text.replace(' ', 'T'))
            } else {
              null
            }
          }
          .getOrNull()
  if (instant != null) {
    return instant.toLocalDateTime(TimeZone.currentSystemDefault())
  }

  val date = parseUbaaDate(text) ?: return null
  val match = ubaDatePattern.find(text) ?: return null
  val time = parseTimeAfterDate(text, match.range.last + 1) ?: return null
  return LocalDateTime(date.year, date.month, date.day, time.hour, time.minute, time.second)
}

fun parseUbaaTime(raw: String?): LocalTime? {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return null
  val match = ubaTimePattern.find(text) ?: return null
  return runCatching {
        LocalTime(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].ifBlank { "0" }.toInt(),
        )
      }
      .getOrNull()
}

/** 抽取文本里的第一个时间段（支持 `14:00-15:35`、`09:50-12:15,12:30-13:30`）。 */
fun extractUbaaTimeRangeText(raw: String?): String? {
  val text = raw?.trim().orEmpty()
  if (text.isEmpty()) return null
  val first = ubaTimeRangePattern.find(text) ?: return null
  return text.substring(first.range.first).trim().replace(" - ", "-")
}

private fun parseTimeAfterDate(text: String, startIndex: Int): LocalTime? {
  val tail = text.substring(startIndex)
  return parseUbaaTime(tail)
}

private fun ubaFullWeekdayLabel(dayOfWeek: DayOfWeek): String =
    when (dayOfWeek) {
      DayOfWeek.MONDAY -> "星期一"
      DayOfWeek.TUESDAY -> "星期二"
      DayOfWeek.WEDNESDAY -> "星期三"
      DayOfWeek.THURSDAY -> "星期四"
      DayOfWeek.FRIDAY -> "星期五"
      DayOfWeek.SATURDAY -> "星期六"
      DayOfWeek.SUNDAY -> "星期日"
    }

private fun ubaCompactWeekdayLabel(dayOfWeek: DayOfWeek): String =
    when (dayOfWeek) {
      DayOfWeek.MONDAY -> "周一"
      DayOfWeek.TUESDAY -> "周二"
      DayOfWeek.WEDNESDAY -> "周三"
      DayOfWeek.THURSDAY -> "周四"
      DayOfWeek.FRIDAY -> "周五"
      DayOfWeek.SATURDAY -> "周六"
      DayOfWeek.SUNDAY -> "周日"
    }

private val ubaDatePattern = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?""")

private val ubaTimePattern = Regex("""(?:^|[^\d])(\d{1,2}):(\d{2})(?::(\d{2}))?""")

private val ubaTimeRangePattern = Regex("""\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}""")

private val ubaOffsetPattern = Regex("""[+-]\d{2}:?\d{2}$""")
