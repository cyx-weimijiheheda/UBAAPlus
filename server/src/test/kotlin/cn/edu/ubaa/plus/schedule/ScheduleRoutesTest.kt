package cn.edu.ubaa.plus.schedule

import cn.edu.ubaa.plus.auth.LoginException
import cn.edu.ubaa.plus.auth.UnsupportedAcademicPortalException
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleRoutesTest {

  @Test
  fun loginExceptionMapsToInvalidToken() {
    val (status, code) = scheduleErrorResponse(LoginException("session expired"))

    assertEquals(HttpStatusCode.Unauthorized, status)
    assertEquals("invalid_token", code)
  }

  @Test
  fun unsupportedPortalMapsToNotImplemented() {
    val (status, code) =
        scheduleErrorResponse(UnsupportedAcademicPortalException("unsupported portal"))

    assertEquals(HttpStatusCode.NotImplemented, status)
    assertEquals("unsupported_portal", code)
  }

  @Test
  fun genericExceptionMapsToScheduleError() {
    val (status, code) = scheduleErrorResponse(IllegalStateException("boom"))

    assertEquals(HttpStatusCode.BadGateway, status)
    assertEquals("schedule_error", code)
  }
}
