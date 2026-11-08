package cn.edu.ubaa.plus.ui.screens.schedule

import cn.edu.ubaa.plus.model.dto.SectionTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleTimeAxisTest {

  /** 校方课程时间表，逐条核对过的正确值。 */
  private val official =
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

  @Test
  fun `标准作息与校方课程时间表一致`() {
    assertEquals(official, standardSectionTimes())
    assertEquals(14, standardSectionTimes().size)
  }

  @Test
  fun `每节课都是四十五分钟`() {
    for ((start, end) in standardSectionTimes()) {
      val minutes = minutesOf(end) - minutesOf(start)
      assertEquals(45, minutes, "$start-$end 不是 45 分钟")
    }
  }

  @Test
  fun `忽略上游算出来的错误节次时间`() {
    // 连堂课 08:00-09:35 会让上游把第 1 节结束和第 2 节开始都算成 09:35。
    val corrupted = SectionTime(1, "08:00", "09:35")
    val label = sectionTimeLabel(corrupted)
    assertEquals("08:00", label.primary)
    assertEquals("08:45", label.secondary)
  }

  @Test
  fun `课间不会被当成一节课`() {
    // 上游可能给出「09:35 - 09:50」这种把课间当节次的错位值，必须被标准作息覆盖。
    val corrupted = SectionTime(2, "09:35", "09:50")
    val label = sectionTimeLabel(corrupted)
    assertEquals("08:50", label.primary)
    assertEquals("09:35", label.secondary)
  }

  @Test
  fun `十四节全部给出正确起止`() {
    for (section in 1..14) {
      val label = sectionTimeLabel(SectionTime(section, null, null))
      assertEquals(official[section - 1].first, label.primary, "第 $section 节开始时间错")
      assertEquals(official[section - 1].second, label.secondary, "第 $section 节结束时间错")
    }
  }

  @Test
  fun `上游格式不规范也不影响显示`() {
    assertEquals(official[0].first, sectionTimeLabel(SectionTime(1, "8:0", "080000")).primary)
  }

  @Test
  fun `超出十四节才回退上游值`() {
    val label = sectionTimeLabel(SectionTime(20, "23:00", "23:45"))
    assertEquals("23:00", label.primary)
    assertEquals("23:45", label.secondary)
  }

  @Test
  fun `上游时间格式统一成 HH 冒号 mm`() {
    assertEquals("08:00", "8:0".normalizeClock())
    assertEquals("08:00", "08:00:00".normalizeClock())
    assertEquals("21:30", "2130".normalizeClock())
    assertNull("abc".normalizeClock())
    assertNull("25:00".normalizeClock())
    assertNull(null.normalizeClock())
  }

  private fun minutesOf(clock: String): Int {
    val parts = clock.split(":")
    return parts[0].toInt() * 60 + parts[1].toInt()
  }
}
