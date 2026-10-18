package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.DormElectricityApi
import cn.edu.ubaa.plus.api.feature.LocalDormElectricityApiBackend
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DormElectricityApiTest {
  private lateinit var backend: LocalDormElectricityApiBackend
  private lateinit var api: DormElectricityApi

  @BeforeTest
  fun setup() {
    val engine = MockEngine { request ->
      val path = request.url.encodedPath
      when {
        path == "/PubBuaa/QueryIdData" ->
            respond(
                content =
                    ByteReadChannel(
                        """
                        [{"id":40038,"campus":"学院路校区","building":"3号楼","floor":"1",
                          "room":"101","name":"3-1-101","meterNo":"*5824","address":"3-1-101"}]
                        """
                            .trimIndent()
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        path == "/BuaaPay/Meter" ->
            respond(
                content =
                    ByteReadChannel(
                        """
                        {"id":40038,"compus":"学院路校区","building":"3号楼","room":"101",
                         "address":"3-1-101","remain":171,"price":0.48,"payStatus":1,
                         "readingTime":"2026-10-18T00:00:00","ct":2,"payUrl":null,
                         "serial":null,"money":0.0}
                        """
                            .trimIndent()
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        path == "/PubBuaa" ->
            respond(
                content =
                    ByteReadChannel(
                        """
                        <html><body>
                        <h3>最近 5 次购电</h3>
                        <table><tbody>
                        <tr><td>日期</td><td>电量</td><td>金额</td><td>收费员</td></tr>
                        <tr><td>2026年10月17日 11:31:08</td><td>125</td><td>¥ 60.00</td><td>校园支付平台</td></tr>
                        </tbody></table>
                        </body></html>
                        """
                            .trimIndent()
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        path == "/BuaaPay/Pay" ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://pay.example.com/order/1"),
            )
        else -> respondError(HttpStatusCode.NotFound)
      }
    }
    backend =
        LocalDormElectricityApiBackend(
            clientProvider = {
              HttpClient(engine) {
                followRedirects = false
                expectSuccess = false
              }
            }
        )
    backend.clearRoomCache()
    api = DormElectricityApi(backend)
  }

  @Test
  fun `解析房间、电表、购电记录与支付地址`() = runTest {
    val rooms = api.getRooms()
    assertTrue(rooms.isSuccess, rooms.exceptionOrNull()?.message.orEmpty())
    assertEquals("3号楼", rooms.getOrThrow().single().building)

    val meter = api.getMeterInfo(40038)
    assertTrue(meter.isSuccess, meter.exceptionOrNull()?.message.orEmpty())
    assertEquals(171.0, meter.getOrThrow().remain)
    assertEquals(2, meter.getOrThrow().ct)

    val topUps = api.getTopUps(40038)
    assertTrue(topUps.isSuccess, topUps.exceptionOrNull()?.message.orEmpty())
    assertEquals(1, topUps.getOrThrow().size)
    assertEquals("125", topUps.getOrThrow().single().power)

    val pay = api.recharge(40038, writePower = 10)
    assertTrue(pay.isSuccess, pay.exceptionOrNull()?.message.orEmpty())
    assertEquals("https://pay.example.com/order/1", pay.getOrThrow())
  }

  @Test
  fun `校外访问返回 302 时提示校园网`() = runTest {
    val engine = MockEngine {
      respond(
          content = ByteReadChannel(""),
          status = HttpStatusCode.Found,
          headers = headersOf(HttpHeaders.Location, "https://sso.buaa.edu.cn/login"),
      )
    }
    val offlineBackend =
        LocalDormElectricityApiBackend(
            clientProvider = {
              HttpClient(engine) {
                followRedirects = false
                expectSuccess = false
              }
            }
        )
    offlineBackend.clearRoomCache()

    val result = DormElectricityApi(offlineBackend).getRooms()

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("校园网"))
  }
}
