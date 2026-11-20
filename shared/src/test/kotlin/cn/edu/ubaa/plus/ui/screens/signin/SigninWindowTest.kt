package cn.edu.ubaa.plus.ui.screens.signin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class SigninWindowTest {

  private fun at(hour: Int, minute: Int) = LocalDateTime(2026, 11, 20, hour, minute)

  @Test
  fun `上课前十分钟开放`() {
    // 08:00 上课 → 07:50 起可签。
    assertEquals(SigninWindowState.UPCOMING, signinWindow("08:00", "09:40", at(7, 49)).state)
    assertEquals(SigninWindowState.OPEN, signinWindow("08:00", "09:40", at(7, 50)).state)
  }

  @Test
  fun `上课过程中始终可签`() {
    assertEquals(SigninWindowState.OPEN, signinWindow("08:00", "09:40", at(8, 0)).state)
    assertEquals(SigninWindowState.OPEN, signinWindow("08:00", "09:40", at(9, 20)).state)
    assertEquals(SigninWindowState.OPEN, signinWindow("08:00", "09:40", at(9, 39)).state)
  }

  @Test
  fun `下课后关闭`() {
    assertEquals(SigninWindowState.ENDED, signinWindow("08:00", "09:40", at(9, 40)).state)
    assertEquals(SigninWindowState.ENDED, signinWindow("08:00", "09:40", at(11, 0)).state)
  }

  @Test
  fun `不再是上课后三十分钟截止`() {
    // 旧协议允许到上课后 30 分钟；新规则到下课为止，09:40 之后一律关闭。
    assertEquals(SigninWindowState.ENDED, signinWindow("08:00", "09:40", at(9, 45)).state)
  }

  @Test
  fun `缺时间字段时不拦截提交`() {
    val window = signinWindow("", "", at(8, 0))
    assertEquals(SigninWindowState.UNKNOWN, window.state)
    assertTrue(window.allowsSubmit)
    assertNull(window.describe())
  }

  @Test
  fun `只有未开放与已结束才禁止提交`() {
    assertFalse(signinWindow("08:00", "09:40", at(7, 0)).allowsSubmit)
    assertTrue(signinWindow("08:00", "09:40", at(8, 30)).allowsSubmit)
    assertFalse(signinWindow("08:00", "09:40", at(10, 0)).allowsSubmit)
  }

  @Test
  fun `时间文案说明何时可签`() {
    assertEquals("07:50 起可签到", signinWindow("08:00", "09:40", at(7, 0)).describe())
    assertEquals("可以签到", signinWindow("08:00", "09:40", at(8, 30)).describe())
    assertEquals("本堂课签到已结束", signinWindow("08:00", "09:40", at(10, 0)).describe())
  }

  @Test
  fun `跨零点的提前量不会溢出`() {
    // 00:05 上课，减 10 分钟应落到 00:00 而不是负数；00:00 已在窗口内。
    val window = signinWindow("00:05", "01:00", at(0, 0))
    assertEquals("00:00", padClock(window.opensAt!!))
    assertTrue(window.allowsSubmit)
  }
}
