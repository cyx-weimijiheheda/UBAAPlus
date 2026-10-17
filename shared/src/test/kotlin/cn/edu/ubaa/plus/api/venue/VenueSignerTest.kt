package cn.edu.ubaa.plus.api.venue

import kotlin.test.Test
import kotlin.test.assertEquals

class VenueSignerTest {
  private val signer = VenueSigner()

  @Test
  fun `签名与 Python 客户端一致 - 登录`() {
    assertEquals(
        "794c0836f8df45e9549d661b1355312c",
        signer.sign("/api/login", emptyMap(), 1_700_000_000_000L),
    )
  }

  @Test
  fun `签名与 Python 客户端一致 - GET 带 nocache`() {
    val timestamp = 1_700_000_000_000L
    val params =
        signer.addNoCache(
            mapOf(
                "venueSiteId" to 7700,
                "searchDate" to "2026-09-26",
                "hasReserveInfo" to 1,
            ),
            timestamp,
        )

    assertEquals(
        "a994fb4168a0346babb2cabc8007f528",
        signer.sign("/api/reservation/day/info", params, timestamp),
    )
  }

  @Test
  fun `字符串参数会先 trim 再参与签名`() {
    val timestamp = 1_700_000_000_000L
    assertEquals(
        signer.sign("/api/x", mapOf("name" to "abc"), timestamp),
        signer.sign("/api/x", mapOf("name" to "  abc  "), timestamp),
    )
  }

  @Test
  fun `空字符串、列表与对象不参与签名`() {
    val timestamp = 1_700_000_000_000L
    assertEquals(
        signer.sign("/api/x", emptyMap(), timestamp),
        signer.sign(
            "/api/x",
            mapOf("blank" to "", "list" to listOf(1, 2), "map" to mapOf("a" to 1)),
            timestamp,
        ),
    )
  }
}
