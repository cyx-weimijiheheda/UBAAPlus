package cn.edu.ubaa.plus.ui.screens.schedule

import cn.edu.ubaa.plus.model.dto.TodayClass
import kotlin.test.Test
import kotlin.test.assertEquals

class TodayClassDisplayNameTest {

  @Test
  fun `优先使用真实课程名`() {
    assertEquals(
        "高等数学",
        TodayClass("高等数学", null, "08:00-09:40", "高数").displayCourseName(),
    )
  }

  @Test
  fun `bizName 是占位词时回退 shortName`() {
    assertEquals(
        "高等数学",
        TodayClass("课", null, "08:00-09:40", "高等数学").displayCourseName(),
    )
  }

  @Test
  fun `都为空时给出占位标题`() {
    assertEquals("未命名课程", TodayClass("  ", null, null, null).displayCourseName())
  }
}
