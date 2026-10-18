package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.api.local.localUpstreamUrl
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class CampusCardBalanceDto(
    val value: Double?,
    val unclaimedAmount: Double?,
    val unit: String = "元",
)

interface CampusCardApiBackend {
  suspend fun getBalance(): Result<CampusCardBalanceDto>
}

class CampusCardApi internal constructor(private val backend: CampusCardApiBackend) {
  constructor() : this(LocalCampusCardApiBackend())

  suspend fun getBalance(): Result<CampusCardBalanceDto> = backend.getBalance()
}

internal class LocalCampusCardApiBackend(
    private val portalApi: ItPortalApi = ItPortalApi(),
) : CampusCardApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun getBalance(): Result<CampusCardBalanceDto> {
    val portal = portalApi.getCardBalance()
    if (portal.isSuccess) {
      val value = portal.getOrNull()
      if (value?.value != null) {
        return Result.success(
            CampusCardBalanceDto(
                value = value.value,
                unclaimedAmount = value.unclaimedAmount,
                unit = value.unit,
            )
        )
      }
    }
    return getPassBalance()
  }

  private suspend fun getPassBalance(): Result<CampusCardBalanceDto> =
      try {
        val session = LocalAuthSessionStore.get()
        val studentId =
            session?.user?.schoolid?.takeIf { it.isNotBlank() }
                ?: session?.username
                ?: return Result.failure(
                    ApiCallException("请先登录后再查询校园卡", code = "card_unauthenticated")
                )
        val client = LocalUpstreamClientProvider.shared()
        client.get(
            localUpstreamUrl(
                "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fpass.cc-pay.cn%2Flogin"
            )
        )
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val response =
            client.get(
                localUpstreamUrl(
                    "https://pass.cc-pay.cn/api/campus_card/balance?t=$timestamp&stuNo=$studentId"
                )
            ) {
              header(HttpHeaders.Accept, "application/json, text/plain, */*")
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
          throw ApiCallException(
              message = "校园卡服务返回 ${response.status.value}",
              status = response.status,
              code = "card_http_error",
          )
        }
        val root = parseObject(body)
        val success = (root["success"] as? JsonPrimitive)?.contentOrNull == "true"
        if (!success) {
          throw ApiCallException("校园卡余额查询失败", code = "card_query_failed")
        }
        val data = root["data"] as? JsonObject ?: root
        Result.success(
            CampusCardBalanceDto(
                value = data.double("balance"),
                unclaimedAmount = data.double("unclaimedAmount"),
            )
        )
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(ApiCallException(message = "校园卡余额加载失败", code = "card_error", cause = e))
      }

  private fun parseObject(body: String): JsonObject {
    val start = body.indexOf('{')
    val end = body.lastIndexOf('}')
    if (start < 0 || end <= start) {
      throw ApiCallException("校园卡服务返回格式异常", code = "card_parse_error")
    }
    return json.parseToJsonElement(body.substring(start, end + 1)) as? JsonObject
        ?: throw ApiCallException("校园卡服务返回格式异常", code = "card_parse_error")
  }

  private fun JsonObject.double(key: String): Double? =
      (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
}
