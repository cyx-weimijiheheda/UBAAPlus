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

@kotlinx.serialization.Serializable
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

@kotlinx.serialization.Serializable
data class CampusTrafficAmount(val value: String, val unit: String) {
  fun display(): String = "$value ${if (unit == "byte") "B" else unit + "B"}"
}

@kotlinx.serialization.Serializable
data class CampusTrafficProduct(
    val name: String,
    val used: CampusTrafficAmount,
    val freeRemaining: CampusTrafficAmount,
    val billableRemaining: CampusTrafficAmount,
)

@kotlinx.serialization.Serializable
data class CampusTrafficDto(
    val products: List<CampusTrafficProduct>,
    val fetchedAt: Long = System.currentTimeMillis(),
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
      cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator.read("network", "traffic") {
        execute("校园网流量加载失败") {
          LocalUpstreamClientProvider.newNoRedirectClient().use { client ->
            suspend fun home() =
                client.get(localUpstreamUrl(NETWORK_HOME)) {
                  header(HttpHeaders.Referrer, localUpstreamUrl("https://zfw.buaa.edu.cn/pay/card"))
                }
            var response = home()
            if (response.status.value in setOf(301, 302, 303, 307, 308, 401, 403)) {
              activateNetwork(client)
              response = home()
            }
            if (!response.status.isSuccess())
                throw ApiCallException("网费自助服务登录失效，请重新登录", code = "unauthenticated")
            parseCampusTraffic(response.bodyAsText())
          }
        }
      }

  private suspend fun activateNetwork(client: io.ktor.client.HttpClient) {
    var target = "https://zfw.buaa.edu.cn/sso/cas/index"
    repeat(10) {
      val response = client.get(localUpstreamUrl(target))
      if (response.status.value in setOf(301, 302, 303, 307, 308)) {
        val location =
            response.headers[HttpHeaders.Location] ?: throw ApiCallException("网费自助服务响应不完整")
        val next = java.net.URI(localUpstreamUrl(target)).resolve(location)
        // Only university authentication and the network service may receive this session.
        if (
            next.scheme !in setOf("https", "http") ||
                next.host !in setOf("zfw.buaa.edu.cn", "sso.buaa.edu.cn", "d.buaa.edu.cn")
        )
            throw ApiCallException("网费服务重定向异常")
        val decoded =
            java.net.URI(
                cn.edu.ubaa.plus.api.local.LocalWebVpnSupport.fromWebVpnUrl(next.toString())
            )
        if (decoded.host !in setOf("zfw.buaa.edu.cn", "sso.buaa.edu.cn"))
            throw ApiCallException("网费服务重定向异常")
        target = cn.edu.ubaa.plus.api.local.LocalWebVpnSupport.normalizeRedirectUrl(next.toString())
      } else {
        if (
            response.status.isSuccess() &&
                (target.contains("/pay/card") || target.contains("/home"))
        )
            return
        throw ApiCallException("网费自助服务需要重新登录", code = "unauthenticated")
      }
    }
    throw ApiCallException("网费自助服务跳转次数过多")
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
    const val NETWORK_HOME = "https://zfw.buaa.edu.cn/home"
  }
}
