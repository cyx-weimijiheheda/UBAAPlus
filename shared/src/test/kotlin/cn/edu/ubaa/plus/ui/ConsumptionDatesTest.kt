package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.testing.TestSettings
import cn.edu.ubaa.plus.ui.screens.campus.*
import kotlin.test.*
import kotlinx.datetime.LocalDate

class ConsumptionDatesTest {
  private val today = LocalDate.parse("2026-11-19")

  @Test
  fun calendarsDisableFutureAndReversedDatesWhileAllowingSameDay() {
    val first = LocalDate.parse("2026-11-17")
    assertTrue(consumptionDateSelectable(first, true, first, today, today))
    assertTrue(consumptionDateSelectable(first, false, first, today, today))
    assertTrue(consumptionDateSelectable(today, false, first, today, today))
    assertFalse(consumptionDateSelectable(LocalDate.parse("2026-11-20"), true, first, today, today))
    assertFalse(
        consumptionDateSelectable(LocalDate.parse("2026-11-16"), false, first, today, today)
    )
    assertFalse(consumptionDateSelectable(today, true, first, first, today))
  }

  @Test
  fun defaultRangeIsTodayAndViewModelRejectsInvalidDateMutations() {
    TestSettings.installInMemorySettings()
    val model = CampusCardConsumptionViewModel(today = { today })
    assertEquals(today, model.state.value.start)
    assertEquals(today, model.state.value.end)
    model.selectStart(LocalDate.parse("2026-11-20"))
    model.selectEnd(LocalDate.parse("2026-11-18"))
    assertEquals(today, model.state.value.start)
    assertEquals(today, model.state.value.end)
    model.selectStart(LocalDate.parse("2026-11-17"))
    model.selectEnd(LocalDate.parse("2026-11-18"))
    assertEquals(LocalDate.parse("2026-11-17"), model.state.value.start)
    assertEquals(LocalDate.parse("2026-11-18"), model.state.value.end)
  }
}
