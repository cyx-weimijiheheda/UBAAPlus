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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class CampusNetworkInfoDto(
    val userName: String,
    val realName: String,
    val onlineIp: String,
    val sumBytes: Long,
    val remainBytes: Long,
    val productsName: String,
    val userBalance: Double,
    val walletBalance: Double,
)

data class CampusTrafficDto(
    val freeTotal: Double? = null,
    val freeRemain: Double? = null,
    val giftTotal: Double? = null,
    val giftRemain: Double? = null,
    val paidUsage: Double? = null,
)

interface CampusNetworkApiBackend {
  /** 当前在线账号信息；不在校园网或被识别为未在线时返回 success(null)。 */
  suspend fun getSrunInfo(): Result<CampusNetworkInfoDto?>

  suspend fun getTraffic(): Result<CampusTrafficDto>
}

class CampusNetworkApi internal constructor(private val backend: CampusNetworkApiBackend) {
  constructor() : this(LocalCampusNetworkApiBackend())

  suspend fun getSrunInfo(): Result<CampusNetworkInfoDto?> = backend.getSrunInfo()

  suspend fun getTraffic(): Result<CampusTrafficDto> = backend.getTraffic()
}

internal class LocalCampusNetworkApiBackend : CampusNetworkApiBackend {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun getSrunInfo(): Result<CampusNetworkInfoDto?> =
      execute("校园网信息加载失败") {
        val response =
            LocalUpstreamClientProvider.shared().get(
                localUpstreamUrl("https://gw.buaa.edu.cn/cgi-bin/rad_user_info")
            ) {
              header(HttpHeaders.Accept, "application/json, text/plain, */*")
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
          throw ApiCallException(
              message = "校园网服务返回 ${response.status.value}",
              status = response.status,
              code = "network_http_error",
          )
        }
        val root =
            runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: return@execute null
        val error = root.string("error")
        val userName = root.string("user_name")
        if (userName.isBlank()) return@execute null
        if (error.isNotBlank() && error != "ok") return@execute null
        CampusNetworkInfoDto(
            userName = userName,
            realName = root.string("real_name"),
            onlineIp = root.string("online_ip"),
            sumBytes = root.long("sum_bytes") ?: 0L,
            remainBytes = root.long("remain_bytes") ?: 0L,
            productsName = root.string("products_name"),
            userBalance = root.double("user_balance") ?: 0.0,
            walletBalance = root.double("wallet_balance") ?: 0.0,
        )
      }

  override suspend fun getTraffic(): Result<CampusTrafficDto> =
      execute("校园网流量加载失败") {
        val client = LocalUpstreamClientProvider.shared()
        val syncUrl =
            localUpstreamUrl(
                "https://sso.buaa.edu.cn/login?service=https%3A%2F%2Fapp.buaa.edu.cn%2Fa_buaa%2Fapi%2Fcas%2Findex%3Fredirect%3Dhttps%253A%252F%252Fapp.buaa.edu.cn%252Fbuaanet%252Fwap%252Fdefault%252Findex%26from%3Dwap%26login_from%3D&noAutoRedirect=1"
            )
        client.get(syncUrl) { applyAppBuaaHeaders() }
        val response =
            client.get(localUpstreamUrl("https://app.buaa.edu.cn/buaanet/wap/default/index")) {
              applyAppBuaaHeaders()
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
          throw ApiCallException(
              message = "校园网流量服务返回 ${response.status.value}",
              status = response.status,
              code = "network_http_error",
          )
        }
        if (looksLikeLoginPage(body)) {
          throw ApiCallException("校园网登录状态已失效，请重新登录", code = "network_auth")
        }
        parseTraffic(body)
      }

  private fun io.ktor.client.request.HttpRequestBuilder.applyAppBuaaHeaders() {
    header(HttpHeaders.UserAgent, APP_BUAA_USER_AGENT)
    header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    header(
        HttpHeaders.Referrer,
        localUpstreamUrl("https://app.buaa.edu.cn/buaanet/wap/default/index"),
    )
  }

  private fun looksLikeLoginPage(body: String): Boolean {
    val trimmed = body.trimStart()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) &&
        (body.contains("input name=\"execution\"") ||
            body.contains("统一身份认证", ignoreCase = true) ||
            body.contains("CAS Login", ignoreCase = true))
  }

  private fun parseTraffic(html: String): CampusTrafficDto {
    val free = parseBlock(html, "免费流量")
    val gift = parseBlock(html, "赠送流量")
    val paid = parsePaid(html)
    return CampusTrafficDto(
        freeTotal = free?.first,
        freeRemain = free?.second,
        giftTotal = gift?.first,
        giftRemain = gift?.second,
        paidUsage = paid,
    )
  }

  private fun parseBlock(html: String, label: String): Pair<Double, Double>? {
    val index = html.indexOf(label)
    if (index < 0) return null
    val segment = html.substring(index, minOf(html.length, index + 900))
    val numbers =
        Regex("""(\d+(?:\.\d+)?)""")
            .findAll(segment)
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .take(2)
            .toList()
    if (numbers.size < 2) return null
    return numbers[0] to numbers[1]
  }

  private fun parsePaid(html: String): Double? {
    val index = html.indexOf("计费流量")
    if (index < 0) return null
    val segment = html.substring(index, minOf(html.length, index + 600))
    return Regex("""(\d+(?:\.\d+)?)""")
        .findAll(segment)
        .mapNotNull { it.groupValues[1].toDoubleOrNull() }
        .firstOrNull()
  }

  private fun JsonObject.string(key: String): String =
      (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()

  private fun JsonObject.double(key: String): Double? =
      (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

  private fun JsonObject.long(key: String): Long? =
      (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

  private suspend fun <T> execute(message: String, block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: ApiCallException) {
        Result.failure(e)
      } catch (e: Exception) {
        Result.failure(ApiCallException(message = message, code = "network_error", cause = e))
      }

  private companion object {
    const val APP_BUAA_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 MicroMessenger/7.0.1"
  }
}
