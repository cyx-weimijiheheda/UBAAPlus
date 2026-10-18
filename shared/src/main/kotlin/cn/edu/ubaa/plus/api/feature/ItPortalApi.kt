package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.api.local.localUpstreamUrl
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
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
}

class ItPortalApi internal constructor(private val backend: ItPortalApiBackend) {
  constructor() : this(LocalItPortalApiBackend())

  suspend fun getPersonalItems(): Result<List<ItPersonalItemDto>> = backend.getPersonalItems()

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
        val body = getText("/personal/frontend/data/detail?id=$id")
        val root = parseObject(body)
        val data = root["data"] ?: root["d"] ?: root
        val obj = data as? JsonObject ?: root
        ItPersonalDetailDto(
            value = obj.string("value"),
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

  private suspend fun getText(path: String): String {
    val response =
        LocalUpstreamClientProvider.shared().get(localUpstreamUrl("https://it.buaa.edu.cn$path")) {
          header(HttpHeaders.Accept, "application/json, text/plain, */*")
        }
    val body = response.bodyAsText()
    if (!response.status.isSuccess()) {
      throw ApiCallException(
          message = "校园门户返回 ${response.status.value}",
          status = response.status,
          code = "portal_http_error",
      )
    }
    if (looksLikeLoginPage(body)) {
      throw ApiCallException("请先在校园门户完成登录", code = "portal_unauthenticated")
    }
    return body
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
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(ApiCallException(message = message, code = "portal_error", cause = e))
      }
}
