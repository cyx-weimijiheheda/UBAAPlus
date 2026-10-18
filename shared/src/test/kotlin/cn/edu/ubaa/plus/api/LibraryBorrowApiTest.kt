package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.LibraryBorrowApi
import cn.edu.ubaa.plus.api.feature.LocalLibraryBorrowApiBackend
import cn.edu.ubaa.plus.api.local.LocalCookieStore
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

class LibraryBorrowApiTest {
  private lateinit var api: LibraryBorrowApi

  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    runTest { localConnectionTestMutex.lock() }
    LocalCookieStore.settings = MapSettings()
    val engine = MockEngine { request ->
      when (request.url.encodedPath) {
        "/meta-local/opac/cas/rosetta" ->
            respond(
                content = ByteReadChannel("ok"),
                status = HttpStatusCode.OK,
            )
        "/meta-local/opac/users/loans" ->
            respond(
                content =
                    ByteReadChannel(
                        """
                        {"items":[{"item":{"bibAttrs":{"title":"软件工程","author":"张老师","callno":"TP311"}},
                          "loanDate":"2026-09-01","dueDate":"2026-10-01","renewTimes":1}]}
                        """
                            .trimIndent()
                    ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        "/meta-local/opac/users/loan_histories" ->
            respond(
                content = ByteReadChannel("""{"items":[]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        else -> respondError(HttpStatusCode.NotFound)
      }
    }
    api =
        LibraryBorrowApi(
            LocalLibraryBorrowApiBackend(
                clientProvider = { HttpClient(engine) { followRedirects = true } }
            )
        )
  }

  @AfterTest
  fun tearDown() {
    localConnectionTestMutex.unlock()
  }

  @Test
  fun `解析当前借阅与历史`() = runTest {
    val current = api.getCurrentLoans()
    assertTrue(current.isSuccess, current.exceptionOrNull()?.message.orEmpty())
    val loan = current.getOrThrow().single()
    assertEquals("软件工程", loan.title)
    assertEquals("张老师", loan.author)
    assertEquals("TP311", loan.callNo)
    assertEquals(1, loan.renewTimes)

    val history = api.getHistory()
    assertTrue(history.isSuccess, history.exceptionOrNull()?.message.orEmpty())
    assertTrue(history.getOrThrow().isEmpty())
  }
}
