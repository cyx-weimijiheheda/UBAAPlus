package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.CampusNetworkApi
import cn.edu.ubaa.plus.api.feature.LocalCampusNetworkApiBackend
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

class CampusNetworkApiTest {
  private lateinit var api: CampusNetworkApi

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
      when (request.url.host) {
        "gw.buaa.edu.cn" ->
            respond(
                content =
                    ByteReadChannel(
                        """{"error":"ok","user_name":"22373333","real_name":"测试用户",
                           "online_ip":"10.0.0.2","sum_bytes":1073741824,"remain_bytes":2147483648,
                           "products_name":"校园网套餐","user_balance":12.5,"wallet_balance":3.0}"""
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        "sso.buaa.edu.cn" ->
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.OK,
            )
        "zfw.buaa.edu.cn" ->
            respond(
                content =
                    ByteReadChannel(
                        """
                        <table><thead><tr><th data-col-seq="1">产品名称</th><th data-col-seq="2">已用流量</th><th data-col-seq="3">免费流量剩余(不含套餐)</th><th data-col-seq="4">计费流量剩余(不含套餐)</th></tr></thead><tbody><tr><td data-col-seq="1">校园网</td><td data-col-seq="2">2.5G</td><td data-col-seq="3">20G</td><td data-col-seq="4">0G</td></tr></tbody></table>
                        """
                            .trimIndent()
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        else -> respondError(HttpStatusCode.NotFound)
      }
    }
    LocalUpstreamClientProvider.clientFactory = { followRedirects ->
      HttpClient(engine) { this.followRedirects = followRedirects }
    }
    api = CampusNetworkApi(LocalCampusNetworkApiBackend())
  }

  @AfterTest
  fun tearDown() {
    LocalUpstreamClientProvider.reset()
    ConnectionModeStore.clear()
    localConnectionTestMutex.unlock()
  }

  @Test
  fun `解析 SRun 在线信息`() = runTest {
    val result = api.getSrunInfo()
    assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
    val info = result.getOrThrow()
    assertEquals("22373333", info?.userName)
    assertEquals(12.5, info?.userBalance)
    assertEquals("校园网套餐", info?.productsName)
  }

  @Test
  fun `解析 app 北航流量页面`() = runTest {
    val result = api.getTraffic()
    assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
    val traffic = result.getOrThrow()
    assertEquals("20", traffic.products.single().freeRemaining.value)
    assertEquals("2.5", traffic.products.single().used.value)
  }
}
