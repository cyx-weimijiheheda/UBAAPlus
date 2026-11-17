package cn.edu.ubaa.plus.ui.screens.bykc

import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import cn.edu.ubaa.plus.model.dto.*
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import cn.edu.ubaa.plus.repository.AcademicContext
import cn.edu.ubaa.plus.repository.ScheduleStore
import kotlinx.datetime.*

internal data class CourseConflict(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
)

internal data class BykcConflictReport(val conflicts: List<CourseConflict>, val incomplete: Boolean)

internal fun overlappingCourses(
    start: LocalDateTime,
    end: LocalDateTime,
    entries: List<CourseConflict>,
): List<CourseConflict> =
    entries
        .filter { it.start < it.end && start < it.end && it.start < end }
        .distinct()
        .sortedBy { it.start }

/** 仅核对已同步的学校安排；缺失范围明确告知，不声称没有冲突。 */
internal fun checkBykcConflicts(course: BykcCourseDetailDto): BykcConflictReport {
  val start = course.courseStartDate ?: return BykcConflictReport(emptyList(), true)
  val end =
      course.courseEndDate?.takeIf { it > start } ?: return BykcConflictReport(emptyList(), true)
  val context = AcademicContext.current(ScheduleStore.account(), start.date)
  var incomplete = context == null
  val entries = mutableListOf<CourseConflict>()
  context
      ?.weeks
      ?.filter { week ->
        week.startDate <= end.date.toString() && week.endDate >= start.date.toString()
      }
      ?.forEach { week ->
        val schedule = context.schedules[week.serialNumber]
        if (schedule == null) {
          incomplete = true
          return@forEach
        }
        val base = runCatching { LocalDate.parse(week.startDate) }.getOrNull() ?: return@forEach
        schedule.arrangedList.forEach { item ->
          val day = item.dayOfWeek?.takeIf { it in 1..7 }
          val begin = parseUbaaTime(item.beginTime)
          val finish = parseUbaaTime(item.endTime)
          if (day == null || begin == null || finish == null || begin >= finish) {
            incomplete = true
            return@forEach
          }
          val date = base.plus(DatePeriod(days = day - 1))
          entries +=
              CourseConflict(
                  item.courseName,
                  LocalDateTime(date, begin),
                  LocalDateTime(date, finish),
              )
        }
      }
  val exams = context?.bounds?.code?.let { FeatureReadCache.get<ExamArrangementData>("exams", it) }
  if (exams == null) incomplete = true
  exams?.arranged?.forEach { exam ->
    val date = exam.examDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val begin = parseUbaaTime(exam.startTime)
    val finish = parseUbaaTime(exam.endTime)
    if (date != null && begin != null && finish != null && begin < finish)
        entries +=
            CourseConflict(
                "考试 · ${exam.courseName}",
                LocalDateTime(date, begin),
                LocalDateTime(date, finish),
            )
    else incomplete = true
  }
  val chosen = FeatureReadCache.get<List<BykcChosenCourseDto>>("bykc", "chosen")
  if (chosen == null) incomplete = true
  chosen
      ?.filter { it.courseId != course.id }
      ?.forEach { item ->
        val begin = item.courseStartDate
        val finish = item.courseEndDate
        if (begin != null && finish != null && begin < finish)
            entries += CourseConflict("博雅 · ${item.courseName}", begin, finish)
        else incomplete = true
      }
  return BykcConflictReport(overlappingCourses(start, end, entries), incomplete)
}
