package cn.edu.ubaa.plus.model.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.datetime.LocalDateTime

class UbaaDateTimeFormatTest {
  @Test
  fun `formats date with full weekday`() {
    assertEquals("2026.10.30 Fri", formatUbaaDate("2026-10-30"))
    assertEquals("2026.03.15 Sun", formatUbaaDate("2026-3-15"))
  }

  @Test
  fun `formats datetime and trims seconds`() {
    assertEquals("2026.10.30 Fri 14:00", formatUbaaDateTime("2026-10-30 14:00:00"))
    assertEquals("2026.10.30 Fri 14:00", formatUbaaDateTime("2026-10-30T14:00"))
  }

  @Test
  fun `formats chinese date`() {
    assertEquals("2026.10.30 Fri 11:31", formatUbaaDateTime("2026年10月30日 11:31:08"))
  }

  @Test
  fun `formats same day range`() {
    assertEquals(
        "2026.10.30 Fri 14:00-16:00",
        formatUbaaDateRange("2026-10-30 14:00", "2026-10-30 16:00"),
    )
  }

  @Test
  fun `formats cross day range with both dates`() {
    assertEquals(
        "2026.10.30 Fri 23:00 - 2026.10.31 Sat 01:00",
        formatUbaaDateRange("2026-10-30 23:00", "2026-10-31 01:00"),
    )
  }

  @Test
  fun `formats date with separate time range`() {
    assertEquals(
        "2026.10.30 Fri 14:00-16:00",
        formatUbaaDateWithTimeRange("2026-10-30", "14:00", "16:00"),
    )
  }

  @Test
  fun `formats flexible backend range`() {
    assertEquals(
        "2026.04.01 Wed 14:00-15:35",
        formatUbaaFlexibleRange("2026-04-01 14:00-15:35"),
    )
    assertEquals(
        "2026.04.01 Wed 09:50-12:15,12:30-13:30",
        formatUbaaFlexibleRange("2026-04-01 09:50-12:15,12:30-13:30"),
    )
    assertEquals(
        "2026.04.01 Wed 23:00 - 2026.04.02 Thu 00:30",
        formatUbaaFlexibleRange("2026-04-01 23:00 - 2026-04-02 00:30"),
    )
    assertEquals("2026.04.01 Wed", formatUbaaFlexibleRange("2026-04-01"))
  }

  @Test
  fun `formats compact values`() {
    assertEquals("10.30 Fri", formatUbaaCompactDate("2026-10-30"))
    assertEquals("10.30 Fri 14:00", formatUbaaCompactDateTime("2026-10-30 14:00"))
    assertEquals(
        "10.30 Fri 14:00-16:00",
        formatUbaaCompactTimeRange("2026-10-30", "14:00", "16:00"),
    )
    assertEquals("10.30", formatUbaaMonthDay("2026-10-30"))
  }

  @Test
  fun `returns raw text when parsing fails`() {
    assertEquals("时间待定", formatUbaaDate("时间待定"))
    assertEquals("未知", formatUbaaDateTime("未知"))
    assertEquals("", formatUbaaDate(""))
  }

  @Test
  fun `parses local datetime values`() {
    val dateTime = LocalDateTime.parse("2026-10-30T14:00:00")
    assertEquals("2026.10.30 Fri 14:00", formatUbaaDateTime(dateTime))
    assertEquals("10.30 Fri", formatUbaaCompactDate(dateTime.date))
    assertNotNull(parseUbaaDate("2026-10-30"))
  }
}
