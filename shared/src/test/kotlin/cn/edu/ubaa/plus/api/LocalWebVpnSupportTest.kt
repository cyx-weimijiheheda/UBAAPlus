package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.local.LocalWebVpnSupport
import cn.edu.ubaa.plus.api.local.localUpstreamUrl
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalWebVpnSupportTest {
  @BeforeTest
  fun setup() {
    ConnectionModeStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
  }

  @Test
  fun `WebVPN还原保留尾斜杠重复斜杠与登录片段`() {
    listOf(
            "https://i.buaa.edu.cn/",
            "http://i.buaa.edu.cn/web/#/login?type=0&token=fixture-token",
            "https://i.buaa.edu.cn/web/?q=fixture%2Bquery#/login?type=0&token=fixture-token",
            "https://i.buaa.edu.cn/a//b/?q=test%2Bvalue",
        )
        .forEach { original ->
          assertEquals(
              original,
              LocalWebVpnSupport.fromWebVpnUrl(LocalWebVpnSupport.toWebVpnUrl(original)),
          )
        }
  }

  @Test
  fun `转换后的片段令牌不进入HTTP查询`() {
    val wrapped =
        LocalWebVpnSupport.toWebVpnUrl(
            "http://i.buaa.edu.cn/web/#/login?type=0&token=fixture-token"
        )
    assertEquals(
        "https://d.buaa.edu.cn/http/77726476706e69737468656265737421f9b94389263126557a1dc7af96/web/#/login?type=0&token=fixture-token",
        wrapped,
    )
  }

  @Test
  fun `webvpn codec round-trips https upstream url`() {
    val original = "https://spoc.buaa.edu.cn/spocnewht/cas?token=test-token"

    val wrapped = LocalWebVpnSupport.toWebVpnUrl(original)

    assertTrue(wrapped.startsWith("https://d.buaa.edu.cn/https/"))
    assertEquals(original, LocalWebVpnSupport.fromWebVpnUrl(wrapped))
  }

  @Test
  fun `webvpn codec round-trips custom port url`() {
    val original = "http://iclass.buaa.edu.cn:8081/app/course/stu_scan_sign.action?id=1"

    val wrapped = LocalWebVpnSupport.toWebVpnUrl(original)

    assertTrue(wrapped.startsWith("https://d.buaa.edu.cn/http-8081/"))
    assertEquals(original, LocalWebVpnSupport.fromWebVpnUrl(wrapped))
  }

  @Test
  fun `UC login redirect downgraded to http is upgraded back to https`() {
    assertEquals(
        "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fuc.buaa.edu.cn%2Fapi%2Flogin",
        LocalWebVpnSupport.normalizeRedirectUrl(
            "http://sso.buaa.edu.cn/login?service=https%3A%2F%2Fuc.buaa.edu.cn%2Fapi%2Flogin"
        ),
    )
  }

  @Test
  fun `webvpn http tunnel redirect is upgraded to https tunnel`() {
    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    ConnectionRuntime.resolveSelectedMode()

    val wrapped = LocalWebVpnSupport.toWebVpnUrl("http://sso.buaa.edu.cn/login?service=x")
    assertTrue(wrapped.startsWith("https://d.buaa.edu.cn/http/"))
    assertTrue(
        LocalWebVpnSupport.normalizeRedirectUrl(wrapped).startsWith("https://d.buaa.edu.cn/https/")
    )
    assertTrue(
        LocalWebVpnSupport.normalizeRedirectUrl("https://sso.buaa.edu.cn/login")
            .startsWith("https://d.buaa.edu.cn/https/")
    )
  }

  @Test
  fun `upstream url helper wraps direct upstream when current mode is webvpn`() {
    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    ConnectionRuntime.resolveSelectedMode()

    val resolved = localUpstreamUrl("https://uc.buaa.edu.cn/api/uc/status")

    assertTrue(resolved.startsWith("https://d.buaa.edu.cn/https/"))
    assertEquals("https://uc.buaa.edu.cn/api/uc/status", LocalWebVpnSupport.fromWebVpnUrl(resolved))
  }
}
