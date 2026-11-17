package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.*
import kotlin.test.*
import kotlinx.datetime.LocalTime

class LibBookBookingWindowTest {
  @Test
  fun windowsUseTheEarlierClosingMinuteAndRejectMissingOrReversedTimes() {
    val day =
        LibBookDayScheduleDto(
            "2026-11-18",
            bookStart = "07:00",
            bookEnd = "22:00",
            bookClose = "21:30",
        )
    assertNotNull(day.blockedReason(LocalTime(6, 59)))
    assertNull(day.blockedReason(LocalTime(7, 0)))
    assertNull(day.blockedReason(LocalTime(21, 30, 59)))
    assertNotNull(day.blockedReason(LocalTime(21, 31)))
    assertNotNull(day.copy(bookStart = null).blockedReason(LocalTime(12, 0)))
    assertNotNull(day.copy(bookStart = "22:30").blockedReason(LocalTime(12, 0)))
  }

  @Test
  fun datesNeverBorrowAnotherDaysSlots() {
    val a = LibBookDayScheduleDto("2026-11-18", listOf(LibBookTimeSlotDto("a", "08:00", "12:00")))
    val b = LibBookDayScheduleDto("2026-11-19", listOf(LibBookTimeSlotDto("b", "13:00", "18:00")))
    val detail = LibBookAreaDetailDto("area", days = listOf(a, b))
    assertEquals("b", detail.schedule("2026-11-19")?.timeSlots?.single()?.id)
    assertNull(detail.schedule("2026-11-20"))
    assertNull(detail.copy(days = listOf(a, a)).schedule(a.day))
  }
}
