package cn.edu.ubaa.plus.util

import kotlin.test.*

class DecimalFormatTest {
  @Test
  fun upstreamMoneyIsRoundedAsDecimalText() {
    assertEquals("3.41", formatMoney("3.410000000000001"))
    assertEquals("3.42", formatMoney("3.415"))
    assertEquals("3.41", formatMoney("3.41e0"))
    assertEquals("0.00", formatMoney("-0.0001"))
    assertEquals("-1.24", formatMoney("-1.235"))
    assertEquals("12.00", formatMoney(12.0))
  }

  @Test
  fun invalidValuesStayUnavailable() {
    listOf(null, "", "NaN", "Infinity", "未提供", "1e10000000").forEach { assertNull(formatMoney(it)) }
  }
}
