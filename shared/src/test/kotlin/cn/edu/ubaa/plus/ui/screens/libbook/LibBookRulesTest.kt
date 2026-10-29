package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.datetime.LocalDateTime

class LibBookRulesTest {
  @Test
  fun `check in deadline is thirty minutes after creation`() {
    val booking = LibBookBookingDto(id = "b1", createTime = "2026-05-08 08:00:00")

    val deadline = libBookCheckInDeadline(booking)

    assertNotNull(deadline)
    assertEquals(LocalDateTime.parse("2026-05-08T08:30:00"), deadline)
    assertEquals("08:30", formatLibBookClock(deadline))
  }

  @Test
  fun `remaining minutes never goes negative`() {
    val deadline = LocalDateTime.parse("2026-05-08T08:30:00")

    assertEquals(20, libBookRemainingMinutes(deadline, LocalDateTime.parse("2026-05-08T08:10:00")))
    assertEquals(0, libBookRemainingMinutes(deadline, LocalDateTime.parse("2026-05-08T09:10:00")))
  }

  @Test
  fun `parses common date time formats`() {
    assertEquals(
        LocalDateTime.parse("2026-05-08T08:00:00"),
        parseLibBookDateTime("2026-05-08 08:00"),
    )
    assertEquals(
        LocalDateTime.parse("2026-05-08T08:00:00"),
        parseLibBookDateTime("2026-05-08T08:00:00"),
    )
  }

  @Test
  fun `booking without creation time has no deadline`() {
    assertEquals(null, libBookCheckInDeadline(LibBookBookingDto(id = "b1")))
  }
}
