package cn.edu.ubaa.plus.ui.screens.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptchaFailureTest {

  @Test
  fun `Invalid parameter 视为验证码错误`() {
    assertTrue(isCaptchaFailure("Invalid parameter"))
    assertTrue(isCaptchaFailure("invalid parameter: captcha"))
  }

  @Test
  fun `登录失败与参数不正确也视为验证码错误`() {
    assertTrue(isCaptchaFailure("登录失败"))
    assertTrue(isCaptchaFailure("请求参数不正确，请检查后重试"))
    assertTrue(isCaptchaFailure("账号或密码错误，请重试"))
  }

  @Test
  fun `空消息保守视为需要刷新验证码`() {
    assertTrue(isCaptchaFailure(null))
    assertTrue(isCaptchaFailure(""))
  }

  @Test
  fun `大小写与前后空白不影响判断`() {
    assertTrue(isCaptchaFailure("  INVALID PARAMETER  "))
    assertTrue(isCaptchaFailure("Captcha Error"))
  }

  @Test
  fun `网络类错误不误判为验证码问题`() {
    assertFalse(isCaptchaFailure("连接超时，请稍后重试"))
    assertFalse(isCaptchaFailure("认证服务响应超时，请稍后重试"))
  }
}
