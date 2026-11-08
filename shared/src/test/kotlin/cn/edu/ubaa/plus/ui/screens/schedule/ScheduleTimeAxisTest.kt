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
  fun `标准作息覆盖十四节且都是具体时间`() {
    val times = standardSectionTimes()
    assertEquals(14, times.size)
    assertTrue(times.all { it.first.matches(HH_MM) && it.second.matches(HH_MM) })
    assertEquals("08:00", times.first().first)
    assertEquals("22:15", times.last().second)
  }

  @Test
  fun `超出标准作息的节次不会显示破折号占位`() {
    val label = sectionTimeLabel(SectionTime(20, null, null))
    assertNotNull(label.primary)
    assertTrue(label.primary != "--:--")
  }

  private companion object {
    val HH_MM = Regex("\\d{2}:\\d{2}")
  }
}
