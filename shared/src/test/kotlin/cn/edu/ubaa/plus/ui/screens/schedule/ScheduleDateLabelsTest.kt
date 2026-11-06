package cn.edu.ubaa.plus.ui.screens.schedule

import cn.edu.ubaa.plus.model.dto.Week
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleDateLabelsTest {

  @Test
  fun headerDayLabelsReturnsWeekdayAndMonthDayForValidStartDate() {
    assertEquals(
        listOf(
            ScheduleHeaderDayLabel("Mon", "04.13"),
            ScheduleHeaderDayLabel("Tue", "04.14"),
            ScheduleHeaderDayLabel("Wed", "04.15"),
            ScheduleHeaderDayLabel("Thu", "04.16"),
            ScheduleHeaderDayLabel("Fri", "04.17"),
            ScheduleHeaderDayLabel("Sat", "04.18"),
            ScheduleHeaderDayLabel("Sun", "04.19"),
        ),
        week(startDate = "2026-04-13").headerDayLabels(),
    )
  }

  @Test
  fun headerDayLabelsDoesNotIncludeYearWhenWeekCrossesYears() {
    assertEquals(
        listOf(
            ScheduleHeaderDayLabel("Mon", "12.29"),
            ScheduleHeaderDayLabel("Tue", "12.30"),
            ScheduleHeaderDayLabel("Wed", "12.31"),
            ScheduleHeaderDayLabel("Thu", "01.01"),
            ScheduleHeaderDayLabel("Fri", "01.02"),
            ScheduleHeaderDayLabel("Sat", "01.03"),
            ScheduleHeaderDayLabel("Sun", "01.04"),
        ),
        week(startDate = "2025-12-29").headerDayLabels(),
    )
  }

  @Test
  fun headerDayLabelsFallsBackToWeekdaysWhenDateParsingFails() {
    assertEquals(
        defaultScheduleHeaderDayLabels(),
        week(startDate = "invalid", endDate = "still-invalid").headerDayLabels(),
    )
  }

  @Test
  fun headerDayLabelsCanRecoverFromEndDateWhenStartDateIsInvalid() {
    assertEquals(
        listOf(
            ScheduleHeaderDayLabel("Mon", "04.13"),
            ScheduleHeaderDayLabel("Tue", "04.14"),
            ScheduleHeaderDayLabel("Wed", "04.15"),
            ScheduleHeaderDayLabel("Thu", "04.16"),
            ScheduleHeaderDayLabel("Fri", "04.17"),
            ScheduleHeaderDayLabel("Sat", "04.18"),
            ScheduleHeaderDayLabel("Sun", "04.19"),
        ),
        week(startDate = "invalid", endDate = "2026-04-19").headerDayLabels(),
    )
  }

  @Test
  fun headerDayLabelsSupportsUnpaddedAndDateTimeFormattedWeekBoundaries() {
    assertEquals(
        listOf(
            ScheduleHeaderDayLabel("Mon", "04.13"),
            ScheduleHeaderDayLabel("Tue", "04.14"),
            ScheduleHeaderDayLabel("Wed", "04.15"),
            ScheduleHeaderDayLabel("Thu", "04.16"),
            ScheduleHeaderDayLabel("Fri", "04.17"),
            ScheduleHeaderDayLabel("Sat", "04.18"),
            ScheduleHeaderDayLabel("Sun", "04.19"),
        ),
        week(startDate = "2026-4-13 00:00:00", endDate = "2026-4-19 23:59:59").headerDayLabels(),
    )
  }
}

private fun week(startDate: String, endDate: String = "2026-04-19") =
    Week(
        startDate = startDate,
        endDate = endDate,
        term = "2025-2026-2",
        curWeek = false,
        serialNumber = 7,
        name = "第7周",
    )
