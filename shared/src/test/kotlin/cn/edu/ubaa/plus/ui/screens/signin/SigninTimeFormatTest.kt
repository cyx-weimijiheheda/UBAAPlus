package cn.edu.ubaa.plus.ui.screens.signin

import kotlin.test.Test
import kotlin.test.assertEquals

class SigninTimeFormatTest {

  @Test
  fun `纯时间区间保持 HH mm`() {
    assertEquals("08:00 - 09:40", formatSigninTimeRange("08:00", "09:40"))
  }

  @Test
  fun `带日期时补上统一日期格式`() {
    assertEquals(
        "2026.11.06 Fri 08:00 - 09:40",
        formatSigninTimeRange("2026-11-06 08:00", "2026-11-06 09:40"),
    )
  }

  @Test
  fun `解析不了时保留原文`() {
    assertEquals("上午 - 下午", formatSigninTimeRange("上午", "下午"))
  }
}
