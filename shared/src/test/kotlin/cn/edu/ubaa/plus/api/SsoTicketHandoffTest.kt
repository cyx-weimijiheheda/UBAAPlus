package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.api.local.SsoTicketHandoff
import cn.edu.ubaa.plus.testing.TestSettings
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SsoTicketHandoffTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    runTest { localConnectionTestMutex.lock() }
    ConnectionModeStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
    LocalCookieStore.settings = MapSettings()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
  }

  @AfterTest
  fun tearDown() {
    LocalUpstreamClientProvider.reset()
    ConnectionModeStore.clear()
    localConnectionTestMutex.unlock()
  }

  @Test
  fun `CAS 返回 ticket 时拼接到 service callback`() = runTest {
    val engine = MockEngine { request ->
      val service = request.url.parameters["service"].orEmpty()
      respond(
          content = ByteReadChannel(""),
          status = HttpStatusCode.Found,
          headers =
              headersOf(
                  HttpHeaders.Location,
                  "$service?ticket=ST-test-ticket",
              ),
      )
    }
    LocalUpstreamClientProvider.isolatedClientFactory = { followRedirects, _ ->
      HttpClient(engine) { this.followRedirects = followRedirects }
    }

    val result = SsoTicketHandoff.authenticatedUrl("https://it.buaa.edu.cn/home")

    assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
    assertTrue(result.getOrThrow().contains("ticket=ST-test-ticket"))
    assertTrue(result.getOrThrow().contains("cas_login"))
  }

  @Test
  fun `图书馆使用 rosetta 作为 CAS service`() = runTest {
    var service = ""
    val engine = MockEngine { request ->
      service = request.url.parameters["service"].orEmpty()
      respond(
          content = ByteReadChannel(""),
          status = HttpStatusCode.Found,
          headers = headersOf(HttpHeaders.Location, "$service?ticket=ST-lib-ticket"),
      )
    }
    LocalUpstreamClientProvider.isolatedClientFactory = { followRedirects, _ ->
      HttpClient(engine) { this.followRedirects = followRedirects }
    }

    val result = SsoTicketHandoff.authenticatedUrl("https://space.lib.buaa.edu.cn/space/")

    assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
    assertTrue(service.endsWith("/meta-local/opac/cas/rosetta"), service)
    assertTrue(result.getOrThrow().contains("ticket=ST-lib-ticket"))
  }

  @Test
  fun `非 BUAA 地址直接返回原地址`() = runTest {
    val result = SsoTicketHandoff.authenticatedUrl("https://example.com/pay")

    assertTrue(result.isSuccess)
    assertTrue(result.getOrThrow() == "https://example.com/pay")
  }

  @Test
  fun `CAS 返回登录页时失败`() = runTest {
    val engine = MockEngine {
      respond(
          content = ByteReadChannel("<html>CAS Login</html>"),
          status = HttpStatusCode.OK,
          headers = headersOf(HttpHeaders.ContentType, "text/html"),
      )
    }
    LocalUpstreamClientProvider.isolatedClientFactory = { followRedirects, _ ->
      HttpClient(engine) { this.followRedirects = followRedirects }
    }

    val result = SsoTicketHandoff.authenticatedUrl("https://it.buaa.edu.cn/home")

    assertTrue(result.isFailure)
  }
}
