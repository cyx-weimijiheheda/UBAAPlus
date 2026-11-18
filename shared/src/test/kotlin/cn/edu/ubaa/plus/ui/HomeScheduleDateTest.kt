package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.model.dto.TodayClass
import cn.edu.ubaa.plus.ui.screens.menu.homeScheduleDate
import kotlin.test.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

class HomeScheduleDateTest {
  private fun course(time: String?) = TodayClass("合成课程", null, time, null)

  @Test
  fun waitsAnHourAfterTheLatestCourseIncludingEveningClasses() {
    val classes = listOf(course("20:40-22:15"), course("08:00-09:35"))
    assertEquals(
        LocalDate.parse("2026-11-18"),
        homeScheduleDate(LocalDateTime.parse("2026-11-18T23:14:59"), classes),
    )
    assertEquals(
        LocalDate.parse("2026-11-19"),
        homeScheduleDate(LocalDateTime.parse("2026-11-18T23:15:00"), classes),
    )
  }

  @Test
  fun unknownOrEmptySchedulesAreNotGuessedAndMidnightUsesTheNewDay() {
    val now = LocalDateTime.parse("2026-12-31T23:59:00")
    assertEquals(now.date, homeScheduleDate(now, emptyList()))
    assertEquals(now.date, homeScheduleDate(now, listOf(course(null))))
    assertEquals(now.date, homeScheduleDate(now, listOf(course("10:00-11:00"), course("未定"))))
    assertEquals(
        LocalDate.parse("2027-01-01"),
        homeScheduleDate(now, listOf(course("19:00–20:35"))),
    )
    assertEquals(
        LocalDate.parse("2027-01-01"),
        homeScheduleDate(LocalDateTime.parse("2027-01-01T00:01:00"), listOf(course("14:00-15:35"))),
    )
  }
}
