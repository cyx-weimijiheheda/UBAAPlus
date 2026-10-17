package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.ui.screens.menu.regularFeatureItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FeaturesScreenTest {

  @Test
  fun `功能页包含十三个入口且顺序固定`() {
    val ids = regularFeatureItems().map { it.id }

    assertEquals(
        listOf(
            "schedule",
            "classroom",
            "iclass",
            "libbook",
            "spoc",
            "bykc",
            "venue",
            "ygdk",
            "cgyy",
            "judge",
            "evaluation",
            "exam",
            "grade",
        ),
        ids,
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
