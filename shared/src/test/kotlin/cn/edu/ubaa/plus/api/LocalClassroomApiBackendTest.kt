package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.core.DefaultApiFactory
import cn.edu.ubaa.plus.api.feature.ClassroomApi
import cn.edu.ubaa.plus.api.local.LocalAuthSession
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.model.dto.ClassroomQueryResponse
import cn.edu.ubaa.plus.model.dto.UserData
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class LocalClassroomApiBackendTest {
  private val json = Json { ignoreUnknownKeys = true }

  @BeforeTest
  fun setup() {
    runTest { localConnectionTestMutex.lock() }
    cn.edu.ubaa.plus.testing.TestSettings.installInMemorySettings()
    ConnectionModeStore.settings = MapSettings()
    LocalAuthSessionStore.settings = MapSettings()
    LocalCookieStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    ConnectionRuntime.apiFactoryProvider = { DefaultApiFactory }
    LocalAuthSessionStore.save(
        LocalAuthSession(
            username = "22373333",
            user = UserData(name = "Test User", schoolid = "22373333"),
            authenticatedAt = "2026-04-20T08:00:00Z",
            lastActivity = "2026-04-20T08:30:00Z",
        )
    )
    LocalUpstreamClientProvider.reset()
  }

  @AfterTest
  fun tearDown() {
    LocalUpstreamClientProvider.reset()
    LocalAuthSessionStore.clearAllScopes()
    LocalCookieStore.clearAllScopes()
    ConnectionRuntime.clearSelectedMode()
    ConnectionRuntime.apiFactoryProvider = { DefaultApiFactory }
    localConnectionTestMutex.unlock()
  }

  @Test
  fun `classroom api uses direct upstream backend to query classrooms`() = runTest {
    val engine = MockEngine { request ->
      when {
        request.url.host == "sso.buaa.edu.cn" && request.url.encodedPath == "/login" ->
            respond(
                content = ByteReadChannel.Empty,
                status = HttpStatusCode.OK,
            )
        request.url.host == "app.buaa.edu.cn" &&
            request.url.encodedPath == "/buaafreeclass/wap/default/search1" -> {
          assertTrue(request.headers[HttpHeaders.UserAgent].orEmpty().contains("Mozilla/5.0"))
          assertEquals("XMLHttpRequest", request.headers["X-Requested-With"])
          assertEquals(
              "https://app.buaa.edu.cn/site/classRoomQuery/index",
              request.headers[HttpHeaders.Referrer],
          )
          assertEquals("1", request.url.parameters["xqid"])
          assertEquals("", request.url.parameters["floorid"])
          assertEquals("2026-04-20", request.url.parameters["date"])
          respond(
              content =
                  ByteReadChannel(
                      json.encodeToString(
                          ClassroomQueryResponse(
                              e = 0,
                              m = "ok",
                              d =
                                  cn.edu.ubaa.plus.model.dto.ClassroomData(
                                      list =
                                          mapOf(
                                              "主楼" to
                                                  listOf(
                                                      cn.edu.ubaa.plus.model.dto.ClassroomInfo(
                                                          id = "1",
                                                          floorid = "101",
                                                          name = "主M101",
                                                          kxsds = "1,2",
                                                      )
                                                  )
                                          )
                                  ),
                          )
                      )
                  ),
              status = HttpStatusCode.OK,
              headers = headersOf(HttpHeaders.ContentType, "application/json"),
          )
        }
        else -> error("Unexpected url: ${request.url}")
      }
    }
    useMockUpstream(engine)

    val result = ClassroomApi().queryClassrooms(xqid = 1, date = "2026-04-20")

    assertTrue(result.isSuccess)
    assertEquals("主M101", result.getOrNull()?.d?.list?.get("主楼")?.singleOrNull()?.name)
  }

  @Test
  fun `classroom api clears local session when upstream redirects to sso and uc session is invalid`() =
      runTest {
        val engine = MockEngine { request ->
          when {
            request.url.host == "sso.buaa.edu.cn" && request.url.encodedPath == "/login" ->
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.OK,
                )
            request.url.host == "uc.buaa.edu.cn" && request.url.encodedPath == "/api/uc/status" ->
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.Unauthorized,
                )
            request.url.host == "app.buaa.edu.cn" &&
                request.url.encodedPath == "/buaafreeclass/wap/default/search1" ->
                respond(
                    content = ByteReadChannel.Empty,
                    status = HttpStatusCode.Found,
                    headers =
                        headersOf(
                            HttpHeaders.Location,
                            "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fapp.buaa.edu.cn",
                        ),
                )
            else -> error("Unexpected url: ${request.url}")
          }
        }
        useMockUpstream(engine)

        val result = ClassroomApi().queryClassrooms(xqid = 1, date = "2026-04-20")

        assertTrue(result.isFailure)
        val exception = assertIs<ApiCallException>(result.exceptionOrNull())
        assertEquals("unauthenticated", exception.code)
        assertNull(LocalAuthSessionStore.get())
      }

  @Test
  fun `expired classroom cookie resynchronizes once without clearing a valid main session`() =
      runTest {
        var syncs = 0
        var reads = 0
        useMockUpstream(
            MockEngine { request ->
              when (request.url.host) {
                "sso.buaa.edu.cn" -> {
                  syncs++
                  respond("", HttpStatusCode.OK)
                }
                "app.buaa.edu.cn" -> {
                  reads++
                  if (reads == 1)
                      respond(
                          "",
                          HttpStatusCode.Found,
                          headersOf(HttpHeaders.Location, "https://sso.buaa.edu.cn/login"),
                      )
                  else respond("""{"e":0,"m":"ok","d":{"list":{}}}""", HttpStatusCode.OK)
                }
                "uc.buaa.edu.cn" ->
                    respond(
                        """{"code":0,"data":{"schoolid":"22373333","name":"Test User"}}""",
                        HttpStatusCode.OK,
                    )
                else -> error("Unexpected host")
              }
            }
        )
        val result = ClassroomApi().queryClassrooms(1, "2026-04-20")
        assertTrue(result.isSuccess)
        assertEquals(2, syncs)
        assertEquals(2, reads)
        assertTrue(LocalAuthSessionStore.get() != null)
      }

  @Test
  fun `HTTP success with a business error is not a successful empty query`() = runTest {
    useMockUpstream(
        MockEngine { request ->
          if (request.url.host == "sso.buaa.edu.cn") respond("", HttpStatusCode.OK)
          else respond("""{"e":1,"m":"query unavailable","d":{"list":{}}}""", HttpStatusCode.OK)
        }
    )
    val result = ClassroomApi().queryClassrooms(1, "2026-04-20")
    assertTrue(result.isFailure)
    assertEquals(
        "classroom_response_error",
        assertIs<ApiCallException>(result.exceptionOrNull()).code,
    )
  }

  @Test
  fun `successful empty PHP list is accepted without accepting a nonempty array`() = runTest {
    var array = "[]"
    useMockUpstream(
        MockEngine { request ->
          if (request.url.host == "sso.buaa.edu.cn") respond("", HttpStatusCode.OK)
          else respond("""{"e":0,"m":"ok","d":{"list":$array}}""", HttpStatusCode.OK)
        }
    )
    assertTrue(ClassroomApi().queryClassrooms(1, "2026-04-20").getOrThrow().d.list.isEmpty())
    array = "[{}]"
    assertTrue(ClassroomApi().queryClassrooms(1, "2026-04-20").isFailure)
  }

  @Test
  fun `persistent subsystem expiry is retried only once and does not log out a valid account`() =
      runTest {
        var reads = 0
        useMockUpstream(
            MockEngine { request ->
              when (request.url.host) {
                "sso.buaa.edu.cn" -> respond("", HttpStatusCode.OK)
                "uc.buaa.edu.cn" ->
                    respond(
                        """{"code":0,"data":{"schoolid":"22373333","name":"Test User"}}""",
                        HttpStatusCode.OK,
                    )
                else -> {
                  reads++
                  respond("<html><input name='execution'></html>", HttpStatusCode.OK)
                }
              }
            }
        )
        val result = ClassroomApi().queryClassrooms(1, "2026-04-20")
        assertTrue(result.isFailure)
        assertEquals(2, reads)
        assertTrue(LocalAuthSessionStore.get() != null)
      }

  @Test
  fun `replacing a login session invalidates subsystem synchronization`() = runTest {
    var syncs = 0
    useMockUpstream(
        MockEngine { request ->
          if (request.url.host == "sso.buaa.edu.cn") {
            syncs++
            respond("", HttpStatusCode.OK)
          } else respond("""{"e":0,"m":"ok","d":{"list":{}}}""", HttpStatusCode.OK)
        }
    )
    val backend = cn.edu.ubaa.plus.api.local.LocalClassroomApiBackend()
    assertTrue(backend.queryClassrooms(1, "2026-04-20").isSuccess)
    assertTrue(backend.queryClassrooms(1, "2026-04-21").isSuccess)
    assertEquals(1, syncs)
    LocalAuthSessionStore.save(
        LocalAuthSessionStore.get()!!.copy(authenticatedAt = "2026-04-21T00:00:00Z")
    )
    assertTrue(backend.queryClassrooms(1, "2026-04-21").isSuccess)
    assertEquals(2, syncs)
  }

  @Test
  fun `webvpn relative gateway login redirects trigger bounded resynchronization`() = runTest {
    val session = LocalAuthSessionStore.get()!!
    ConnectionRuntime.clearSelectedMode()
    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    ConnectionRuntime.resolveSelectedMode()
    LocalAuthSessionStore.save(session)
    var syncs = 0
    var reads = 0
    LocalUpstreamClientProvider.clientFactory = { redirects ->
      HttpClient(
          MockEngine { request ->
            assertEquals("d.buaa.edu.cn", request.url.host)
            assertTrue(
                request.attributes.getOrNull(cn.edu.ubaa.plus.api.local.LocalPrivateRead) == true
            )
            val url =
                io.ktor.http.Url(
                    cn.edu.ubaa.plus.api.local.LocalWebVpnSupport.fromWebVpnUrl(
                        request.url.toString()
                    )
                )
            if (url.host == "sso.buaa.edu.cn") {
              syncs++
              respond("", HttpStatusCode.OK)
            } else {
              reads++
              if (reads == 1)
                  respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/login"))
              else respond("""{"e":0,"m":"ok","d":{"list":{}}}""", HttpStatusCode.OK)
            }
          }
      ) {
        followRedirects = redirects
      }
    }
    assertTrue(ClassroomApi().queryClassrooms(1, "2026-04-20").isSuccess)
    assertEquals(2, syncs)
    assertEquals(2, reads)
  }

  private fun useMockUpstream(engine: MockEngine) {
    LocalUpstreamClientProvider.clientFactory = { followRedirects ->
      HttpClient(engine) {
        this.followRedirects = followRedirects
        install(HttpCookies) { storage = LocalCookieStore.storage(ConnectionMode.DIRECT) }
      }
    }
  }
}
