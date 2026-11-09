package cn.edu.ubaa.plus.ui.screens.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
  }

  @Test
  fun `时间轴固定十四节且顺序完整`() {
    val slots = sectionSlots()
    assertEquals(14, slots.size)
    assertEquals((1..14).toList(), slots.map { it.section })
    slots.forEachIndexed { index, slot ->
      assertEquals(official[index].first, slot.start, "第 ${slot.section} 节开始时间错")
      assertEquals(official[index].second, slot.end, "第 ${slot.section} 节结束时间错")
    }
  }

  @Test
  fun `每节课都是四十五分钟`() {
    sectionSlots().forEach { slot ->
      assertEquals(
          45,
          minutesOf(slot.end) - minutesOf(slot.start),
          "${slot.start}-${slot.end} 不是 45 分钟",
      )
    }
  }

  @Test
  fun `时间轴不读取上游数据`() {
    // 没有任何参数来自上游：连续调用结果稳定，且一定是 14 节。
    assertEquals(sectionSlots(), sectionSlots())
    assertTrue(sectionSlots().all { it.start.matches(HH_MM) && it.end.matches(HH_MM) })
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

  private companion object {
    val HH_MM = Regex("^\\d{2}:\\d{2}$")
  }
}
