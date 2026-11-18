package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.ApiCallException
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
import kotlinx.datetime.LocalDate

class CampusCardConsumptionApiTest {
  private val start = LocalDate.parse("2026-11-18")
  private val end = LocalDate.parse("2026-11-19")
  private var failSecond = false
  private var repeatPage = false
  private val requestedPages = mutableListOf<Int>()

  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    runTest { localConnectionTestMutex.lock() }
    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    ConnectionRuntime.resolveSelectedMode()
    ScheduleStore.useAccount("synthetic")
    LocalAuthSessionStore.save(
        LocalAuthSession("synthetic", UserData("Synthetic", "synthetic"), "now", "now")
    )
    LocalUpstreamClientProvider.reset()
    LocalUpstreamClientProvider.clientFactory = { redirects ->
      HttpClient(
          MockEngine { request ->
            val url = Url(LocalWebVpnSupport.fromWebVpnUrl(request.url.toString()))
            if (url.encodedPath == "/api/campus_card/trades") {
              assertTrue(request.attributes.getOrNull(LocalPrivateRead) == true)
              assertEquals("consume", url.parameters["tradeType"])
              assertEquals("synthetic", url.parameters["stuNo"])
              assertEquals("2026-11-17T16:00:00Z", url.parameters["minTsCreation"])
              assertEquals("2026-11-19T15:59:59.999Z", url.parameters["maxTsCreation"])
              val page = url.parameters["pageNum"]!!.toInt()
              requestedPages += page
              if (page == 2 && failSecond) respond("unavailable", HttpStatusCode.ServiceUnavailable)
              else
                  respond(
                      page(page, repeatPage),
                      HttpStatusCode.OK,
                      headersOf(HttpHeaders.ContentType, "application/json"),
                  )
            } else respond("<html>synthetic SSO landing</html>", HttpStatusCode.OK)
          }
      ) {
        followRedirects = redirects
      }
    }
  }

  @AfterTest
  fun cleanup() {
    LocalUpstreamClientProvider.reset()
    localConnectionTestMutex.unlock()
  }

  private fun row(time: String) =
      """{"date":"2026-11-18 $time","amount":-235,"balance":12345,"merName":"合成商户","txName":"消费"}"""

  private fun page(n: Int, repeat: Boolean = false): String {
    val data = if (n == 1 || repeat) "${row("12:00:00")},${row("09:00:00")}" else row("09:00:00")
    return """{"success":true,"data":{"count":0,"pageSize":2,"pageNum":$n,"data":[$data]}}"""
  }

  private fun api() = CampusCardConsumptionApi(LocalCampusCardConsumptionBackend(), { end })

  @Test
  fun pagesUntilShortPageIgnoringBogusCountThenSortsWithoutDroppingIdenticalPurchases() = runTest {
    val result = api().query(start, end).getOrThrow()
    assertEquals(listOf(1, 2), requestedPages)
    assertEquals(
        listOf("2026-11-18 09:00:00", "2026-11-18 09:00:00", "2026-11-18 12:00:00"),
        result.trades.map { it.dateTime },
    )
    assertEquals("-2.35", result.trades.first().amount)
    assertEquals("123.45", result.trades.first().balance)
  }

  @Test
  fun partialRefreshPreservesLastCompleteSnapshotAndOriginalTimestamp() = runTest {
    val before = api().query(start, end).getOrThrow()
    failSecond = true
    val after = api().query(start, end).getOrThrow()
    assertEquals(before, after)
    assertNotNull(FeatureReadCache.readWarning("card", consumptionCacheKey(start, end)))
  }

  @Test
  fun repeatedPagesAreRejectedInsteadOfCachingIncompleteData() = runTest {
    repeatPage = true
    assertTrue(api().query(start, end).isFailure)
    assertNull(
        FeatureReadCache.get<CampusCardConsumptionDto>("card", consumptionCacheKey(start, end))
    )
  }

  @Test
  fun invalidOrFutureDatesNeverSendRequestsAndSingleDayCoversTheEntireDay() = runTest {
    var calls = 0
    val api =
        CampusCardConsumptionApi(
            object : CampusCardConsumptionBackend {
              override suspend fun query(
                  start: LocalDate,
                  end: LocalDate,
              ): Result<CampusCardConsumptionDto> {
                calls++
                return Result.success(
                    CampusCardConsumptionDto(start.toString(), end.toString(), emptyList())
                )
              }
            },
            { end },
        )
    assertTrue(api.query(end, start).isFailure)
    assertTrue(api.query(start, LocalDate.parse("2026-11-20")).isFailure)
    assertEquals(0, calls)
    assertTrue(api.query(end, end).isSuccess)
    assertEquals(1, calls)
    assertEquals(
        "2026-11-18T16:00:00Z" to "2026-11-19T15:59:59.999Z",
        consumptionUtcBounds(end, end),
    )
  }

  @Test
  fun malformedDatesMoneyAndOutOfRangeRecordsCannotBecomeAValidEmptyResult() {
    for (body in
        listOf(
            page(1).replace("-235", "unknown"),
            page(1).replace("2026-11-18", "2026-11-31"),
            page(1).replace("2026-11-18", "2026-11-17"),
            "<html>login</html>",
        )) {
      assertFailsWith<ApiCallException> { parseConsumptionPage(body, start, end, 1) }
    }
  }
}
