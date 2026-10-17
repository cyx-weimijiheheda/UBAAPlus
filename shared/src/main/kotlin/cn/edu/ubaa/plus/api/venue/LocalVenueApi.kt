package cn.edu.ubaa.plus.api.venue

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.LocalCasParser
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.model.dto.CaptchaInfo
import cn.edu.ubaa.plus.model.dto.LoginRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 运动场地直连客户端。
 *
 * 与研讨室（venue-zhjs）不同，运动场地使用 `venue-server`，但同样可以直连，不需要 WebVPN。 会话依赖直接模式的 SSO
 * Cookie（`sso_buaa_token`）；没有 Cookie 时可通过 CAS 表单登录。
 */
internal class LocalVenueApi(
    private val clientProvider: () -> HttpClient = {
      LocalUpstreamClientProvider.newClient(
          cookieStorage = LocalCookieStore.storage(ConnectionMode.DIRECT),
          followRedirects = true,
      )
    },
    private val signer: VenueSigner = VenueSigner(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : VenueApiBackend {

  private val client: HttpClient by lazy(clientProvider)
  private val loginMutex = Mutex()

  @Volatile private var accessToken: String? = null

  override fun logout() {
    accessToken = null
  }

  override suspend fun resumeSession(): VenueAuthOutcome {
    if (!accessToken.isNullOrBlank()) return VenueAuthOutcome.Ready
    return loginMutex.withLock {
      if (!accessToken.isNullOrBlank()) {
        VenueAuthOutcome.Ready
      } else {
        try {
          activateVenueSso()
          val ssoToken = readSsoToken()
          if (ssoToken.isNullOrBlank()) {
            VenueAuthOutcome.NeedsCredentials()
          } else {
            exchangeToken(ssoToken)
          }
        } catch (e: Exception) {
          VenueAuthOutcome.Failed(e.message ?: "运动场地会话建立失败")
        }
      }
    }
  }

  override suspend fun login(
      username: String,
      password: String,
      captcha: String?,
      execution: String?,
  ): VenueAuthOutcome =
      loginMutex.withLock {
        accessToken = null
        try {
          val page =
              client.request("${HOST_URL}/venue-server/sso/manageLogin") { method = HttpMethod.Get }
          val finalUrl = page.call.request.url
          if (finalUrl.host != SSO_HOST) {
            val ssoToken = readSsoToken()
            return@withLock if (ssoToken.isNullOrBlank()) {
              VenueAuthOutcome.Failed("无法进入统一身份认证页面")
            } else {
              exchangeToken(ssoToken)
            }
          }

          val html = page.bodyAsText()
          val actualExecution =
              execution?.takeIf { it.isNotBlank() } ?: LocalCasParser.extractExecution(html)
          val captchaInfo = LocalCasParser.detectCaptcha(html, SSO_CAPTCHA_URL)
          if (captchaInfo != null && captcha.isNullOrBlank()) {
            return@withLock VenueAuthOutcome.NeedsCaptcha(
                captcha = withCaptchaImage(captchaInfo),
                execution = actualExecution,
                username = username,
            )
          }

          val request =
              LoginRequest(
                  username = username,
                  password = password,
                  captcha = captcha,
                  execution = actualExecution,
              )
          val parameters =
              if (captchaInfo != null || !captcha.isNullOrBlank()) {
                LocalCasParser.buildCaptchaLoginParameters(request, actualExecution)
              } else {
                LocalCasParser.buildCasLoginParameters(html, request)
              }

          val response =
              client.request(finalUrl.toString()) {
                method = HttpMethod.Post
                setBody(FormDataContent(parameters))
              }
          val responseHost = response.call.request.url.host
          if (responseHost == SSO_HOST || !response.status.isSuccess()) {
            val body = response.bodyAsText()
            val newCaptcha = LocalCasParser.detectCaptcha(body, SSO_CAPTCHA_URL)
            if (newCaptcha != null) {
              return@withLock VenueAuthOutcome.NeedsCaptcha(
                  captcha = withCaptchaImage(newCaptcha),
                  execution = LocalCasParser.extractExecution(body).ifBlank { actualExecution },
                  username = username,
              )
            }
            val error =
                LocalCasParser.findLoginError(body)
                    ?: LocalCasParser.extractTipText(body)
                    ?: "登录失败，请检查学号或密码"
            return@withLock VenueAuthOutcome.Failed(error)
          }

          val ssoToken =
              readSsoToken() ?: return@withLock VenueAuthOutcome.Failed("登录成功但未获取到运动场地会话")
          exchangeToken(ssoToken)
        } catch (e: Exception) {
          VenueAuthOutcome.Failed(e.message ?: "运动场地登录失败")
        }
      }

  override suspend fun getSites(): Result<VenueSiteList> =
      execute("运动场地列表加载失败，请稍后重试") {
        val envelope = requestEnvelope(HttpMethod.Get, "/api/reservation/campus/venue/info")
        val data =
            envelope.requireOk("运动场地列表加载失败")["data"]?.jsonObject
                ?: return@execute VenueSiteList(emptyList(), 3)
        parseVenueSiteList(data)
      }

  override suspend fun getDayInfo(siteId: Int, date: String): Result<VenueDayInfo?> =
      execute("运动场地可用信息加载失败，请稍后重试") {
        val envelope =
            requestEnvelope(
                HttpMethod.Get,
                "/api/reservation/day/info",
                params =
                    mapOf(
                        "venueSiteId" to siteId,
                        "searchDate" to date,
                        "hasReserveInfo" to 1,
                    ),
            )
        val code = envelope["code"]?.jsonPrimitive?.intOrNull
        if (code != null && code != 200) {
          val message = envelope.messageOrNull().orEmpty()
          if (code == 250 || message.contains("最多只能查看") || message.contains("尚未")) {
            return@execute null
          }
          throw VenueApiException(message.ifBlank { "运动场地可用信息加载失败" })
        }
        val data = envelope["data"]?.jsonObject ?: return@execute null
        parseVenueDayInfo(data, siteId, date)
      }

  override suspend fun getBuddies(): Result<List<VenueBuddy>> =
      execute("同伴列表加载失败，请稍后重试") {
        val envelope =
            requestEnvelope(
                HttpMethod.Get,
                "/api/buddies",
                params = mapOf("page" to -1, "size" to -1),
            )
        parseVenueBuddies(envelope.requireOk("同伴列表加载失败")["data"])
      }

  override suspend fun addBuddy(userUid: String): Result<Unit> =
      execute("添加同伴失败，请稍后重试") {
        requestEnvelope(
                HttpMethod.Post,
                "/api/buddies",
                form = mapOf("buddyType" to 1, "userUid" to userUid),
            )
            .requireOk("添加同伴失败")
        Unit
      }

  override suspend fun deleteBuddy(buddyId: Int): Result<Unit> =
      execute("删除同伴失败，请稍后重试") {
        requestEnvelope(HttpMethod.Post, "/api/buddies/del/$buddyId").requireOk("删除同伴失败")
        Unit
      }

  override suspend fun getCaptcha(): Result<VenueCaptchaChallenge> =
      execute("验证码加载失败，请稍后重试") {
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val envelope =
            requestEnvelope(
                HttpMethod.Get,
                "/api/captcha/get",
                params =
                    mapOf(
                        "captchaType" to CAPTCHA_TYPE,
                        "clientUid" to CLIENT_UID,
                        "ts" to timestamp,
                    ),
            )
        val data =
            envelope.requireOk("验证码加载失败")["data"]?.jsonObject ?: throw VenueApiException("验证码响应为空")
        parseVenueCaptcha(data) ?: throw VenueApiException("验证码响应格式不正确")
      }

  override suspend fun verifyCaptcha(
      challenge: VenueCaptchaChallenge,
      points: List<VenuePoint>,
      imageWidth: Int,
      imageHeight: Int,
  ): Result<VenueCaptchaResult> =
      execute("验证码校验失败，请重试") {
        val payload = VenueCrypto.captchaPayload(challenge, points, imageWidth, imageHeight)
        val envelope =
            requestEnvelope(
                HttpMethod.Post,
                "/api/captcha/check",
                form =
                    mapOf(
                        "captchaType" to CAPTCHA_TYPE,
                        "pointJson" to payload.pointJson,
                        "token" to challenge.token,
                    ),
            )
        val data =
            envelope.requireOk("验证码校验失败")["data"]?.jsonObject
                ?: throw VenueApiException("验证码校验响应为空")
        if (data.stringOrNull("repCode") != "0000") {
          throw VenueApiException("验证码错误，请重新点击", code = "venue_captcha_failed")
        }
        VenueCaptchaResult(verification = payload.verification, captchaToken = challenge.token)
      }

  override suspend fun submitOrder(request: VenueOrderRequest): Result<VenueOrderResult> =
      execute("运动场地预约失败，请稍后重试") {
        val envelope =
            requestEnvelope(
                HttpMethod.Post,
                "/api/reservation/order/submit",
                form = buildVenueSubmitForm(request),
            )
        parseVenueOrderResult(envelope.requireOk("运动场地预约失败"))
      }

  override suspend fun pay(tradeNo: String, payType: Int): Result<VenuePayResult> =
      execute("支付订单创建失败，请稍后重试") {
        val envelope =
            requestEnvelope(
                HttpMethod.Post,
                "/api/venue/finances/order/pay",
                form = mapOf("payType" to payType, "venueTradeNo" to tradeNo),
            )
        parseVenuePayResult(envelope.requireOk("支付订单创建失败"))
      }

  // ---------- 会话 ----------

  private suspend fun activateVenueSso() {
    client.request("${HOST_URL}/venue-server/sso/manageLogin") { method = HttpMethod.Get }
  }

  private suspend fun readSsoToken(): String? =
      LocalCookieStore.storage(ConnectionMode.DIRECT)
          .get(Url("$HOST_URL/"))
          .firstOrNull { it.name == SSO_COOKIE_NAME && it.value.isNotBlank() }
          ?.value

  private suspend fun exchangeToken(ssoToken: String): VenueAuthOutcome {
    val envelope =
        requestEnvelope(
            HttpMethod.Post,
            "/api/login",
            extraHeaders = mapOf("sso-token" to ssoToken),
            includeAuthorization = false,
        )
    envelope.requireOk("运动场地登录失败")
    val data = envelope["data"]?.jsonObject ?: return VenueAuthOutcome.Failed("运动场地登录响应为空")

    var token = (data["token"] as? JsonObject)?.get("access_token")?.jsonPrimitive?.contentOrNull
    val roles = (data["roles"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    if (roles.size > 1 && !token.isNullOrBlank()) {
      val role =
          roles.firstOrNull { it.stringOrNull("name").orEmpty().contains("学生") }
              ?: roles.firstOrNull { it.stringOrNull("name").orEmpty().contains("教师") }
              ?: roles.firstOrNull()
      val roleId = role?.intOrNull("id")
      if (roleId != null) {
        val roleEnvelope =
            requestEnvelope(
                HttpMethod.Post,
                "/roleLogin",
                form = mapOf("roleid" to roleId),
                extraHeaders = mapOf("cgAuthorization" to requireNotNull(token)),
                includeAuthorization = false,
            )
        val roleData = roleEnvelope["data"] as? JsonObject
        token =
            (roleData?.get("token") as? JsonObject)
                ?.get("access_token")
                ?.jsonPrimitive
                ?.contentOrNull ?: token
      }
    }

    accessToken = token
    return if (token.isNullOrBlank()) {
      VenueAuthOutcome.Failed("运动场地登录成功但未返回业务 token")
    } else {
      VenueAuthOutcome.Ready
    }
  }

  private suspend fun ensureSession() {
    if (!accessToken.isNullOrBlank()) return
    when (val outcome = resumeSession()) {
      VenueAuthOutcome.Ready -> Unit
      else -> throw VenueAuthRequiredException(outcome)
    }
  }

  private fun isLoginRedirect(response: HttpResponse, body: String): Boolean {
    val host = response.call.request.url.host
    return response.status == HttpStatusCode.Unauthorized ||
        host == SSO_HOST ||
        (body.contains("name=\"execution\"") && body.contains("username_password"))
  }

  @OptIn(ExperimentalEncodingApi::class)
  private suspend fun withCaptchaImage(info: CaptchaInfo): CaptchaInfo =
      runCatching {
            val response = client.request(info.imageUrl) { method = HttpMethod.Get }
            if (!response.status.isSuccess()) {
              info
            } else {
              val bytes = response.body<ByteArray>()
              info.copy(base64Image = "data:image/jpeg;base64,${Base64.encode(bytes)}")
            }
          }
          .getOrDefault(info)

  // ---------- 请求 ----------

  private suspend fun requestEnvelope(
      method: HttpMethod,
      path: String,
      params: Map<String, Any?> = emptyMap(),
      form: Map<String, Any?> = emptyMap(),
      extraHeaders: Map<String, String> = emptyMap(),
      includeAuthorization: Boolean = true,
      allowRetry: Boolean = true,
  ): JsonObject {
    if (includeAuthorization) ensureSession()

    val timestamp = Clock.System.now().toEpochMilliseconds()
    val actualParams =
        if (method == HttpMethod.Get) signer.addNoCache(params, timestamp) else params
    val signSource = if (method == HttpMethod.Get) actualParams else form
    val sign = signer.sign(path, signSource, timestamp)
    val token = accessToken

    val response =
        client.request(buildUrl(path)) {
          this.method = method
          header(HttpHeaders.Accept, "application/json, text/plain, */*")
          header(HttpHeaders.Referrer, "$HOST_URL/venue/")
          header("app-key", signer.appKey)
          header("timestamp", timestamp.toString())
          header("sign", sign)
          if (includeAuthorization && !token.isNullOrBlank()) {
            header("cgAuthorization", token)
          }
          extraHeaders.forEach { (key, value) -> header(key, value) }

          if (method == HttpMethod.Get) {
            actualParams.forEach { (key, value) ->
              if (value != null) parameter(key, value.toString())
            }
          } else {
            val parameters =
                Parameters.build {
                  form.forEach { (key, value) -> if (value != null) append(key, value.toString()) }
                }
            setBody(FormDataContent(parameters))
          }
        }

    val body = response.bodyAsText()
    if (isLoginRedirect(response, body)) {
      accessToken = null
      if (!allowRetry) {
        throw VenueAuthRequiredException(VenueAuthOutcome.NeedsCredentials())
      }
      ensureSession()
      return requestEnvelope(
          method = method,
          path = path,
          params = params,
          form = form,
          extraHeaders = extraHeaders,
          includeAuthorization = includeAuthorization,
          allowRetry = false,
      )
    }

    return runCatching { json.parseToJsonElement(body).jsonObject }
        .getOrElse { throw VenueApiException("运动场地系统返回了非 JSON 响应") }
  }

  private fun buildUrl(path: String): String = "$HOST_URL/venue-server/${path.removePrefix("/")}"

  private fun JsonObject.requireOk(defaultMessage: String): JsonObject {
    val code = this["code"]?.jsonPrimitive?.intOrNull
    if (code != null && code != 200) {
      val message = messageOrNull() ?: defaultMessage
      throw VenueApiException(
          message = message,
          businessCode = code,
      )
    }
    return this
  }

  private fun JsonObject.messageOrNull(): String? = stringOrNull("message") ?: stringOrNull("msg")

  private suspend fun <T> execute(message: String, block: suspend () -> T): Result<T> =
      try {
        Result.success(block())
      } catch (e: VenueAuthRequiredException) {
        Result.failure(
            ApiCallException(
                message = "运动场地登录状态已失效，请重新登录",
                status = HttpStatusCode.Unauthorized,
                code = "venue_unauthenticated",
                cause = e,
            )
        )
      } catch (e: VenueApiException) {
        Result.failure(
            ApiCallException(
                message = e.message ?: message,
                status = null,
                code = e.code,
                cause = e,
            )
        )
      } catch (e: Exception) {
        Result.failure(
            ApiCallException(message = message, status = null, code = "venue_error", cause = e)
        )
      }

  private companion object {
    const val HOST_URL = "https://cgyy.buaa.edu.cn"
    const val SSO_HOST = "sso.buaa.edu.cn"
    const val SSO_COOKIE_NAME = "sso_buaa_token"
    const val SSO_CAPTCHA_URL = "https://sso.buaa.edu.cn/captcha"
    const val CAPTCHA_TYPE = "clickWord"
    const val CLIENT_UID = "buaa-venue-selector"
  }
}

internal class VenueApiException(
    message: String,
    val code: String = "venue_error",
    val businessCode: Int? = null,
) : Exception(message)

internal class VenueAuthRequiredException(val outcome: VenueAuthOutcome) : Exception("运动场地登录状态失效")
