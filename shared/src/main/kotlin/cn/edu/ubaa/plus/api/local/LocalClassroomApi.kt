package cn.edu.ubaa.plus.api.local

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.auth.toUserFacingApiException
import cn.edu.ubaa.plus.api.feature.ClassroomApiBackend
import cn.edu.ubaa.plus.model.dto.ClassroomQueryResponse
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json

internal class LocalClassroomApiBackend : ClassroomApiBackend {
  private val json = Json { ignoreUnknownKeys = true }
  private val syncMutex = Mutex()
  @Volatile private var syncedSession: ClassroomSessionKey? = null

  private data class ClassroomSessionKey(
      val mode: ConnectionMode?,
      val username: String,
      val authenticatedAt: String,
  )

  private fun currentSessionKey(): ClassroomSessionKey? =
      LocalAuthSessionStore.get()?.let {
        ClassroomSessionKey(ConnectionRuntime.currentMode(), it.username, it.authenticatedAt)
      }

  private fun checkSession(key: ClassroomSessionKey) {
    if (currentSessionKey() != key) throw CancellationException("账户或连接模式已切换")
  }

  internal fun clearCache() {
    syncedSession = null
  }

  override suspend fun queryClassrooms(xqid: Int, date: String): Result<ClassroomQueryResponse> {
    val key = currentSessionKey() ?: return Result.failure(localUnauthenticatedApiException())
    return try {
      syncLocalClassroomSession(key)
      LocalUpstreamClientProvider.newNoRedirectClient().use { client ->
        for (attempt in 0..1) {
          checkSession(key)
          val response =
              client.get(
                  localUpstreamUrl("https://app.buaa.edu.cn/buaafreeclass/wap/default/search1")
              ) {
                attributes.put(LocalPrivateRead, true)
                parameter("xqid", xqid)
                parameter("floorid", "")
                parameter("date", date)
                applyClassroomHeaders()
              }
          val body = response.bodyAsText()
          currentCoroutineContext().ensureActive()
          checkSession(key)
          if (isLocalClassroomSessionExpired(response, body)) {
            syncedSession = null
            if (attempt == 0) {
              // Only re-activate this read-only subsystem using the existing SSO cookie.
              syncLocalClassroomSession(key)
              continue
            }
            return@use Result.failure(
                resolveLocalBusinessAuthenticationFailure("classroom_query_failed")
            )
          }
          return@use parseClassroomResponse(response, body)
        }
        Result.failure(localBusinessApiException("classroom_query_failed", "空闲教室查询失败，请稍后重试"))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e.toUserFacingApiException("空闲教室查询失败，请稍后重试"))
    }
  }

  private suspend fun syncLocalClassroomSession(key: ClassroomSessionKey) {
    if (syncedSession == key) return
    syncMutex.withLock {
      checkSession(key)
      if (syncedSession == key) return@withLock
      val response =
          try {
            LocalUpstreamClientProvider.shared().get(classroomSyncUrl()) {
              attributes.put(LocalPrivateRead, true)
              header(HttpHeaders.UserAgent, LOCAL_CLASSROOM_USER_AGENT)
            }
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {
            // A sync failure must stay retryable; an existing application cookie may still work.
            return@withLock
          }
      currentCoroutineContext().ensureActive()
      checkSession(key)
      if (response.status.value in 200..399) syncedSession = key
    }
  }

  private fun parseClassroomResponse(
      response: HttpResponse,
      body: String,
  ): Result<ClassroomQueryResponse> {
    if (response.status != HttpStatusCode.OK)
        return Result.failure(
            localBusinessApiException(
                code = "classroom_query_failed",
                defaultMessage = "空闲教室查询失败，请稍后重试",
                status = response.status,
            )
        )
    return try {
      val root = json.parseToJsonElement(body) as? JsonObject ?: throw IllegalArgumentException()
      if ((root["e"] as? JsonPrimitive)?.intOrNull != 0) {
        return Result.failure(
            ApiCallException("空教室查询未成功，请刷新后重试", code = "classroom_response_error")
        )
      }
      val data = root["d"] as? JsonObject ?: throw IllegalArgumentException()
      // PHP represents an empty associative array as []; only accept the empty form.
      val list = data["list"]
      val normalized =
          if (list is JsonArray && list.isEmpty())
              JsonObject(root + ("d" to JsonObject(data + ("list" to JsonObject(emptyMap())))))
          else root
      Result.success(json.decodeFromJsonElement<ClassroomQueryResponse>(normalized))
    } catch (e: Exception) {
      Result.failure(
          ApiCallException("空教室响应格式异常，请刷新后重试", code = "classroom_response_error", cause = e)
      )
    }
  }
}

private fun isLocalClassroomSessionExpired(response: HttpResponse, body: String): Boolean {
  if (response.status == HttpStatusCode.Unauthorized) return true
  val location =
      response.headers[HttpHeaders.Location]?.let {
        runCatching { java.net.URI(response.call.request.url.toString()).resolve(it).toString() }
            .getOrNull()
      }
  if (localIsSsoUrl(location)) return true
  if (location != null) {
    val uri = runCatching { java.net.URI(location) }.getOrNull()
    if (uri?.host == "d.buaa.edu.cn" && uri.path == "/login") return true
  }
  if (body.trimStart().startsWith("<")) {
    return org.jsoup.Jsoup.parse(body).selectFirst("input[name=execution]") != null ||
        body.contains("统一身份认证")
  }
  return false
}

private fun HttpRequestBuilder.applyClassroomHeaders() {
  header(HttpHeaders.UserAgent, LOCAL_CLASSROOM_USER_AGENT)
  header(HttpHeaders.Accept, "application/json, text/javascript, */*; q=0.01")
  header(
      HttpHeaders.Referrer,
      localUpstreamUrl("https://app.buaa.edu.cn/site/classRoomQuery/index"),
  )
  header("X-Requested-With", "XMLHttpRequest")
}

private fun classroomSyncUrl(): String =
    localUpstreamUrl(
        "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fapp.buaa.edu.cn%2Fa_buaa%2Fapi%2Fcas%2Findex%3Fredirect%3Dhttps%253A%252F%252Fapp.buaa.edu.cn%252Fsite%252FclassRoomQuery%252Findex%26from%3Dwap%26login_from%3D&noAutoRedirect=1"
    )

private const val LOCAL_CLASSROOM_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 16; 24031PN0DC Build/BP2A.250605.031.A3; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/138.0.7204.180 Mobile Safari/537.36 XWEB/1380275 MMWEBSDK/20230806 MMWEBID/4102 wxworklocal/3.2.200 wwlocal/3.2.200 wxwork/4.0.0 appname/wxworklocal-customized wxworklocal-device-code/195ef5586d7d3c2808fcbea32d77c0d4 MicroMessenger/7.0.1 appScheme/wxworklocalcustomized Language/zh_CN ColorScheme/Light WXWorklocalClientType/Android Brand/xiaomi"
