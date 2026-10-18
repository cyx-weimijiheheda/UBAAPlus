package cn.edu.ubaa.plus.api.venue

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LocalVenueApiTest {
  @BeforeTest
  fun setup() {
    LocalCookieStore.settings = MapSettings()
  }

  @AfterTest
  fun tearDown() {
    LocalCookieStore.clearAllScopes()
  }

  @Test
  fun `会员码使用签名 GET 请求并解析二维码`() = runTest {
    LocalCookieStore.storage(ConnectionMode.DIRECT)
        .addCookie(
            Url("https://cgyy.buaa.edu.cn/"),
            Cookie(name = "sso_buaa_token", value = "sso-token"),
        )

    val engine = MockEngine { request ->
      when (request.url.encodedPath) {
        "/venue-server/sso/manageLogin" ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
            )
        "/venue-server/api/login" -> {
          assertTrue(request.headers["sso-token"] == "sso-token")
          respond(
              content =
                  ByteReadChannel(
                      """{"code":200,"data":{"token":{"access_token":"access"},"roles":[]}}"""
                  ),
              status = HttpStatusCode.OK,
              headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        }
        "/venue-server/api/vip/code/view" -> {
          assertTrue(request.url.parameters["isNeedReservation"] == "1")
          assertTrue(request.url.parameters["isNeedVipCard"] == "1")
          assertTrue(request.url.parameters["qrRgb"] == "10,45,103")
          assertNotNull(request.url.parameters["nocache"])
          assertNotNull(request.headers["app-key"])
          assertNotNull(request.headers["timestamp"])
          assertNotNull(request.headers["sign"])
          respond(
              content =
                  ByteReadChannel(
                      """
                      {"code":200,"data":{
                        "qrCode":"iVBORw0KGgo=",
                        "dueDate":"2026-10-17 21:40:00",
                        "isDynamicCode":true,
                        "currDate":"2026-10-17 21:39:50"
                      }}
                      """
                          .trimIndent()
                  ),
              status = HttpStatusCode.OK,
              headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        }
        else -> error("Unexpected url: ${request.url}")
      }
    }

    val api = LocalVenueApi(clientProvider = { HttpClient(engine) })
    val result = api.getMembershipCode()

    assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
    val code = result.getOrThrow()
    assertNotNull(code)
    assertTrue(code.qrCode == "iVBORw0KGgo=")
    assertTrue(code.isDynamicCode)
  }

  @Test
  fun `预约记录使用分页 GET 请求`() = runTest {
    LocalCookieStore.storage(ConnectionMode.DIRECT)
        .addCookie(
            Url("https://cgyy.buaa.edu.cn/"),
            Cookie(name = "sso_buaa_token", value = "sso-token"),
        )

    val engine = MockEngine { request ->
      when (request.url.encodedPath) {
        "/venue-server/sso/manageLogin" ->
            respond(content = ByteReadChannel(""), status = HttpStatusCode.OK)
        "/venue-server/api/login" ->
            respond(
                content =
                    ByteReadChannel(
                        """{"code":200,"data":{"token":{"access_token":"access"},"roles":[]}}"""
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        "/venue-server/api/orders/mine" -> {
          assertTrue(request.url.parameters["page"] == "0")
          assertTrue(request.url.parameters["size"] == "20")
          assertNotNull(request.url.parameters["nocache"])
          assertNotNull(request.headers["sign"])
          respond(
              content =
                  ByteReadChannel(
                      """
                      {"code":200,"data":{"totalElements":1,"content":[
                        {"id":101,"campusStr":"学院路校区","venueName":"体育馆",
                         "siteName":"羽毛球","reservationDate":"2026-10-20",
                         "reservationDateDetail":"2026-10-20 19:00-20:00",
                         "amountFee":25.0,"payStatus":2,"orderStatus":1}
                      ]}}
                      """
                          .trimIndent()
                  ),
              status = HttpStatusCode.OK,
              headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        }
        else -> error("Unexpected url: ${request.url}")
      }
    }

    val api = LocalVenueApi(clientProvider = { HttpClient(engine) })
    val result = api.getMyOrders(page = 0, size = 20)

    assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
    val page = result.getOrThrow()
    assertTrue(page.totalElements == 1)
    assertTrue(page.records.single().venueName == "体育馆")
    assertTrue(page.records.single().payStatus == 2)
  }
}
