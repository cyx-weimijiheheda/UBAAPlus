package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.userFacingMessageForCode
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkUtilsTest {

  @Test
  fun gradeErrorCodeHasUserFacingMessage() {
    assertEquals(
        "成绩查询失败，请稍后重试",
        userFacingMessageForCode("grade_error", HttpStatusCode.BadGateway),
    )
  }
}
