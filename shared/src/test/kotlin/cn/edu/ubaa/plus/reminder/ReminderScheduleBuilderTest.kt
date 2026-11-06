package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.model.dto.BykcChosenCourseDto
import cn.edu.ubaa.plus.model.dto.BykcSignConfigDto
import cn.edu.ubaa.plus.model.dto.CourseClass
import cn.edu.ubaa.plus.model.dto.SectionTime
import cn.edu.ubaa.plus.model.dto.Week
import cn.edu.ubaa.plus.model.dto.WeeklySchedule
import cn.edu.ubaa.plus.repository.SemesterSchedule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant

class ReminderScheduleBuilderTest {

  private val friday = LocalDateTime(2026, 11, 6, 7, 0)

  private fun course(
      name: String = "高等数学",
      begin: String? = "08:00",
      end: String? = "09:40",
      dayOfWeek: Int = 5,
  ) =
      CourseClass(
          courseCode = "CS101",
          courseName = name,
          courseSerialNo = null,
          credit = null,
          beginTime = begin,
          endTime = end,
          beginSection = 1,
          endSection = 2,
          placeName = "主楼 101",
          weeksAndTeachers = null,
          teachingTarget = null,
          color = null,
          dayOfWeek = dayOfWeek,
      )

  private fun semester(
      course: CourseClass,
      sectionTimes: List<SectionTime> = emptyList(),
  ): SemesterSchedule {
    val week =
        Week(
            startDate = "2026-11-02",
            endDate = "2026-11-08",
            term = "2026-1",
            curWeek = true,
            serialNumber = 1,
            name = "第1周",
        )
    return SemesterSchedule(
        terms = emptyList(),
        termCode = "2026-1",
        weeks = listOf(week),
        schedules =
            mapOf(
                1 to
                    WeeklySchedule(
                        listOf(course),
                        "2026-1",
                        "第一学期",
                        sectionTimes,
                    )
            ),
    )
  }

  @Test
  fun `普通课程开课提前二十分钟签到提前十分钟`() {
    val events = ReminderScheduleBuilder.timetableEvents(listOf(semester(course())), friday)

    val open = events.single { it.kind == ReminderEventKind.COURSE_OPEN }
    val sign = events.single { it.kind == ReminderEventKind.ICLASS_SIGN_IN }

    assertEquals(LocalDateTime(2026, 11, 6, 7, 40), open.fireAt)
    assertEquals(LocalDateTime(2026, 11, 6, 7, 50), sign.fireAt)
    assertEquals(AppNotificationRoute.Home, open.route)
    assertEquals(AppNotificationRoute.Home, sign.route)
    assertEquals("高等数学 即将开课", open.title)
    assertEquals("高等数学 可以签到", sign.title)
    assertEquals(10.minutes, sign.fireAt.toKotlinInstant() - open.fireAt.toKotlinInstant())
  }

  @Test
  fun `没有具体时间时回退到节次作息表`() {
    val events =
        ReminderScheduleBuilder.timetableEvents(
            listOf(
                semester(
                    course(begin = null, end = null),
                    sectionTimes =
                        listOf(
                            SectionTime(1, "10:00", "10:45"),
                            SectionTime(2, "10:50", "11:35"),
                        ),
                )
            ),
            friday,
        )

    assertEquals(LocalDateTime(2026, 11, 6, 9, 40), events.first().fireAt)
  }

  @Test
  fun `博雅课程按签到信息生成开课签到签退`() {
    val course =
        BykcChosenCourseDto(
            id = 1,
            courseId = 42,
            courseName = "博雅讲座",
            signConfig =
                BykcSignConfigDto(
                    signStartDate = LocalDateTime(2026, 11, 6, 14, 0),
                    signOutStartDate = LocalDateTime(2026, 11, 6, 16, 30),
                ),
        )

    val events = ReminderScheduleBuilder.bykcEvents(listOf(course), friday)

    val open = events.single { it.kind == ReminderEventKind.COURSE_OPEN }
    val sign = events.single { it.kind == ReminderEventKind.BYKC_SIGN_IN }
    val signOut = events.single { it.kind == ReminderEventKind.BYKC_SIGN_OUT }

    assertEquals(LocalDateTime(2026, 11, 6, 13, 50), open.fireAt)
    assertEquals(LocalDateTime(2026, 11, 6, 14, 0), sign.fireAt)
    assertEquals(LocalDateTime(2026, 11, 6, 16, 30), signOut.fireAt)
    assertEquals(AppNotificationRoute.BykcCourse(42), sign.route)
    assertEquals(10.minutes, sign.fireAt.toKotlinInstant() - open.fireAt.toKotlinInstant())
  }

  @Test
  fun `超出提醒范围的事件不生成`() {
    val course =
        BykcChosenCourseDto(
            id = 1,
            courseId = 42,
            courseName = "博雅讲座",
            signConfig = BykcSignConfigDto(signStartDate = LocalDateTime(2026, 11, 8, 14, 0)),
        )

    assertTrue(ReminderScheduleBuilder.bykcEvents(listOf(course), friday).isEmpty())
  }

  @Test
  fun `合并按 key 去重`() {
    val first =
        ReminderEvent(
            key = "same",
            kind = ReminderEventKind.COURSE_OPEN,
            fireAt = friday,
            title = "A",
            text = "",
            route = AppNotificationRoute.Home,
        )
    val second = first.copy(title = "B")

    assertEquals(listOf(first), ReminderScheduleBuilder.merge(listOf(first), listOf(second)))
  }

  private fun LocalDateTime.toKotlinInstant() = toInstant(ReminderScheduleBuilder.zone)
}
