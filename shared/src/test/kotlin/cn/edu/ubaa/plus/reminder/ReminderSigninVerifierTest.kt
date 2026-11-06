package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.model.dto.SigninClassDto
import cn.edu.ubaa.plus.model.dto.SigninStatusResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

class ReminderSigninVerifierTest {

  private val event =
      ReminderEvent(
          key = "iclass-sign:2026-11-06:CS101:1",
          kind = ReminderEventKind.ICLASS_SIGN_IN,
          fireAt = LocalDateTime(2026, 11, 6, 7, 50),
          title = "高等数学 可以签到",
          text = "",
          route = AppNotificationRoute.Home,
          courseName = "高等数学",
      )

  @Test
  fun `已签到时抑制通知`() = runTest {
    assertFalse(ReminderSigninVerifier(response(signStatus = 1)).shouldNotify(event))
  }

  @Test
  fun `未签到时照常通知`() = runTest {
    assertTrue(ReminderSigninVerifier(response(signStatus = 0)).shouldNotify(event))
  }

  @Test
  fun `课程对不上时照常通知`() = runTest {
    val verifier = ReminderSigninVerifier {
      Result.success(
          SigninStatusResponse(
              code = 200,
              message = "ok",
              data = listOf(SigninClassDto("1", "大学物理", "08:00", "09:40", 1)),
          )
      )
    }

    assertTrue(verifier.shouldNotify(event))
  }

  @Test
  fun `接口失败时照常通知`() = runTest {
    val verifier: ReminderSigninVerifier = ReminderSigninVerifier {
      Result.failure(IllegalStateException("boom"))
    }

    assertTrue(verifier.shouldNotify(event))
  }

  @Test
  fun `非签到事件直接放行`() = runTest {
    assertTrue(
        ReminderSigninVerifier(response(signStatus = 1))
            .shouldNotify(event.copy(kind = ReminderEventKind.COURSE_OPEN))
    )
  }

  private fun response(signStatus: Int): suspend () -> Result<SigninStatusResponse> = {
    Result.success(
        SigninStatusResponse(
            code = 200,
            message = "ok",
            data = listOf(SigninClassDto("1", "高等数学", "08:00", "09:40", signStatus)),
        )
    )
  }
}
