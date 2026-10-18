package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.ui.screens.menu.orderFeatureItems
import cn.edu.ubaa.plus.ui.screens.menu.regularFeatureItems
import cn.edu.ubaa.plus.ui.screens.menu.resolveFeatureItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureOrderTest {
  @Test
  fun `自定义顺序优先，未知 id 被忽略，新功能追加`() {
    val ordered = orderFeatureItems(listOf("judge", "unknown", "schedule"))

    assertEquals("judge", ordered.first().id)
    assertEquals("schedule", ordered[1].id)
    assertEquals(regularFeatureItems().size, ordered.size)
    assertTrue(ordered.none { it.id == "unknown" })
  }

  @Test
  fun `隐藏功能不出现在结果中`() {
    val result = resolveFeatureItems(emptyList(), setOf("judge", "dorm"))

    assertTrue(result.none { it.id == "judge" })
    assertTrue(result.none { it.id == "dorm" })
    assertEquals(regularFeatureItems().size - 2, result.size)
  }

  @Test
  fun `全部隐藏时结果为空`() {
    val allIds = regularFeatureItems().map { it.id }.toSet()

    assertTrue(resolveFeatureItems(emptyList(), allIds).isEmpty())
  }
}
