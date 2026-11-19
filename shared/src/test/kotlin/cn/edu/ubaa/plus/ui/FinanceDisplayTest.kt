package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.feature.FinanceIncome
import cn.edu.ubaa.plus.ui.screens.finance.financeMonth
import cn.edu.ubaa.plus.ui.screens.finance.financeYuan
import kotlin.test.*

class FinanceDisplayTest {
  @Test
  fun zeroMissingNegativeAndMonthAreDisplayedWithoutRoundingArtifacts() {
    assertEquals("0.00 元", financeYuan(0))
    assertEquals("未提供", financeYuan(null))
    assertEquals("-0.05 元", financeYuan(-5))
    assertEquals(
        "2026-01",
        financeMonth(FinanceIncome("id", null, null, null, null, null, null, null, "2026", "1")),
    )
  }
}
