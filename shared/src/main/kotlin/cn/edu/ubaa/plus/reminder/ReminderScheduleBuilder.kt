package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.model.dto.BykcChosenCourseDto
import cn.edu.ubaa.plus.model.dto.CourseClass
import cn.edu.ubaa.plus.model.dto.SectionTime
import cn.edu.ubaa.plus.model.dto.scheduleSectionTimes
import cn.edu.ubaa.plus.model.format.formatUbaaDate
import cn.edu.ubaa.plus.model.format.formatUbaaTime
import cn.edu.ubaa.plus.repository.SavedAgendaItem
import cn.edu.ubaa.plus.repository.SemesterSchedule
import cn.edu.ubaa.plus.repository.savedAgenda
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 把课表和博雅课程转换成定点提醒。
 *
 * 恒定规则：开课提醒永远比签到提醒早 10 分钟。普通课程签到在开课前 10 分钟（智慧教室提前 10 分钟开放）， 博雅签到以课程信息里的 `signStartDate` 为准，所以开课提醒取
 * `signStart - 10 分钟`。
 */
object ReminderScheduleBuilder {
  val zone: TimeZone = TimeZone.of("Asia/Shanghai")

  /** 今天/明天课表里的开课与签到事件。 */
  fun timetableEvents(
      semesters: List<SemesterSchedule>,
      now: LocalDateTime,
      horizonDays: Int = 2,
  ): List<ReminderEvent> {
    val sectionTimes = scheduleSectionTimes(semesters.flatMap { it.schedules.values })
    return savedAgenda(semesters, now.date, horizonDays).flatMap { item ->
      timetableEventsFor(item, sectionTimes)
    }
  }

  private fun timetableEventsFor(
      item: SavedAgendaItem,
      sectionTimes: List<SectionTime>,
  ): List<ReminderEvent> {
    val course = item.course
    val begin = course.resolveBegin(sectionTimes) ?: return emptyList()
    val end = course.resolveEnd(sectionTimes)
    val beginAt = LocalDateTime(item.date, begin)
    val timeLabel =
        if (end != null) "${formatUbaaTime(begin)}-${formatUbaaTime(end)}"
        else formatUbaaTime(begin)
    val place = course.placeName?.takeIf { it.isNotBlank() }
    val detail = listOfNotNull(timeLabel, place).joinToString(" · ")
    val id = course.identityKey(begin)
    val courseName = course.courseName

    return listOf(
        ReminderEvent(
            key = "iclass-open:${item.date}:$id",
            kind = ReminderEventKind.COURSE_OPEN,
            fireAt = beginAt.shiftMinutes(-20),
            title = "$courseName 即将开课",
            text =
                buildString {
                  append("上课时间 $detail")
                  if (place == null) append(" · 地点待确认")
                },
            route = AppNotificationRoute.Home,
        ),
        ReminderEvent(
            key = "iclass-sign:${item.date}:$id",
            kind = ReminderEventKind.ICLASS_SIGN_IN,
            fireAt = beginAt.shiftMinutes(-10),
            title = "$courseName 现在可以签到",
            text = "$detail · 智慧教室签到已开放，点击前往首页签到",
            route = AppNotificationRoute.Home,
            courseName = courseName,
            beginTime = formatUbaaTime(begin),
        ),
    )
  }

  /** 博雅已选课程的开课、签到、签退事件。 */
  fun bykcEvents(
      courses: List<BykcChosenCourseDto>,
      now: LocalDateTime,
      horizonDays: Int = 2,
  ): List<ReminderEvent> {
    val lastDate = now.date.plus(horizonDays - 1, DateTimeUnit.DAY)
    return courses.flatMap { course ->
      if (course.pass == 1) return@flatMap emptyList()
      val signStart =
          course.signConfig?.signStartDate ?: course.courseStartDate ?: return@flatMap emptyList()
      if (signStart.date < now.date || signStart.date > lastDate) return@flatMap emptyList()

      val events = mutableListOf<ReminderEvent>()
      val courseName = course.courseName
      val route = AppNotificationRoute.BykcCourse(course.courseId)
      val signOutStart = course.signConfig?.signOutStartDate
      val place = course.coursePosition?.takeIf { it.isNotBlank() }
      val teacher = course.courseTeacher?.takeIf { it.isNotBlank() }
      val header =
          listOfNotNull(
                  formatUbaaDate(signStart.date),
                  "${formatUbaaTime(signStart.time)} 起可签到",
                  place,
                  teacher,
              )
              .joinToString(" · ")

      events +=
          ReminderEvent(
              key = "bykc-open:${course.courseId}:$signStart",
              kind = ReminderEventKind.COURSE_OPEN,
              fireAt = signStart.shiftMinutes(-10),
              title = "$courseName 即将开课",
              text = header,
              route = route,
          )
      if (course.checkin == 0) {
        events +=
            ReminderEvent(
                key = "bykc-sign:${course.courseId}:$signStart",
                kind = ReminderEventKind.BYKC_SIGN_IN,
                fireAt = signStart,
                title = "$courseName 现在可以签到",
                text =
                    listOfNotNull(header, signOutStart?.let { "签退 ${formatUbaaTime(it.time)} 开始" })
                        .joinToString(" · "),
                route = route,
            )
      }
      // 不开放签到的博雅课程同样不该提醒签退：签到和签退是同一套签到配置。
      signOutStart
          ?.takeIf { course.checkin == 0 }
          ?.let { signOut ->
            events +=
                ReminderEvent(
                    key = "bykc-signout:${course.courseId}:$signOut",
                    kind = ReminderEventKind.BYKC_SIGN_OUT,
                    fireAt = signOut,
                    title = "$courseName 现在可以签退",
                    text =
                        listOfNotNull(
                                formatUbaaDate(signOut.date),
                                "${formatUbaaTime(signOut.time)} 起可签退",
                                place,
                            )
                            .joinToString(" · "),
                    route = route,
                )
          }
      events
    }
  }

  /** 合并多个来源并按 key 去重。 */
  fun merge(vararg lists: List<ReminderEvent>): List<ReminderEvent> =
      lists.flatMap { it }.distinctBy { it.key }
}

internal fun LocalDateTime.shiftMinutes(minutes: Int): LocalDateTime =
    (toInstant(ReminderScheduleBuilder.zone) + minutes.minutes).toLocalDateTime(
        ReminderScheduleBuilder.zone
    )

private fun CourseClass.resolveBegin(sectionTimes: List<SectionTime>): LocalTime? =
    parseHm(beginTime)
        ?: sectionTimes.firstOrNull { it.section == beginSection }?.start?.let(::parseHm)

private fun CourseClass.resolveEnd(sectionTimes: List<SectionTime>): LocalTime? =
    parseHm(endTime) ?: sectionTimes.firstOrNull { it.section == endSection }?.end?.let(::parseHm)

private fun CourseClass.identityKey(begin: LocalTime): String =
    "${courseCode}:${beginSection ?: begin.hour * 60 + begin.minute}"

private fun parseHm(raw: String?): LocalTime? {
  val parts = raw?.trim()?.split(":") ?: return null
  if (parts.size < 2) return null
  val hour = parts[0].toIntOrNull() ?: return null
  val minute = parts[1].toIntOrNull() ?: return null
  if (hour !in 0..23 || minute !in 0..59) return null
  return LocalTime(hour, minute)
}
