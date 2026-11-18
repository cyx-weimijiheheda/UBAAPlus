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

  private fun useEngine(engine: MockEngine) {
    LocalUpstreamClientProvider.reset()
    LocalUpstreamClientProvider.clientFactory = { redirects ->
      HttpClient(engine) { followRedirects = redirects }
    }
    api = ItPortalApi(LocalItPortalApiBackend())
  }

  @Test
  fun `门户实际信封解析动态ID和零余额`() = runTest {
    useEngine(
        MockEngine { request ->
          when (request.url.encodedPath) {
            "/personal/frontend/data/items" ->
                respond("""{"e":0,"d":{"data":[{"key":"net.netFee","id":"a+b&c"}]}}""")
            "/personal/frontend/data/detail" -> {
              assertEquals("a+b&c", request.url.parameters["id"])
              respond("""{"e":0,"d":{"data":{"value":0,"unit":"元"}}}""")
            }
            else -> error("不应请求网关余额")
          }
        }
    )
    assertEquals("0", api.findPersonalValue("net.netFee").getOrThrow().value)
  }

  @Test
  fun `WebVPN未激活门户时引导一次并重读网费`() = runTest {
    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    var activated = false
    var activations = 0
    val hosts = mutableListOf<String>()
    useEngine(
        MockEngine { request ->
          assertEquals("d.buaa.edu.cn", request.url.host)
          val original =
              io.ktor.http.Url(
                  cn.edu.ubaa.plus.api.local.LocalWebVpnSupport.fromWebVpnUrl(
                      request.url.toString()
                  )
              )
          hosts += original.host
          when (original.encodedPath) {
            "/personal/frontend/data/items" -> {
              assertEquals("XMLHttpRequest", request.headers["X-Requested-With"])
              if (activated)
                  respond("""{"e":0,"d":{"data":[{"key":"net.netFee","id":"fee-id"}]}}""")
              else respond("""{"e":10013}""")
            }
            "/manage/common/login/index" -> {
              activations++
              respond(
                  "",
                  HttpStatusCode.Found,
                  headersOf(HttpHeaders.Location, "https://sso.buaa.edu.cn/login?service=fixture"),
              )
            }
            "/login" ->
                respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.Location,
                        "https://it.buaa.edu.cn/manage/common/cas_login/30001?ticket=fixture",
                    ),
                )
            "/manage/common/cas_login/30001" ->
                respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://it.buaa.edu.cn/home/"),
                )
            "/home/" -> {
              activated = true
              respond("<!DOCTYPE html><html>门户首页</html>")
            }
            "/personal/frontend/data/detail" ->
                respond("""{"e":0,"d":{"data":{"value":"18.50"}}}""")
            else -> error("非预期请求")
          }
        }
    )
    assertEquals("18.50", api.findPersonalValue("net.netFee").getOrThrow().value)
    assertEquals(1, activations)
    assertTrue(hosts.none { it == "gw.buaa.edu.cn" })
  }

  @Test
  fun `门户业务错误不能当成空余额成功`() = runTest {
    useEngine(MockEngine { respond("""{"e":500,"d":{"data":{"value":"0"}}}""") })
    assertTrue(api.getPersonalDetail("id").isFailure)
  }

  @Test
  fun `门户会话仍失效不重复引导`() = runTest {
    var activations = 0
    useEngine(
        MockEngine { request ->
          when (request.url.encodedPath) {
            "/manage/common/login/index" -> {
              activations++
              respond(
                  "",
                  HttpStatusCode.Found,
                  headersOf(HttpHeaders.Location, "https://it.buaa.edu.cn/home/"),
              )
            }
            "/home/" -> respond("<html>门户首页</html>")
            else -> respond("""{"e":10013}""")
          }
        }
    )
    assertTrue(api.findPersonalValue("net.netFee").isFailure)
    assertEquals(1, activations)
  }
}
