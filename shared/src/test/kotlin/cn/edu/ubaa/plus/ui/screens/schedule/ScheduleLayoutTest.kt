package cn.edu.ubaa.plus.ui.screens.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleLayoutTest {
  @Test
  fun `高屏按最大行高铺开`() {
    val metrics = scheduleRowMetrics(900f, 14)

    assertEquals(64f, metrics.rowHeightDp)
    assertFalse(metrics.scrollable)
  }

  @Test
  fun `普通手机均分高度且不滚动`() {
    val metrics = scheduleRowMetrics(560f, 14)

    assertEquals(40f, metrics.rowHeightDp)
    assertFalse(metrics.scrollable)
  }

  @Test
  fun `矮屏压到最小行高也不滚动`() {
    // 不允许超出第一屏滚动：宁可字号更小，也要 14 节全在一屏内。
    val metrics = scheduleRowMetrics(420f, 14)

    assertEquals(MIN_ROW_HEIGHT_DP, metrics.rowHeightDp)
    assertFalse(metrics.scrollable)
  }

  @Test
  fun `任何高度都不产生滚动`() {
    for (height in listOf(300f, 380f, 420f, 560f, 800f, 1400f)) {
      assertFalse(scheduleRowMetrics(height, 14).scrollable, "高度 $height 时不应滚动")
    }
  }

  @Test
  fun `十四节总高度不超过可用高度`() {
    for (height in listOf(420f, 560f, 800f)) {
      val metrics = scheduleRowMetrics(height, 14)
      assertTrue(
          metrics.rowHeightDp * 14 <= height,
          "高度 $height 时 14 节总高 ${metrics.rowHeightDp * 14} 超出屏幕",
      )
    }
  }

  @Test
  fun `行高不会小到看不清`() {
    assertTrue(scheduleRowMetrics(300f, 14).rowHeightDp >= MIN_ROW_HEIGHT_DP)
  }

  @Test
  fun `节数为零时返回默认值`() {
    val metrics = scheduleRowMetrics(500f, 0)

    assertEquals(64f, metrics.rowHeightDp)
    assertFalse(metrics.scrollable)
  }
}
