package cn.edu.ubaa.plus.ui.screens.libbook

import kotlin.test.Test
import kotlin.test.assertEquals

class LibBookQrQueryParserTest {
  @Test
  fun `parses query from seat qr url`() {
    assertEquals(
        mapOf("area_id" to "8", "seat_id" to "101"),
        parseLibBookQrParams(
            "https://booking.lib.buaa.edu.cn/h5/booking?area_id=8&seat_id=101#/detail"
        ),
    )
  }

  @Test
  fun `parses plain query string and decodes values`() {
    assertEquals(
        mapOf("query" to "a b", "seat_id" to "101"),
        parseLibBookQrParams("query=a%20b&seat_id=101"),
    )
  }

  @Test
  fun `treats plain token as query`() {
    assertEquals(mapOf("query" to "token-1"), parseLibBookQrParams("token-1"))
  }

  @Test
  fun `blank input yields empty map`() {
    assertEquals(emptyMap(), parseLibBookQrParams("  "))
  }
}
