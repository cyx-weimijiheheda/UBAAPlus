package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.IclassSigninLink
import cn.edu.ubaa.plus.api.feature.IclassSocketInfoDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IclassSigninLinkTest {

  @Test
  fun `按学校二维码格式拼链接`() {
    // 与学校二维码一致：8081、courseSchedId、整数时间戳。
    assertEquals(
        "http://iclass.buaa.edu.cn:8081/app/course/stu_scan_sign.action" +
            "?courseSchedId=2530645&timestamp=1795132596158",
        IclassSigninLink.build(courseSchedId = "2530645", timestamp = 1795132596158L),
    )
  }

  @Test
  fun `带用户 ID 时追加 id 参数`() {
    val link = IclassSigninLink.build("2530645", 1795132596158L, userId = "229626")
    assertTrue(link.endsWith("&id=229626"))
  }

  @Test
  fun `时间戳必须输出整数而不是字符串`() {
    // 上游把字符串时间戳当无效参数并静默丢弃，这里锁死格式。
    val link = IclassSigninLink.build("1", 1234567890L)
    assertTrue(link.contains("timestamp=1234567890"))
    assertFalse(link.contains("timestamp=\"1234567890\""))
  }

  @Test
  fun `排课 ID 为 0 或空表示当前没有课`() {
    assertFalse(IclassSigninLink.isUsableCourseSchedId("0"))
    assertFalse(IclassSigninLink.isUsableCourseSchedId(""))
    assertFalse(IclassSigninLink.isUsableCourseSchedId(null))
    assertFalse(IclassSigninLink.isUsableCourseSchedId("  "))
    assertTrue(IclassSigninLink.isUsableCourseSchedId("2530645"))
  }

  @Test
  fun `课时信息判断是否在课上`() {
    assertFalse(IclassSocketInfoDto(courseSchedId = "0").hasCurrentCourse)
    assertTrue(IclassSocketInfoDto(courseSchedId = "2530645").hasCurrentCourse)
  }
}
