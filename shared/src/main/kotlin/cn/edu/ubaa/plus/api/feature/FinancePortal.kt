package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.*
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.*
import io.ktor.http.*
import java.net.URI
import kotlinx.serialization.json.*

internal fun financeTarget(raw: String): String {
  val uri =
      runCatching { URI(LocalWebVpnSupport.fromWebVpnUrl(raw)) }.getOrNull()
          ?: throw ApiCallException("财务跳转地址异常", code = "finance_redirect")
  val paths =
      when (uri.host) {
        "sso.buaa.edu.cn" -> setOf("/login")
        "icw.buaa.edu.cn" -> setOf("/checkbuaa.aspx", "/Newindex.aspx", "/jump.aspx")
        "chaxun.icw.buaa.edu.cn" -> setOf("/gxzhcxzhb/login")
        else -> emptySet()
      }
  if (
      uri.scheme !in setOf("http", "https") ||
          uri.port !in setOf(-1, 80, 443) ||
          uri.userInfo != null ||
          uri.fragment != null ||
          uri.path !in paths
  )
      throw ApiCallException("财务跳转地址异常", code = "finance_redirect")
  return "https://${uri.host}${uri.rawPath}" + (uri.rawQuery?.let { "?$it" } ?: "")
}

/** Route-bound transient handoff values never enter settings or the data cache. */
internal class FinancePortal : AutoCloseable {
  private val client = LocalUpstreamClientProvider.newNoRedirectClient()
  private val scope = SchoolReadCoordinator.capture()
  private var home: String? = null

  private fun checkSession() {
    if (
        !scope.isCurrent() ||
            scope.owner == null ||
            LocalAuthSessionStore.get()?.user?.schoolid != scope.owner
    )
        throw ApiCallException("登录状态已失效", code = "unauthenticated")
  }

  private suspend fun get(target: String): HttpResponse {
    checkSession()
    val response = client.get(localUpstreamUrl(target)) { attributes.put(LocalPrivateRead, true) }
    val redirect =
        response.headers[HttpHeaders.Location]?.let {
          runCatching { URI(localUpstreamUrl(target)).resolve(it) }.getOrNull()
        }
    if (
        cn.edu.ubaa.plus.api.ConnectionRuntime.currentMode() ==
            cn.edu.ubaa.plus.api.ConnectionMode.WEBVPN &&
            response.status.value == 302 &&
            redirect?.host == "d.buaa.edu.cn" &&
            redirect.path == "/login" &&
            redirect.query == null &&
            redirect.fragment == null
    )
        throw ApiCallException("财务会话已失效", code = "unauthenticated")
    return response
  }

  suspend fun openHome(): String {
    home?.let {
      return it
    }
    var target =
        "https://sso.buaa.edu.cn/login?service=http%3A%2F%2Ficw.buaa.edu.cn%2Fcheckbuaa.aspx"
    repeat(12) {
      val response = get(target)
      if (response.status.value in setOf(301, 302, 303, 307, 308)) {
        val location = response.headers[HttpHeaders.Location] ?: throw ApiCallException("财务登录跳转缺失")
        target = financeTarget(URI(localUpstreamUrl(target)).resolve(location).toString())
      } else if (
          response.status == HttpStatusCode.OK &&
              URI(target).path == "/Newindex.aspx" &&
              URI(target).host == "icw.buaa.edu.cn"
      ) {
        return response.bodyAsText().also { home = it }
      } else throw ApiCallException("财务门户登录未完成，请重新登录后重试", code = "unauthenticated")
    }
    throw ApiCallException("财务登录跳转次数过多", code = "finance_redirect")
  }

  class Credential(val user: String, val token: String) {
    override fun toString() = "FinanceCredential([redacted])"
  }

  suspend fun credential(): Credential {
    openHome()
    val jump = "https://icw.buaa.edu.cn/jump.aspx?sysid=wscx"
    val response = get(jump)
    if (response.status.value !in setOf(301, 302, 303, 307, 308))
        throw ApiCallException("财务查询登录跳转异常")
    val location =
        financeTarget(
            URI(localUpstreamUrl(jump))
                .resolve(
                    response.headers[HttpHeaders.Location] ?: throw ApiCallException("财务登录信息缺失")
                )
                .toString()
        )
    val url = Url(location)
    if (url.host != "chaxun.icw.buaa.edu.cn" || url.encodedPath != "/gxzhcxzhb/login")
        throw ApiCallException("财务查询跳转异常")
    fun field(name: String) =
        url.parameters.getAll(name)?.singleOrNull()?.takeIf { it.isNotBlank() }
            ?: throw ApiCallException("财务登录信息缺失")
    val user = field("Userid")
    checkSession()
    if (user != scope.owner)
        throw ApiCallException("财务账户与当前账户不一致", code = "finance_account_mismatch")
    val login =
        client.post(localUpstreamUrl("https://chaxun.icw.buaa.edu.cn/api-zhcx/login")) {
          attributes.put(LocalPrivateRead, true)
          header(HttpHeaders.Referrer, localUpstreamUrl(location))
          setBody(
              FormDataContent(
                  Parameters.build {
                    append("username", user)
                    append("password", field("Cypt"))
                    append("yzm", "yzm")
                    append("stamp", field("Stamp"))
                  }
              )
          )
        }
    if (login.status != HttpStatusCode.OK) throw ApiCallException("财务认证失败")
    val data = financeEnvelope(login.bodyAsText())
    val token =
        (data["token"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw ApiCallException("财务认证信息缺失")
    return Credential(user, token)
  }

  suspend fun query(credential: Credential, page: Int): JsonObject {
    checkSession()
    val response =
        client.post(localUpstreamUrl("https://chaxun.icw.buaa.edu.cn/api-zhcx/getStudentSRCX")) {
          attributes.put(LocalPrivateRead, true)
          contentType(ContentType.Application.Json)
          header(HttpHeaders.Authorization, "Bearer ${credential.token}")
          setBody(
              buildJsonObject {
                    put("userId", credential.user)
                    put("currentPage", page)
                    put("limit", 100)
                  }
                  .toString()
          )
        }
    if (response.status == HttpStatusCode.Unauthorized || response.status.value in 300..399)
        throw ApiCallException("财务会话已失效", code = "finance_auth")
    if (response.status != HttpStatusCode.OK) throw ApiCallException("财务查询服务暂不可用")
    return financeEnvelope(response.bodyAsText())
  }

  override fun close() {
    client.close()
  }
}

internal fun financeEnvelope(body: String): JsonObject {
  val root =
      runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
          ?: throw ApiCallException("财务响应格式异常", code = "finance_format")
  val code = (root["code"] as? JsonPrimitive)?.intOrNull
  if (code == 401) throw ApiCallException("财务会话已失效", code = "finance_auth")
  if (code != 200) throw ApiCallException("财务服务返回失败", code = "finance_format")
  return root["data"] as? JsonObject ?: throw ApiCallException("财务响应格式异常", code = "finance_format")
}
