package cn.edu.ubaa.plus.ui.screens.schedule

import cn.edu.ubaa.plus.model.dto.Week
import kotlin.test.*
import kotlinx.datetime.LocalDateTime

class ScheduleNowMarkerTest {
  private val week = Week("2026-11-16", "2026-11-22", "term", true, 1, "第一周")

  private fun marker(time: String) =
      scheduleNowMarker(week, LocalDateTime.parse("2026-11-18T$time"))

  @Test
  fun `课内按分钟比例定位在今天一列`() {
    val position = marker("08:22:30")!!
    assertEquals(2, position.dayIndex)
    assertEquals(0.5f, position.rowOffset, 0.0001f)
  }

  @Test
  fun `课间和午休定位在下一节边界`() {
    assertEquals(1f, marker("08:47:00")?.rowOffset)
    assertEquals(5f, marker("12:30:00")?.rowOffset)
  }

  @Test
  fun `首节前末节后和其他周不显示`() {
    assertNull(marker("07:59:00"))
    assertNull(marker("22:16:00"))
    assertNull(scheduleNowMarker(week, LocalDateTime.parse("2027-11-18T09:00:00")))
    assertNull(scheduleNowMarker(week, LocalDateTime.parse("2026-11-23T09:00:00")))
  }

  @Test
  fun `跨年周按完整日期匹配且支持结束日期兜底`() {
    val yearWeek = week.copy(startDate = "", endDate = "2027-01-03")
    assertEquals(
        4,
        scheduleNowMarker(yearWeek, LocalDateTime.parse("2027-01-01T08:00:00"))?.dayIndex,
    )
    assertNull(
        scheduleNowMarker(
            week.copy(startDate = "", endDate = ""),
            LocalDateTime.parse("2026-11-18T08:00:00"),
        )
    )
  }
}
