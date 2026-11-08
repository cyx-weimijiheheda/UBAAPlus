package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.model.dto.SigninClassDto
import cn.edu.ubaa.plus.model.dto.SigninStatusResponse
import kotlin.test.Test
import kotlin.test.assertEquals
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
          beginTime = "08:00",
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
  fun `课程不在可签到名单里时只提醒开课不提醒签到`() = runTest {
    // 智慧教室只返回可签到的课；查不到说明这门课不需要签到、也没法在 UBAA 签到。
    val verifier = ReminderSigninVerifier {
      Result.success(
          SigninStatusResponse(
              code = 200,
              message = "ok",
              data = listOf(SigninClassDto("1", "大学物理", "08:00", "09:40", 0)),
          )
      )
    }

    assertEquals(ReminderSigninDecision.SUPPRESS_NOT_SIGNABLE, verifier.decide(event))
    assertFalse(verifier.shouldNotify(event))
  }

  @Test
  fun `可签到名单为空时也不提醒签到`() = runTest {
    val verifier = ReminderSigninVerifier {
      Result.success(SigninStatusResponse(code = 200, message = "ok", data = emptyList()))
    }

    assertFalse(verifier.shouldNotify(event))
  }

  @Test
  fun `接口失败时保守照常提醒`() = runTest {
    val verifier: ReminderSigninVerifier = ReminderSigninVerifier {
      Result.failure(IllegalStateException("boom"))
    }

    assertEquals(ReminderSigninDecision.NOTIFY, verifier.decide(event))
    assertTrue(verifier.shouldNotify(event))
  }

  @Test
  fun `同名课程按上课时间区分`() = runTest {
    val verifier = ReminderSigninVerifier {
      Result.success(
          SigninStatusResponse(
              code = 200,
              message = "ok",
              data =
                  listOf(
                      SigninClassDto("1", "高等数学", "10:00", "11:40", 1),
                      SigninClassDto("2", "高等数学", "08:00", "09:40", 0),
                  ),
          )
      )
    }

    // event 的 beginTime 是 08:00，应该匹配到未签到的那一段。
    assertEquals(ReminderSigninDecision.NOTIFY, verifier.decide(event))
  }

  @Test
  fun `课程名有包含关系时仍能匹配`() = runTest {
    val verifier = ReminderSigninVerifier {
      Result.success(
          SigninStatusResponse(
              code = 200,
              message = "ok",
              data = listOf(SigninClassDto("1", "高等数学（实验班）", "08:00", "09:40", 1)),
          )
      )
    }

    assertEquals(ReminderSigninDecision.SUPPRESS_SIGNED_IN, verifier.decide(event))
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
