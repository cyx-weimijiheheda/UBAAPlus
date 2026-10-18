package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.local.getLibBookHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class LibraryLoanDto(
    val title: String,
    val author: String,
    val callNo: String,
    val loanDate: String,
    val dueDate: String,
    val renewTimes: Int,
    val status: String,
)

interface LibraryBorrowApiBackend {
  suspend fun getCurrentLoans(): Result<List<LibraryLoanDto>>

  suspend fun getHistory(): Result<List<LibraryLoanDto>>
}

class LibraryBorrowApi internal constructor(private val backend: LibraryBorrowApiBackend) {
  constructor() : this(LocalLibraryBorrowApiBackend())

  suspend fun getCurrentLoans(): Result<List<LibraryLoanDto>> = backend.getCurrentLoans()

  suspend fun getHistory(): Result<List<LibraryLoanDto>> = backend.getHistory()
}

internal class LocalLibraryBorrowApiBackend(
    private val clientProvider: () -> HttpClient = {
      HttpClient(getLibBookHttpClientEngine()) {
        followRedirects = true
        install(HttpCookies) { storage = LocalCookieStore.storage(ConnectionMode.DIRECT) }
      }
    },
) : LibraryBorrowApiBackend {
  private val json = Json { ignoreUnknownKeys = true }
  private val mutex = Mutex()
  private var loggedIn = false
  private val client by lazy { clientProvider() }

  override suspend fun getCurrentLoans(): Result<List<LibraryLoanDto>> = fetch("loans")

  override suspend fun getHistory(): Result<List<LibraryLoanDto>> = fetch("loan_histories")

  private suspend fun fetch(path: String): Result<List<LibraryLoanDto>> =
      try {
        ensureLogin()
        var response = request(path, cookieHeader = null)
        if (response.status == HttpStatusCode.Unauthorized) {
          loggedIn = false
          response = request(path, cookieHeader = cookieHeader())
        }
        val body = response.bodyAsText()
        if (response.status == HttpStatusCode.Unauthorized || looksLikeLoginPage(body)) {
          loggedIn = false
          throw ApiCallException("图书馆登录状态已失效，请打开网页登录", code = "library_auth")
        }
        if (!response.status.isSuccess()) {
          throw ApiCallException(
              message = "图书馆服务返回 ${response.status.value}",
              status = response.status,
              code = "library_http_error",
          )
        }
        Result.success(parseLoans(body))
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(ApiCallException(message = "借阅记录加载失败", code = "library_error", cause = e))
      }

  private suspend fun ensureLogin() {
    mutex.withLock {
      if (loggedIn) return
      client.get(
          "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fspace.lib.buaa.edu.cn%2Fspace%2F"
      ) {
        header(HttpHeaders.UserAgent, USER_AGENT)
      }
      loggedIn = true
    }
  }

  private suspend fun request(path: String, cookieHeader: String?): HttpResponse =
      client.get("https://space.lib.buaa.edu.cn/meta-local/opac/users/$path?page=1&pageSize=100") {
        header(HttpHeaders.Accept, "application/json, text/plain, */*")
        header(HttpHeaders.Referrer, "https://space.lib.buaa.edu.cn/space/")
        header(HttpHeaders.UserAgent, USER_AGENT)
        cookieHeader?.takeIf { it.isNotBlank() }?.let { header(HttpHeaders.Cookie, it) }
      }

  private suspend fun cookieHeader(): String {
    val cookies =
        LocalCookieStore.storage(ConnectionMode.DIRECT)
            .get(Url("https://space.lib.buaa.edu.cn/space/"))
    return cookies.joinToString("; ") { "${it.name}=${it.value}" }
  }

  private fun looksLikeLoginPage(body: String): Boolean {
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        body.contains("CAS Login", ignoreCase = true) ||
        body.contains("统一身份认证", ignoreCase = true)
  }

  private fun parseLoans(body: String): List<LibraryLoanDto> {
    val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyList()
    val array =
        root["items"] as? JsonArray
            ?: root["data"] as? JsonArray
            ?: (root["data"] as? JsonObject)?.get("items") as? JsonArray
            ?: return emptyList()
    return array.mapNotNull { element ->
      val row = element as? JsonObject ?: return@mapNotNull null
      val book = row["item"] as? JsonObject ?: row
      val bib = book["bibAttrs"] as? JsonObject ?: book
      val title = bib.string("title").ifBlank { book.string("title") }
      if (title.isBlank()) return@mapNotNull null
      LibraryLoanDto(
          title = title,
          author = bib.string("author").ifBlank { book.string("author") },
          callNo = bib.string("callno").ifBlank { bib.string("callNo") },
          loanDate = row.string("loanDate").ifBlank { book.string("loanDate") },
          dueDate = row.string("dueDate").ifBlank { book.string("dueDate") },
          renewTimes = row.int("renewTimes") ?: 0,
          status = row.string("loanTypeDesc").ifBlank { row.string("status") },
      )
    }
  }

  private fun JsonObject.string(key: String): String =
      (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

  private fun JsonObject.int(key: String): Int? =
      (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

  private companion object {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
  }
}
