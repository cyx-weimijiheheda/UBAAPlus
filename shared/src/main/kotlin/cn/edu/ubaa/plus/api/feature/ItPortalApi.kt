package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.api.local.LocalWebVpnSupport
import cn.edu.ubaa.plus.api.local.localUpstreamUrl
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ItPersonalItemDto(
    val key: String,
    val id: String,
    val name: String,
    val url: String?,
)

@kotlinx.serialization.Serializable
data class ItPersonalDetailDto(
    val value: String,
    val url: String?,
)

data class ItCardBalanceDto(
    val value: Double?,
    val unclaimedAmount: Double?,
    val unit: String = "元",
)

interface ItPortalApiBackend {
  suspend fun getPersonalItems(): Result<List<ItPersonalItemDto>>

  suspend fun getPersonalDetail(id: String): Result<ItPersonalDetailDto>

  suspend fun getCardBalance(): Result<ItCardBalanceDto>

  suspend fun getAvatar(): Result<SchoolAvatarDto> = Result.failure(ApiCallException("学校头像暂不可用"))
}

class ItPortalApi internal constructor(private val backend: ItPortalApiBackend) {
  constructor() : this(LocalItPortalApiBackend())

  suspend fun getPersonalItems(): Result<List<ItPersonalItemDto>> = backend.getPersonalItems()

  suspend fun getAvatar(): Result<SchoolAvatarDto> =
      cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator.read("profile", "school-avatar") {
        backend.getAvatar()
      }

  suspend fun getPersonalDetail(id: String): Result<ItPersonalDetailDto> =
      backend.getPersonalDetail(id)

  suspend fun getCardBalance(): Result<ItCardBalanceDto> = backend.getCardBalance()

  suspend fun findPersonalValue(key: String): Result<ItPersonalDetailDto> {
    val items =
        getPersonalItems().getOrElse {
          return Result.failure(it)
        }
    val item =
        items.firstOrNull { it.key == key }
            ?: return Result.failure(ApiCallException("未找到 $key 数据", code = "portal_item_missing"))
    return getPersonalDetail(item.id)
  }
}

internal class LocalItPortalApiBackend : ItPortalApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun getPersonalItems(): Result<List<ItPersonalItemDto>> =
      execute("校园门户数据加载失败") {
        val body = getText("/personal/frontend/data/items?type=personal_data")
        val root = parseObject(body)
        val data = root["data"] ?: root["d"]
        val array: JsonArray =
            when (data) {
              is JsonArray -> data
              is JsonObject -> (data["data"] ?: data["list"] ?: data["items"]) as? JsonArray
              else -> null
            } ?: JsonArray(emptyList())
        array.mapNotNull { element ->
          val item = element as? JsonObject ?: return@mapNotNull null
          val id = item.string("id")
          if (id.isBlank()) return@mapNotNull null
          ItPersonalItemDto(
              key = item.string("key"),
              id = id,
              name = item.string("name"),
              url = item.string("url").ifBlank { null },
          )
        }
      }

  override suspend fun getPersonalDetail(id: String): Result<ItPersonalDetailDto> =
      execute("校园门户数据加载失败") {
        val body = getText("/personal/frontend/data/detail?id=${id.encodeURLParameter()}")
        val root = parseObject(body)
        val data = root["data"] ?: root["d"] ?: root
        val outer = data as? JsonObject ?: root
        val obj = outer["data"] as? JsonObject ?: outer
        val value = obj.string("value")
        if (value.isBlank()) throw ApiCallException("网费数据缺少数值", code = "portal_parse_error")
        ItPersonalDetailDto(
            value = value,
            url = obj.string("url").ifBlank { null },
        )
      }

  override suspend fun getCardBalance(): Result<ItCardBalanceDto> =
      execute("校园卡余额加载失败") {
        val body = getText("/buaa/frontend/card/balance")
        val root = parseObject(body)
        val data = root["data"] ?: root["d"] ?: root
        val obj = data as? JsonObject ?: root
        ItCardBalanceDto(
            value = obj.double("value") ?: obj.double("balance"),
            unclaimedAmount = obj.double("unclaimedAmount"),
            unit = obj.string("unit").ifBlank { "元" },
        )
      }

  override suspend fun getAvatar(): Result<SchoolAvatarDto> =
      execute("学校头像加载失败") {
        val owner =
            cn.edu.ubaa.plus.repository.ScheduleStore.account()
                ?: throw ApiCallException("请先登录", code = "unauthenticated")
        val root = parseObject(getText("/personal/frontend/data/info"))
        val info =
            ((root["d"] ?: root["data"]) as? JsonObject)?.get("info") as? JsonObject
                ?: throw ApiCallException("门户资料格式异常", code = "portal_parse_error")
        if (info.string("xgh") != owner)
            throw ApiCallException("门户账户与当前账户不一致", code = "portal_account_mismatch")
        val source = info.string("avatar").ifBlank { info.string("avatar_url") }
        if (
            source.isBlank() || source.contains("headpic", true) || source.contains("default", true)
        )
            SchoolAvatarDto(owner)
        else fetchSchoolAvatar(owner, source)
      }

  private suspend fun getText(path: String, allowActivation: Boolean = true): String {
    val response =
        LocalUpstreamClientProvider.shared().get(localUpstreamUrl("https://it.buaa.edu.cn$path")) {
          header(HttpHeaders.Accept, "application/json, text/plain, */*")
          header("X-Requested-With", "XMLHttpRequest")
          header(HttpHeaders.Referrer, localUpstreamUrl(PORTAL_HOME))
        }
    val body = response.bodyAsText()
    if (!response.status.isSuccess()) {
      throw ApiCallException(
          message = "校园门户返回 ${response.status.value}",
          status = response.status,
          code = "portal_http_error",
      )
    }
    val payload = if (looksLikeLoginPage(body)) null else parseObject(body)
    val error = (payload?.get("e") as? JsonPrimitive)?.contentOrNull
    if (payload == null || error == "10013") {
      if (allowActivation) {
        activatePortal()
        return getText(path, allowActivation = false)
      }
      throw ApiCallException("校园门户登录状态已失效，请重新登录", code = "portal_unauthenticated")
    }
    if (error != null && error !in setOf("0", "OK")) {
      throw ApiCallException("校园门户数据暂不可用", code = "portal_upstream_error")
    }
    return body
  }

  /** 使用当前路线的 SSO Cookie 激活门户；重定向仅允许门户及统一认证的已知入口。 */
  private suspend fun activatePortal() {
    var target =
        "https://it.buaa.edu.cn/manage/common/login/index?redirect=${PORTAL_HOME.encodeURLParameter()}"
    val client = LocalUpstreamClientProvider.newNoRedirectClient()
    try {
      repeat(10) {
        val url = Url(target)
        val allowed =
            url.protocol.name == "https" &&
                url.port == 443 &&
                url.user.isNullOrEmpty() &&
                url.password.isNullOrEmpty() &&
                ((url.host == "it.buaa.edu.cn" &&
                    url.encodedPath in
                        setOf(
                            "/manage/common/login/index",
                            "/manage/common/cas_login/30001",
                            "/home/",
                        )) || (url.host == "sso.buaa.edu.cn" && url.encodedPath == "/login"))
        if (!allowed) throw ApiCallException("校园门户登录跳转异常", code = "portal_redirect_error")
        val response = client.get(localUpstreamUrl(target))
        if (response.status.value in setOf(301, 302, 303, 307, 308)) {
          val location =
              response.headers[HttpHeaders.Location]
                  ?: throw ApiCallException("校园门户登录跳转缺失", code = "portal_redirect_error")
          val resolved = URI(localUpstreamUrl(target)).resolve(location).toString()
          target =
              LocalWebVpnSupport.fromWebVpnUrl(LocalWebVpnSupport.normalizeRedirectUrl(resolved))
        } else {
          val body = response.bodyAsText()
          if (
              response.status.isSuccess() &&
                  target.substringBefore('?') == PORTAL_HOME &&
                  !body.contains("CAS Login", ignoreCase = true) &&
                  !body.contains("name=\"execution\"")
          )
              return
          throw ApiCallException("校园门户登录状态已失效，请重新登录", code = "portal_unauthenticated")
        }
      }
      throw ApiCallException("校园门户登录跳转次数过多", code = "portal_redirect_error")
    } finally {
      client.close()
    }
  }

  private companion object {
    const val PORTAL_HOME = "https://it.buaa.edu.cn/home/"
  }

  private fun looksLikeLoginPage(body: String): Boolean {
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
        trimmed.startsWith("<html", ignoreCase = true) ||
        body.contains("CAS Login", ignoreCase = true) ||
        body.contains("统一身份认证", ignoreCase = true)
  }

  private fun parseObject(body: String): JsonObject {
    val start = body.indexOf('{')
    val end = body.lastIndexOf('}')
    if (start < 0 || end <= start) {
      throw ApiCallException("校园门户返回格式异常", code = "portal_parse_error")
    }
    return json.parseToJsonElement(body.substring(start, end + 1)).let {
      it as? JsonObject ?: throw ApiCallException("校园门户返回格式异常", code = "portal_parse_error")
    }
  }

  private fun JsonObject.string(key: String): String =
      (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

  private fun JsonObject.double(key: String): Double? =
      (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

  private suspend fun <T> execute(message: String, block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: CancellationException) {
        throw e
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(ApiCallException(message = message, code = "portal_error", cause = e))
      }
}
