package cn.edu.ubaa.plus.repository

import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import cn.edu.ubaa.plus.model.dto.*
import kotlinx.datetime.LocalDate

internal data class SemesterBounds(val code: String, val start: LocalDate, val end: LocalDate)

internal data class AcademicTermContext(
    val bounds: SemesterBounds,
    val weeks: List<Week>,
    val schedules: Map<Int, WeeklySchedule>,
)

/** 读取本机已同步的当前学期，不因提醒或冲突检测额外请求学校。 */
internal object AcademicContext {
  fun current(owner: String?, today: LocalDate): AcademicTermContext? {
    if (owner == null || owner != ScheduleStore.account()) return null
    val saved = runCatching { ScheduleStore.read(owner) }.getOrDefault(emptyList())
    val terms =
        FeatureReadCache.get<List<Term>>("schedule", "terms")
            ?: saved.flatMap { it.terms }.distinctBy { it.itemCode }
    val selected = terms.firstOrNull { it.selected } ?: return null
    val local = saved.firstOrNull { it.termCode == selected.itemCode }
    val weeks =
        FeatureReadCache.get<List<Week>>("schedule", "weeks:${selected.itemCode}")
            ?: local?.weeks
            ?: return null
    val dates =
        weeks.map { week ->
          val start = runCatching { LocalDate.parse(week.startDate) }.getOrNull() ?: return null
          val end = runCatching { LocalDate.parse(week.endDate) }.getOrNull() ?: return null
          if (start > end || week.term != selected.itemCode) return null
          start to end
        }
    if (dates.isEmpty()) return null
    val start = dates.minOf { it.first }
    val end = dates.maxOf { it.second }
    if (today < start || today > end) return null
    val schedules =
        weeks
            .mapNotNull { week ->
              (FeatureReadCache.get<WeeklySchedule>(
                      "schedule",
                      "weekly:${selected.itemCode}:${week.serialNumber}",
                  ) ?: local?.schedules?.get(week.serialNumber))
                  ?.let { week.serialNumber to it }
            }
            .toMap()
    return AcademicTermContext(SemesterBounds(selected.itemCode, start, end), weeks, schedules)
  }

  fun hasPhysicalEducation(owner: String?, today: LocalDate): Boolean =
      current(owner, today)?.schedules?.values?.any { week ->
        week.arrangedList.any { it.courseName.contains("体育") }
      } == true
}
