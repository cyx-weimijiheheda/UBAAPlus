package cn.edu.ubaa.plus.ui.screens.schedule

import cn.edu.ubaa.plus.model.dto.SectionTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTimeAxisTest {

  @Test
  fun `上游作息表有时间时原样使用`() {
    val label = sectionTimeLabel(SectionTime(1, "10:00", "10:45"))
    assertEquals("10:00", label.primary)
    assertEquals("10:45", label.secondary)
  }

  @Test
  fun `旧缓存缺时间时回退标准作息而不是占位符`() {
    val label = sectionTimeLabel(SectionTime(3, null, null))
    assertEquals("09:50", label.primary)
    assertEquals("10:35", label.secondary)
  }

  @Test
  fun `只有开始时间时不显示占位符`() {
    val label = sectionTimeLabel(SectionTime(1, "08:00", null))
    assertEquals("08:00", label.primary)
    assertNull(label.secondary)
  }

  @Test
  fun `标准作息与校方课表完全一致`() {
    val expected =
        listOf(
            "08:00" to "08:45",
            "08:50" to "09:35",
            "09:50" to "10:35",
            "10:40" to "11:25",
            "11:30" to "12:15",
            "14:00" to "14:45",
            "14:50" to "15:35",
            "15:50" to "16:35",
            "16:40" to "17:25",
            "17:30" to "18:15",
            "19:00" to "19:45",
            "19:50" to "20:35",
            "20:40" to "21:25",
            "21:30" to "22:15",
        )
    assertEquals(expected, standardSectionTimes())
    assertEquals(14, standardSectionTimes().size)
  }

  @Test
  fun `每一节都能取到具体起止时间`() {
    for (section in 1..14) {
      val label = sectionTimeLabel(SectionTime(section, null, null))
      assertNotNull(label.primary, "第 $section 节缺开始时间")
      assertNotNull(label.secondary, "第 $section 节缺结束时间")
      assertTrue(label.primary.matches(HH_MM), "第 $section 节开始时间格式不对")
      assertTrue(label.secondary.matches(HH_MM), "第 $section 节结束时间格式不对")
    }
  }

  @Test
  fun `上游时间格式不规范时统一成 HH 冒号 mm`() {
    assertEquals("08:00", "8:0".normalizeClock())
    assertEquals("08:00", "08:00:00".normalizeClock())
    assertEquals("21:30", "2130".normalizeClock())
    assertEquals("08:05", "8:05".normalizeClock())
  }

  @Test
  fun `无法解析的时间返回 null 以便回退标准作息`() {
    assertNull("abc".normalizeClock())
    assertNull("".normalizeClock())
    assertNull(null.normalizeClock())
    assertNull("25:00".normalizeClock())
    assertNull("08:70".normalizeClock())
  }

  @Test
  fun `超出标准作息的节次不会显示破折号占位`() {
    val label = sectionTimeLabel(SectionTime(20, null, null))
    assertNotNull(label.primary)
    assertTrue(label.primary != "--:--")
  }

  private companion object {
    val HH_MM = Regex("^\\d{2}:\\d{2}$")
  }
}
