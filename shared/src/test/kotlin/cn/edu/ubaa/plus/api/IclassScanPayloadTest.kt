package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.parseIclassSigninPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IclassScanPayloadTest {

  @Test
  fun `解析完整签到 URL`() {
    val payload =
        parseIclassSigninPayload(
            "http://iclass.buaa.edu.cn:8081/eschool/app/course/stu_scan_sign.action" +
                "?courseSchedId=12345&timestamp=1713600123456"
        )

    assertEquals("12345", payload?.courseSchedId)
    assertEquals("1713600123456", payload?.timestamp)
  }

  @Test
  fun `参数名大小写不敏感且忽略非数字 timestamp`() {
    val payload =
        parseIclassSigninPayload(
            "https://iclass.buaa.edu.cn/app/course/stu_scan_sign.action" +
                "?COURSESCHEDID=abc-1&TimeStamp=not-a-number"
        )

    assertEquals("abc-1", payload?.courseSchedId)
    assertNull(payload?.timestamp)
  }

  @Test
  fun `支持带 hash 的 URL 和纯 query`() {
    val withHash =
        parseIclassSigninPayload(
            "https://iclass.buaa.edu.cn/#/pages/table/signIn?courseSchedId=42&timestamp=99"
        )
    val queryOnly = parseIclassSigninPayload("courseSchedId=42&timestamp=99")

    assertEquals("42", withHash?.courseSchedId)
    assertEquals("99", withHash?.timestamp)
    assertEquals("42", queryOnly?.courseSchedId)
  }

  @Test
  fun `支持 JSON 形式的签到参数`() {
    val payload =
        parseIclassSigninPayload("""{"courseSchedId":"s-9","timestamp":"1713600000123"}""")

    assertEquals("s-9", payload?.courseSchedId)
    assertEquals("1713600000123", payload?.timestamp)
  }

  @Test
  fun `缺少课程安排 ID 时返回 null`() {
    assertNull(parseIclassSigninPayload("https://example.com/foo?timestamp=1"))
    assertNull(parseIclassSigninPayload("随便一段文字"))
    assertNull(parseIclassSigninPayload(""))
  }
}
