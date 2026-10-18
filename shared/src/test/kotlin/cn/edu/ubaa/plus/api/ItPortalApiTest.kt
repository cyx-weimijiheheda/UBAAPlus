package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.ItPortalApi
import cn.edu.ubaa.plus.api.feature.LocalItPortalApiBackend
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.testing.TestSettings
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ItPortalApiTest {
  private lateinit var api: ItPortalApi
  private var lastRequest = ""

  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    runTest { localConnectionTestMutex.lock() }
    ConnectionModeStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
    LocalCookieStore.settings = MapSettings()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    LocalUpstreamClientProvider.reset()

    val engine = MockEngine { request ->
      lastRequest = request.url.toString()
      val path = request.url.encodedPath
      when (path) {
        "/personal/frontend/data/items" ->
            respond(
                content =
                    ByteReadChannel(
                        """{"data":[{"key":"net.netFee","id":"11","name":"网费"},{"key":"book.bookNum","id":"12","name":"待还图书"}]}"""
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        "/personal/frontend/data/detail" ->
            respond(
                content =
                    ByteReadChannel(
                        """{"data":{"value":"12.50","url":"https://example.com/net"}}"""
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        "/buaa/frontend/card/balance" ->
            respond(
                content = ByteReadChannel("""{"data":{"value":"88.00","unit":"元"}}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        else -> respondError(HttpStatusCode.NotFound)
      }
    }
    LocalUpstreamClientProvider.clientFactory = { followRedirects ->
      HttpClient(engine) { this.followRedirects = followRedirects }
    }
    api = ItPortalApi(LocalItPortalApiBackend())
  }

  @AfterTest
  fun tearDown() {
    LocalUpstreamClientProvider.reset()
    ConnectionModeStore.clear()
    localConnectionTestMutex.unlock()
  }

  @Test
  fun `解析个人数据项与详情`() = runTest {
    val items = api.getPersonalItems()
    assertTrue(items.isSuccess, "${items.exceptionOrNull()?.message.orEmpty()} last=$lastRequest")
    assertEquals("net.netFee", items.getOrThrow().first().key)

    val detail = api.findPersonalValue("net.netFee")
    assertTrue(detail.isSuccess, "${detail.exceptionOrNull()?.message.orEmpty()} last=$lastRequest")
    assertEquals("12.50", detail.getOrThrow().value)
    assertEquals("https://example.com/net", detail.getOrThrow().url)
  }

  @Test
  fun `解析校园卡余额`() = runTest {
    val balance = api.getCardBalance()
    assertTrue(
        balance.isSuccess,
        "${balance.exceptionOrNull()?.message.orEmpty()} last=$lastRequest",
    )
    assertEquals(88.0, balance.getOrThrow().value)
  }
}
