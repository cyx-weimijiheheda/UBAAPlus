package cn.edu.ubaa.plus.ui.screens.profile

import kotlin.test.*

class ProfilePrivacyTest {
  @Test
  fun `手机号只保留两端各两位`() {
    assertEquals("13" + "*".repeat(7) + "78", maskProfileValue("13812345678", 2, 2))
  }

  @Test
  fun `证件号只保留前两位和末一位`() {
    assertEquals("11" + "*".repeat(15) + "X", maskProfileValue("11010119900101001X", 2, 1))
  }

  @Test
  fun `银行卡剔除空白后只保留两端四位`() {
    assertEquals("6222********2222", maskProfileValue("6222 0000 1111 2222", 4, 4))
  }

  @Test
  fun `空值和短值不泄露完整内容`() {
    assertEquals("未提供", maskProfileValue(null, 2, 2))
    assertEquals("未提供", maskProfileValue(" ", 2, 2))
    assertEquals("****", maskProfileValue("1234", 2, 2))
    assertEquals("**", maskProfileValue("12", 4, 4))
  }
}
