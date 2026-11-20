package cn.edu.ubaa.plus.signin

import cn.edu.ubaa.plus.auth.InMemoryCookieStorageFactory
import cn.edu.ubaa.plus.auth.InMemorySessionStore
import cn.edu.ubaa.plus.auth.SessionManager
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.utils.VpnCipher
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class SigninClientTest {

  @Test
  fun `sanitizeSignInMessage maps not in class time error`() {
    val client = SigninClient(studentId = "24182104")

    try {
      assertEquals(
          "当前不是上课时间，无法签到",
          client.sanitizeSignInMessage(success = false, rawMessage = "当前时间不是上课时间！"),
      )
    } finally {
      client.close()
    }
  }

  @Test
  fun `sanitizeSignInMessage keeps unknown upstream message`() {
    // 学校改了策略时关键词表不可能提前覆盖，必须把原始文案透出来便于定位。
    val client = SigninClient(studentId = "24182104")

    try {
      assertEquals(
          "签到失败：策略调整，请勿重复提交",
          client.sanitizeSignInMessage(success = false, rawMessage = "策略调整，请勿重复提交"),
      )
    } finally {
      client.close()
    }
  }

  @Test
  fun `sanitizeSignInMessage maps scan requirement`() {
    val client = SigninClient(studentId = "24182104")

    try {
      assertEquals(
          "学校要求现场扫码签到，请使用扫码入口",
          client.sanitizeSignInMessage(success = false, rawMessage = "请扫码签到"),
      )
    } finally {
      client.close()
    }
  }

  @Test
  fun `get classes follows iclass SSO redirects and app logs in with loginName`() = runBlocking {
    val originalVpnEnabled = VpnCipher.isEnabled
    VpnCipher.isEnabled = false
    val loginName = "Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg="
    var myCenterRequests = 0
    val appLoginPhones = mutableListOf<String>()
    val sessionManager =
        SessionManager(
            sessionStore = InMemorySessionStore(),
            cookieStorageFactory = InMemoryCookieStorageFactory(),
            clientFactory = { _: CookiesStorage ->
              HttpClient(MockEngine) {
                engine {
                  addHandler { request ->
                    when {
                      request.url.host == "iclass.buaa.edu.cn" &&
                          request.url.port == 8346 &&
                          request.url.parameters["type"] == "jumpMyCenter" -> {
                        myCenterRequests++
                        respond(
                            content = "",
                            status = HttpStatusCode.Found,
                            headers =
                                headersOf(
                                    HttpHeaders.Location,
                                    if (myCenterRequests == 1) {
                                      "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Ficlass.buaa.edu.cn%3A8346%2F%3Ftype%3DjumpMyCenter"
                                    } else {
                                      "https://iclass.buaa.edu.cn:8346/?loginName=$loginName&type=jumpMyCenter#/MyCenter"
                                    },
                                ),
                        )
                      }
                      request.url.host == "sso.buaa.edu.cn" ->
                          respond(
                              content = "",
                              status = HttpStatusCode.Found,
                              headers =
                                  headersOf(
                                      HttpHeaders.Location,
                                      "https://iclass.buaa.edu.cn:8346/cas-login?ticket=ST-test",
                                  ),
                          )
                      request.url.host == "iclass.buaa.edu.cn" &&
                          request.url.port == 8346 &&
                          request.url.encodedPath == "/cas-login" ->
                          respond(
                              content = "",
                              status = HttpStatusCode.Found,
                              headers =
                                  headersOf(HttpHeaders.Location, "/?type=jumpMyCenter#/MyCenter"),
                          )
                      else ->
                          error("Unexpected SSO request: ${request.method.value} ${request.url}")
                    }
                  }
                }
              }
            },
        )
    val appClient =
        HttpClient(MockEngine) {
          engine {
            addHandler { request ->
              when {
                request.url.host == "iclass.buaa.edu.cn" &&
                    request.url.port == 8347 &&
                    request.url.encodedPath == "/app/user/login.action" -> {
                  appLoginPhones += request.url.parameters["phone"].orEmpty()
                  respond(
                      content =
                          ByteReadChannel(
                              """{"STATUS":0,"result":{"id":"user-1","sessionId":"session-1"}}"""
                          ),
                      status = HttpStatusCode.OK,
                      headers =
                          headersOf(
                              HttpHeaders.ContentType,
                              ContentType.Application.Json.toString(),
                          ),
                  )
                }
                request.url.toString() ==
                    "https://iclass.buaa.edu.cn:8347/app/course/get_stu_course_sched.action?id=user-1&dateStr=20260524" ->
                    respond(
                        content =
                            ByteReadChannel(
                                """{"STATUS":0,"result":[{"id":"course-1","courseName":"软件工程","classBeginTime":"08:00","classEndTime":"09:40","signStatus":0}]}"""
                            ),
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString(),
                            ),
                    )
                else -> error("Unexpected app request: ${request.method.value} ${request.url}")
              }
            }
          }
        }

    try {
      val candidate = sessionManager.prepareSession("24182104")
      sessionManager.commitSession(candidate, UserData("Test User", "24182104"))

      val client = SigninClient("24182104", sessionManager = sessionManager, client = appClient)
      val classes = client.getClasses("20260524")

      assertEquals(listOf(loginName), appLoginPhones)
      assertEquals(2, myCenterRequests)
      assertEquals("course-1", classes.singleOrNull()?.courseId)
    } finally {
      appClient.close()
      sessionManager.close()
      VpnCipher.isEnabled = originalVpnEnabled
    }
  }

  @Test
  fun `直连签到使用 8181 stu_auto_sign 且提交六个字段`() = runBlocking { assertSigninRequest(useWebVpn = false) }

  @Test
  fun `WebVPN 签到包装 8181 stu_auto_sign`() = runBlocking { assertSigninRequest(useWebVpn = true) }

  @Test
  fun `签到提交不再请求时间戳`() = runBlocking {
    // stu_auto_sign 不带时间戳，旧实现会先 GET get_timestamp.action，这里断言它不再发生。
    val originalVpnEnabled = VpnCipher.isEnabled
    VpnCipher.isEnabled = false
    var timestampRequested = false
    val sessionManager = buildSigninSessionManager { request ->
      if (VpnCipher.fromVpnUrl(request.url.toString()).contains("get_timestamp")) {
        timestampRequested = true
      }
    }
    val appClient = buildSigninAppClient { request, upstreamUrl ->
      when {
        upstreamUrl.encodedPath == "/app/user/login.action" ->
            respondJson("""{"STATUS":0,"result":{"id":"user-1","sessionId":"session-1"}}""")
        upstreamUrl.port == 8181 -> respondJson("""{"STATUS":0,"result":{"stuSignStatus":1}}""")
        else -> error("未预期的 app 请求：${request.method.value} ${request.url}")
      }
    }

    try {
      val candidate = sessionManager.prepareSession("24182104")
      sessionManager.commitSession(candidate, UserData("Test User", "24182104"))
      val client = SigninClient("24182104", sessionManager = sessionManager, client = appClient)

      assertTrue(client.signIn("course-1").first)
      assertEquals(false, timestampRequested)
    } finally {
      appClient.close()
      sessionManager.close()
      VpnCipher.isEnabled = originalVpnEnabled
    }
  }

  private suspend fun assertSigninRequest(useWebVpn: Boolean) {
    val originalVpnEnabled = VpnCipher.isEnabled
    VpnCipher.isEnabled = useWebVpn
    val expectedHost = if (useWebVpn) "d.buaa.edu.cn" else "iclass.buaa.edu.cn"
    // stu_auto_sign 固定走 8181 的 HTTPS 入口，两条路线一致，WebVPN 只额外包一层网关。
    val submitUrl = "https://iclass.buaa.edu.cn:8181/app/course/stu_auto_sign.action"
    val loginName = "Rjc1QkJDMUMxNzVENkY0NkZCNzFDMEM5RjYwNzg4RDg="
    val sessionManager =
        SessionManager(
            sessionStore = InMemorySessionStore(),
            cookieStorageFactory = InMemoryCookieStorageFactory(),
            clientFactory = { _: CookiesStorage ->
              HttpClient(MockEngine) {
                engine {
                  addHandler { request ->
                    assertEquals(expectedHost, request.url.host)
                    val upstreamUrl = Url(VpnCipher.fromVpnUrl(request.url.toString()))
                    when {
                      upstreamUrl.host == "iclass.buaa.edu.cn" &&
                          upstreamUrl.port == 8346 &&
                          upstreamUrl.parameters["type"] == "jumpMyCenter" ->
                          respond(
                              content = "",
                              status = HttpStatusCode.Found,
                              headers =
                                  headersOf(
                                      HttpHeaders.Location,
                                      "https://iclass.buaa.edu.cn:8346/?loginName=$loginName&type=jumpMyCenter#/MyCenter",
                                  ),
                          )
                      else -> error("未预期的 SSO 请求：${request.method.value} ${request.url}")
                    }
                  }
                }
              }
            },
        )
    val appClient =
        HttpClient(MockEngine) {
          install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
          engine {
            addHandler { request ->
              assertEquals(expectedHost, request.url.host)
              val upstreamUrl = Url(VpnCipher.fromVpnUrl(request.url.toString()))
              when {
                upstreamUrl.host == "iclass.buaa.edu.cn" &&
                    upstreamUrl.port == 8347 &&
                    upstreamUrl.encodedPath == "/app/user/login.action" -> {
                  assertEquals(loginName, request.url.parameters["phone"])
                  respond(
                      content =
                          ByteReadChannel(
                              """{"STATUS":0,"result":{"id":"user-1","sessionId":"session-1"}}"""
                          ),
                      status = HttpStatusCode.OK,
                      headers =
                          headersOf(
                              HttpHeaders.ContentType,
                              ContentType.Application.Json.toString(),
                          ),
                  )
                }
                upstreamUrl.toString() == submitUrl -> {
                  assertEquals(HttpMethod.Post, request.method)
                  assertEquals("session-1", request.headers["sessionId"])
                  // stu_auto_sign 不带查询串，六个字段全部在正文里。
                  assertEquals("", upstreamUrl.encodedQuery)
                  val form = assertIs<FormDataContent>(request.body).formData
                  assertEquals("user-1", form["id"])
                  assertEquals("course-1", form["courseSchedId"])
                  assertEquals("02:00:00:00:00:00", form["routerInfo"])
                  assertEquals("0", form["longitude"])
                  assertEquals("0", form["latitude"])
                  assertEquals("Android", form["machineInfo"])
                  assertEquals(6, form.names().count())
                  respondJson("""{"STATUS":0,"ERRMSG":"签到成功","result":{"stuSignStatus":1}}""")
                }
                // 旧入口已停用，请求到它必须失败，避免回退到不可用协议。
                upstreamUrl.encodedPath.endsWith("stu_scan_sign.action") ->
                    respond(
                        content = ByteReadChannel("<html><title>404 Not Found</title></html>"),
                        status = HttpStatusCode.NotFound,
                        headers =
                            headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                    )
                else -> error("未预期的 app 请求：${request.method.value} ${request.url}")
              }
            }
          }
        }

    try {
      val candidate = sessionManager.prepareSession("24182104")
      sessionManager.commitSession(candidate, UserData("Test User", "24182104"))

      val client = SigninClient("24182104", sessionManager = sessionManager, client = appClient)
      val result = client.signIn("course-1")

      assertTrue(result.first, result.second)
      assertEquals("签到成功", result.second)
    } finally {
      appClient.close()
      sessionManager.close()
      VpnCipher.isEnabled = originalVpnEnabled
    }
  }
}

/** 签到时 mock 的 SSO 会话管理器：只放行 iClass 引导请求。 */
private fun buildSigninSessionManager(
    onRequest: (io.ktor.client.request.HttpRequestData) -> Unit
): SessionManager =
    SessionManager(
        sessionStore = InMemorySessionStore(),
        cookieStorageFactory = InMemoryCookieStorageFactory(),
        clientFactory = { _: CookiesStorage ->
          HttpClient(MockEngine) {
            engine {
              addHandler { request ->
                onRequest(request)
                val upstreamUrl = Url(VpnCipher.fromVpnUrl(request.url.toString()))
                if (
                    upstreamUrl.host == "iclass.buaa.edu.cn" &&
                        upstreamUrl.port == 8346 &&
                        upstreamUrl.parameters["type"] == "jumpMyCenter"
                ) {
                  respond(
                      content = "",
                      status = HttpStatusCode.Found,
                      headers =
                          headersOf(
                              HttpHeaders.Location,
                              "https://iclass.buaa.edu.cn:8346/?loginName=test-login&type=jumpMyCenter#/MyCenter",
                          ),
                  )
                } else {
                  error("未预期的 SSO 请求：${request.method.value} ${request.url}")
                }
              }
            }
          }
        },
    )

/** 签名 app 侧 mock 客户端；由用例决定各路由的响应。 */
private fun buildSigninAppClient(
    handler:
        io.ktor.client.engine.mock.MockRequestHandleScope.(
            io.ktor.client.request.HttpRequestData,
            Url,
        ) -> io.ktor.client.request.HttpResponseData
): HttpClient =
    HttpClient(MockEngine) {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      engine {
        addHandler { request ->
          handler(request, Url(VpnCipher.fromVpnUrl(request.url.toString())))
        }
      }
    }

/** 统一的 JSON 响应，避免每个分支重复构造 header。 */
private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    body: String
): io.ktor.client.request.HttpResponseData =
    respond(
        content = ByteReadChannel(body),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
