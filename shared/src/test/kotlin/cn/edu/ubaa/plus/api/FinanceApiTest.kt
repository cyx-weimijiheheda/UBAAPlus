package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.api.local.*
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.testing.TestSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*

class FinanceApiTest {
  private var emptySecond = false
  private var duplicate = false
  private var expired = 0
  private var logins = 0
  private var mode = ConnectionMode.DIRECT

  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    runTest { localConnectionTestMutex.lock() }
    ScheduleStore.useAccount("SYNTHETIC")
    LocalUpstreamClientProvider.reset()
  }

  @AfterTest
  fun cleanup() {
    LocalUpstreamClientProvider.reset()
    localConnectionTestMutex.unlock()
  }

  private fun configure(selected: ConnectionMode) {
    mode = selected
    ConnectionModeStore.save(mode)
    ConnectionRuntime.resolveSelectedMode()
    LocalAuthSessionStore.save(
        LocalAuthSession("SYNTHETIC", UserData("Synthetic", "SYNTHETIC"), "now", "now")
    )
    LocalUpstreamClientProvider.reset()
    LocalUpstreamClientProvider.clientFactory = { redirects ->
      HttpClient(
          MockEngine { request ->
            assertFalse(redirects)
            assertTrue(request.attributes.getOrNull(LocalPrivateRead) == true)
            val url = Url(LocalWebVpnSupport.fromWebVpnUrl(request.url.toString()))
            fun hop(raw: String) =
                respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.Location,
                        if (mode == ConnectionMode.WEBVPN) localUpstreamUrl(raw) else raw,
                    ),
                )
            when (url.encodedPath) {
              "/login" -> {
                assertEquals("http://icw.buaa.edu.cn/checkbuaa.aspx", url.parameters["service"])
                hop("http://icw.buaa.edu.cn/checkbuaa.aspx?ticket=synthetic")
              }
              "/checkbuaa.aspx" -> hop("http://icw.buaa.edu.cn/Newindex.aspx")
              "/Newindex.aspx" ->
                  respond(
                      """<span id="Lab_Name">合成用户</span><span id="Lab_Num">SYNTHETIC</span><span id="Lab_Job">合成学院</span><span id="Lab_Yhkh">synthetic-bank</span><input name="__VIEWSTATE" value="secret-view-state">"""
                  )
              "/jump.aspx" ->
                  hop(
                      "https://chaxun.icw.buaa.edu.cn/gxzhcxzhb/login?Userid=SYNTHETIC&Cypt=fixture%2Bcipher&Stamp=1"
                  )
              "/api-zhcx/login" -> {
                logins++
                val text = request.body.toByteArray().decodeToString()
                assertEquals("fixture+cipher", parseQueryString(text)["password"])
                assertEquals("yzm", parseQueryString(text)["yzm"])
                respond(
                    """{"code":200,"data":{"token":"synthetic-token"}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
              }
              "/api-zhcx/getStudentSRCX" -> {
                assertEquals("Bearer synthetic-token", request.headers[HttpHeaders.Authorization])
                val query =
                    Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                assertEquals("SYNTHETIC", query["userId"]?.jsonPrimitive?.content)
                assertEquals(100, query["limit"]?.jsonPrimitive?.int)
                if (expired > 0) {
                  expired--
                  respond("", HttpStatusCode.Unauthorized)
                } else {
                  val page = query["currentPage"]!!.jsonPrimitive.int
                  val rows =
                      if (page == 2 && emptySecond) ""
                      else
                          """{"lsh":"${if(duplicate)"1" else page}","ffxmmc":"合成补助","je":12.34,"se":0,"sfje":0,"nian":"2026","yue":"11","yhzh":"never-project-bank"}"""
                  respond(
                      """{"code":200,"data":{"total":2,"mx":[$rows]}}""",
                      headers = headersOf(HttpHeaders.ContentType, "application/json"),
                  )
                }
              }
              else -> error("unexpected synthetic path")
            }
          }
      ) {
        followRedirects = redirects
      }
    }
  }

  @Test
  fun bothRoutesReadCompletePagesPreserveZeroAndDoNotCacheCredentialsOrUnneededIdentity() =
      runTest {
        for (selected in listOf(ConnectionMode.DIRECT, ConnectionMode.WEBVPN)) {
          configure(selected)
          val result = FinanceApi().incomes().getOrThrow()
          assertEquals(2, result.entries.size)
          assertEquals(1234L, result.entries.first().amountCents)
          assertEquals(0L, result.entries.first().paidCents)
          val saved = FeatureReadCache.entry("finance", "incomes")!!.body
          assertFalse(saved.contains("synthetic-token"))
          assertFalse(saved.contains("never-project-bank"))
          assertFalse(saved.contains("fixture+cipher"))
        }
      }

  @Test
  fun oneExpiredFinanceTokenIsRebuiltOnce() = runTest {
    configure(ConnectionMode.WEBVPN)
    expired = 1
    assertTrue(FinanceApi().incomes().isSuccess)
    assertEquals(2, logins)
  }

  @Test
  fun missingPagesAndDuplicateRecordsCannotReplaceLastCompleteSnapshot() = runTest {
    configure(ConnectionMode.DIRECT)
    val first = FinanceApi().incomes().getOrThrow()
    val timestamp = FeatureReadCache.entry("finance", "incomes")!!.savedAt
    emptySecond = true
    assertEquals(first, FinanceApi().incomes().getOrThrow())
    assertEquals(timestamp, FeatureReadCache.entry("finance", "incomes")!!.savedAt)
    assertNotNull(FeatureReadCache.readWarning("finance", "incomes"))
    emptySecond = false
    duplicate = true
    assertTrue(LocalFinanceBackend().incomes().isFailure)
  }

  @Test
  fun profileProjectionChecksOwnerAndDiscardsAspNetAuthenticationFields() = runTest {
    configure(ConnectionMode.DIRECT)
    val profile = FinanceApi().profile().getOrThrow()
    assertEquals("synthetic-bank", profile.bankCard)
    assertNull(profile.phone)
    assertFalse(FeatureReadCache.entry("finance", "profile")!!.body.contains("secret-view-state"))
    assertFails { parseFinanceProfile("<span id='Lab_Num'>OTHER</span>", "SYNTHETIC") }
  }

  @Test
  fun amountsMissingAndZeroAreDifferentAndInvalidPagesAreNotEmptySuccess() {
    assertNull(financeMoney(JsonNull))
    assertEquals(0L, financeMoney(JsonPrimitive(0)))
    assertEquals(-5L, financeMoney(JsonPrimitive(-0.05)))
    assertFails { financeMoney(JsonPrimitive(0.001)) }
    assertFails { financeMoney(JsonPrimitive("12.34")) }
    assertEquals(
        0,
        parseFinancePage(Json.parseToJsonElement("""{"data":[],"count":0}""").jsonObject).total,
    )
    assertFails {
      parseFinancePage(Json.parseToJsonElement("""{"mx":[],"total":"0"}""").jsonObject)
    }
    assertFails { parseFinancePage(JsonObject(emptyMap())) }
  }

  @Test
  fun handoffRejectsExternalAndUnexpectedTargetsAndRetainsRawQueryExactly() {
    for (url in
        listOf(
            "https://evil.example/Newindex.aspx",
            "https://icw.buaa.edu.cn/Unknown.aspx",
            "https://user@icw.buaa.edu.cn/Newindex.aspx",
            "file:///Newindex.aspx",
        )) assertFails { financeTarget(url) }
    assertEquals(
        "https://chaxun.icw.buaa.edu.cn/gxzhcxzhb/login?Cypt=a%2Bb",
        financeTarget("http://chaxun.icw.buaa.edu.cn/gxzhcxzhb/login?Cypt=a%2Bb"),
    )
  }
}
