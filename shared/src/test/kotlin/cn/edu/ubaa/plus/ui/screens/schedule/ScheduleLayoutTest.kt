package cn.edu.ubaa.plus.ui.screens.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleLayoutTest {
  @Test
  fun `tall screen fits all periods`() {
    val metrics = scheduleRowMetrics(900f, 14)

    assertEquals(64f, metrics.rowHeightDp)
    assertFalse(metrics.scrollable)
  }

  @Test
  fun `normal phone shrinks rows to fit`() {
    val metrics = scheduleRowMetrics(560f, 14)

    assertEquals(40f, metrics.rowHeightDp)
    assertFalse(metrics.scrollable)
  }

  @Test
  fun `short screen falls back to minimum and scrolls`() {
    val metrics = scheduleRowMetrics(420f, 14)

    assertEquals(40f, metrics.rowHeightDp)
    assertTrue(metrics.scrollable)
  }

  @Test
  fun `zero periods returns default`() {
    val metrics = scheduleRowMetrics(500f, 0)

    assertEquals(64f, metrics.rowHeightDp)
    assertFalse(metrics.scrollable)
  }
}
