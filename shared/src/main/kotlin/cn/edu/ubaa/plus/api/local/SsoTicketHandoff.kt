package cn.edu.ubaa.plus.api.local

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.auth.ApiCallException
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import java.net.URI

/**
 * 用应用内已有的直连 SSO 会话换取一次性 CAS service ticket。
 *
 * 只返回带 ticket 的 service callback URL；不跟随重定向、不持久化 ticket。
 */
internal object SsoTicketHandoff {
  suspend fun authenticatedUrl(targetUrl: String): Result<String> {
    val uri =
        runCatching { Url(targetUrl) }.getOrNull()
            ?: return Result.failure(ApiCallException("无效网址", code = "sso_invalid_url"))
    if (uri.protocol.name != "http" && uri.protocol.name != "https") {
      return Result.success(targetUrl)
    }
    val service = serviceCallback(uri) ?: return Result.success(targetUrl)
    val loginUrl = "https://sso.buaa.edu.cn/login?service=${service.encodeURLParameter()}"

    return try {
      val client =
          LocalUpstreamClientProvider.newClient(
              LocalCookieStore.storage(ConnectionMode.DIRECT),
              followRedirects = false,
          )
      val response = client.get(loginUrl)
      val location = response.headers[HttpHeaders.Location]
      if (location.isNullOrBlank()) {
        return Result.failure(ApiCallException("统一身份认证会话已失效", code = "sso_ticket_failed"))
      }
      val resolved =
          runCatching { URI(loginUrl).resolve(location).toString() }.getOrDefault(location)
      if (resolved.contains("ticket=")) {
        Result.success(resolved)
      } else {
        Result.failure(ApiCallException("统一身份认证未返回 ticket", code = "sso_ticket_failed"))
      }
    } catch (e: Exception) {
      Result.failure(ApiCallException("统一身份认证跳转失败", code = "sso_ticket_error", cause = e))
    }
  }

  private fun serviceCallback(uri: Url): String? {
    val host = uri.host.lowercase()
    val path = uri.encodedPath
    return when {
      host == "shsd.buaa.edu.cn" || host == "zfw.buaa.edu.cn" || host == "xylktsd.buaa.edu.cn" ->
          null
      host == "it.buaa.edu.cn" ->
          "https://it.buaa.edu.cn/manage/common/cas_login/30001?redirect=" +
              uri.toString().encodeURLParameter()
      host == "cgyy.buaa.edu.cn" && path.startsWith("/venue-zhjs") ->
          "https://cgyy.buaa.edu.cn/venue-zhjs-server/sso/manageLogin"
      host == "cgyy.buaa.edu.cn" -> "https://cgyy.buaa.edu.cn/venue-server/sso/manageLogin"
      host == "app.buaa.edu.cn" ->
          "https://app.buaa.edu.cn/a_buaa/api/cas/index?redirect=" +
              uri.toString().encodeURLParameter()
      host == "pass.cc-pay.cn" ->
          "https://pass.cc-pay.cn/login?backUrl=" + uri.toString().encodeURLParameter()
      host == "space.lib.buaa.edu.cn" ->
          "https://space.lib.buaa.edu.cn:443/meta-local/opac/cas/rosetta"
      host.endsWith(".buaa.edu.cn") || host == "buaa.edu.cn" -> uri.toString()
      else -> null
    }
  }
}
