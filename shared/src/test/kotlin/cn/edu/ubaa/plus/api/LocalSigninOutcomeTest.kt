package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.SigninApi
import cn.edu.ubaa.plus.api.local.*
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.testing.TestSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class LocalSigninOutcomeTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    ConnectionRuntime.clearSelectedMode()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    LocalAuthSessionStore.save(
        LocalAuthSession(
            "synthetic",
            UserData("Test", "synthetic"),
            "2026-11-20T00:00:00Z",
            "2026-11-20T00:00:00Z",
        )
    )
    LocalUpstreamClientProvider.reset()
  }

  @AfterTest
  fun cleanup() {
    LocalUpstreamClientProvider.reset()
    LocalAuthSessionStore.clearAllScopes()
    ConnectionRuntime.clearSelectedMode()
  }

  private fun backend(
      reply: String,
      signed: Int,
      status: HttpStatusCode = HttpStatusCode.OK,
      onSubmit: () -> Unit,
  ): LocalSigninApiBackend {
    val engine = MockEngine { request ->
      when {
        request.url.port == 8346 ->
            respond(
                "",
                HttpStatusCode.Found,
                headersOf(
                    HttpHeaders.Location,
                    "https://iclass.buaa.edu.cn:8346/?loginName=fixture&type=jumpMyCenter",
                ),
            )
        request.url.encodedPath.endsWith("login.action") ->
            respond(
                """{"STATUS":0,"result":{"id":"fixture-user","sessionId":"fixture-session"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        request.url.encodedPath.endsWith("stu_auto_sign.action") -> {
          onSubmit()
          respond(reply, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        request.url.encodedPath.endsWith("get_stu_course_sched.action") ->
            respond(
                """{"STATUS":0,"result":[{"id":"fixture-course","courseName":"Test","signStatus":$signed}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        else -> error("Unexpected mock route")
      }
    }
    LocalUpstreamClientProvider.clientFactory = { HttpClient(engine) { followRedirects = it } }
    return LocalSigninApiBackend()
  }

  @Test
  fun `空回执且仍未签到时不误报成功也不重发`() = runTest {
    var submits = 0
    val api = SigninApi(backend("\r\n", 0) { submits++ })
    assertTrue(api.performSignin("fixture-course", "123").isFailure)
    assertTrue(api.performSignin("fixture-course", "123").isFailure)
    assertEquals(1, submits)
  }

  @Test
  fun `空回执后仅学校明确的已签到状态可以被核实`() = runTest {
    var submits = 0
    val api = SigninApi(backend("\r\n", 1) { submits++ })
    val result = api.performSignin("fixture-course", "123").getOrThrow()
    assertTrue(result.success)
    assertEquals("已核实当前课程为已签到", result.message)
    assertEquals(1, submits)
  }

  @Test
  fun `缺少业务字段及非成功HTTP都不能作为明确拒绝`() = runTest {
    for ((body, status) in
        listOf(
            "{}" to HttpStatusCode.OK,
            """{"STATUS":0,"result":{}}""" to HttpStatusCode.OK,
            """{"STATUS":0,"result":{"stuSignStatus":1}}""" to HttpStatusCode.BadGateway,
        )) {
      val result = backend(body, 0, status) {}.performSignin("fixture-course", "123")
      assertTrue(result.isFailure)
    }
  }

  @Test
  fun `明确学校拒绝保留原提示`() = runTest {
    val result =
        backend("""{"STATUS":1,"ERRMSG":"二维码已过期"}""", 0) {}
            .performSignin("fixture-course", "123")
            .getOrThrow()
    assertFalse(result.success)
    assertEquals("签到失败：二维码已过期", result.message)
  }

  @Test
  fun `取消签到不会被吞成普通失败`() = runTest {
    val api = backend("", 0) { throw CancellationException("fixture") }
    assertFailsWith<CancellationException> { api.performSignin("fixture-course", "123") }
  }
}
