package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.ui.screens.menu.regularFeatureItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeaturesScreenTest {

  @Test
  fun `功能页包含十二个入口且不再包含 iHome 与更多功能`() {
    val items = regularFeatureItems()

    assertEquals(12, items.size)
    val ids = items.map { it.id }
    assertTrue(
        ids.containsAll(
            listOf(
                "schedule",
                "exam",
                "grade",
                "bykc",
                "classroom",
                "spoc",
                "judge",
                "libbook",
                "venue",
                "cgyy",
                "ygdk",
                "evaluation",
            )
        )
    )
    assertFalse(ids.contains("ihome"))
    assertFalse(ids.contains("more"))
    assertFalse(ids.contains("signin"))
  }

  @Test
  fun `功能入口 id 不重复`() {
    val ids = regularFeatureItems().map { it.id }

    assertEquals(ids.size, ids.toSet().size)
  }
}
