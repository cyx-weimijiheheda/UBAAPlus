package cn.edu.ubaa.plus.api.local

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.SIGNIN_LOGIN_REDIRECT_LIMIT
import cn.edu.ubaa.plus.api.SIGNIN_MY_CENTER_URL
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.extractSigninLoginNameFromUrl
import cn.edu.ubaa.plus.api.feature.SigninApiBackend
import cn.edu.ubaa.plus.api.resolveSigninRedirectUrl
import cn.edu.ubaa.plus.model.dto.IclassAttendanceCourseDto
import cn.edu.ubaa.plus.model.dto.IclassAttendanceRecordDto
import cn.edu.ubaa.plus.model.dto.IclassAttendanceTermDto
import cn.edu.ubaa.plus.model.dto.SigninActionResponse
import cn.edu.ubaa.plus.model.dto.SigninAttendanceResponse
import cn.edu.ubaa.plus.model.dto.SigninClassDto
import cn.edu.ubaa.plus.model.dto.SigninStatusResponse
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class LocalSigninApiBackend : SigninApiBackend {
  private val json = Json { ignoreUnknownKeys = true }
  private val sessionMutex = Mutex()
  private val sessionCache = mutableMapOf<String, LocalSigninSession>()
  private var loginLastError: String? = null

  private val courseCache = mutableMapOf<String, List<LocalSigninCourse>>()

  internal fun clearCache() {
    sessionCache.clear()
    courseCache.clear()
  }

  /** 兼容 iclass 返回的 STATUS/stuSignStatus 等字段可能为字符串或整数的情况。 */
  private fun jsonStringValue(element: JsonElement?, key: String): String? {
    val obj = element?.jsonObject ?: return null
    val prim = obj[key]?.jsonPrimitive ?: return null
    prim.contentOrNull?.let {
      return it
    }
    prim.intOrNull?.let {
      return it.toString()
    }
    prim.longOrNull?.let {
      return it.toString()
    }
    return null
  }

  private fun isStatusSuccess(jsonResponse: JsonObject): Boolean {
    val status = jsonStringValue(jsonResponse, "STATUS")
    return status == "0" || status == "200" || status == "success"
  }

  override suspend fun getTodayClasses(): Result<SigninStatusResponse> {
    return getTodayClasses(allowRetry = true)
  }

  private suspend fun getTodayClasses(allowRetry: Boolean): Result<SigninStatusResponse> {
    val authSession =
        LocalAuthSessionStore.get() ?: return Result.failure(localUnauthenticatedApiException())
    val signinSession = runCatching { currentSession(authSession.studentId()) }.getOrNull()
    if (signinSession == null) {
      return Result.success(SigninStatusResponse(code = 200, message = "获取成功"))
    }

    return try {
      val response =
          LocalUpstreamClientProvider.shared().get(
              localUpstreamUrl(
                  "https://iclass.buaa.edu.cn:8347/app/course/get_stu_course_sched.action"
              )
          ) {
            header("sessionId", signinSession.sessionId)
            parameter("id", signinSession.userId)
            parameter("dateStr", currentSigninDate())
          }
      if (response.status != HttpStatusCode.OK) {
        return Result.success(SigninStatusResponse(code = 200, message = "获取成功"))
      }
      val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
      if (payload.containsKey("STATUS") && !isStatusSuccess(payload)) {
        if (allowRetry) {
          invalidateSession(authSession.studentId())
          val refreshed = runCatching { currentSession(authSession.studentId()) }.getOrNull()
          if (refreshed != null) return getTodayClasses(allowRetry = false)
        }
        return Result.success(SigninStatusResponse(code = 200, message = "获取成功"))
      }
      val classes = payload["result"]?.jsonArray?.map(::mapSigninClass).orEmpty()
      Result.success(SigninStatusResponse(code = 200, message = "获取成功", data = classes))
    } catch (_: Exception) {
      Result.success(SigninStatusResponse(code = 200, message = "获取成功"))
    }
  }

  override suspend fun performSignin(
      courseId: String,
      timestamp: String?,
  ): Result<SigninActionResponse> {
    return performSignin(courseId, timestamp, allowRetry = true)
  }

  private suspend fun performSignin(
      courseId: String,
      scannedTimestamp: String?,
      allowRetry: Boolean,
  ): Result<SigninActionResponse> {
    val authSession =
        LocalAuthSessionStore.get() ?: return Result.failure(localUnauthenticatedApiException())
    val signinSession = runCatching { currentSession(authSession.studentId()) }.getOrNull()
    if (signinSession == null) {
      val error = loginLastError ?: "iclass 登录失败，请以校园网登录查看"
      loginLastError = null
      return Result.success(SigninActionResponse(code = 400, success = false, message = error))
    }

    return try {
      val timestamp = scannedTimestamp?.takeIf { it.isNotBlank() } ?: fetchSigninTimestamp()
      if (timestamp.isNullOrBlank()) {
        return Result.success(
            SigninActionResponse(code = 400, success = false, message = "获取服务器时间失败")
        )
      }

      val response =
          LocalUpstreamClientProvider.shared().submitForm(
              url =
                  localSigninCheckinUrl(
                      directPath = "eschool/app/course/stu_scan_sign.action",
                      webVpnPath = "app/course/stu_scan_sign.action",
                  ),
              formParameters = Parameters.build { append("id", signinSession.userId) },
          ) {
            header("sessionId", signinSession.sessionId)
            parameter("courseSchedId", courseId)
            parameter("timestamp", timestamp)
          }
      val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
      val success =
          isStatusSuccess(payload) &&
              jsonStringValue(payload["result"]?.jsonObject, "stuSignStatus") == "1"
      val rawMessage = jsonStringValue(payload, "ERRMSG")
      if (!success) {
        if (allowRetry && rawMessage?.contains("登录") == true) {
          invalidateSession(authSession.studentId())
          val refreshed = runCatching { currentSession(authSession.studentId()) }.getOrNull()
          if (refreshed != null) return performSignin(courseId, timestamp, allowRetry = false)
        }
      }
      Result.success(
          SigninActionResponse(
              code = if (success) 200 else 400,
              success = success,
              message = sanitizeLocalSigninMessage(success, rawMessage),
          )
      )
    } catch (_: Exception) {
      Result.success(SigninActionResponse(code = 400, success = false, message = "签到失败，请稍后重试"))
    }
  }

  private suspend fun fetchSigninTimestamp(): String? =
      runCatching {
            LocalUpstreamClientProvider.shared()
                .get(localSigninCheckinUrl("app/common/get_timestamp.action"))
                .bodyAsText()
                .let(json::parseToJsonElement)
                .jsonObject["timestamp"]
                ?.jsonPrimitive
                ?.content
          }
          .getOrNull()

  override suspend fun getAttendanceTerms(): Result<List<IclassAttendanceTermDto>> =
      withSigninSession("考勤学期加载失败，请以校园网登录查看") { _, session ->
        val terms = fetchTerms(session).ifEmpty { listOf(inferredTerm()) }
        terms.map { term ->
          IclassAttendanceTermDto(
              code = term.code,
              name = termDisplayName(term),
              isCurrent = term.isCurrent,
          )
        }
      }

  override suspend fun getAttendanceCourses(
      termCode: String
  ): Result<List<IclassAttendanceCourseDto>> =
      withSigninSession("课程列表加载失败，请以校园网登录查看") { _, session ->
        fetchCoursesCached(session, termCode).map { course ->
          IclassAttendanceCourseDto(
              courseId = course.id,
              courseName = course.name,
              teacherName = course.teacherName.orEmpty(),
          )
        }
      }

  override suspend fun getAttendance(
      termCode: String,
      courseId: String,
  ): Result<SigninAttendanceResponse> =
      withSigninSession("考勤查询失败，请以校园网登录查看") { _, session ->
        val term =
            runCatching { fetchTerms(session) }
                .getOrDefault(emptyList())
                .firstOrNull { it.code == termCode } ?: LocalSigninTerm(termCode, null)
        val courses = fetchCoursesCached(session, termCode)
        val course =
            courses.firstOrNull { it.id == courseId }
                ?: throw ApiCallException("未找到所选课程，请刷新后重试", code = "signin_error")
        val records = fetchCourseAttendance(session, course)
        SigninAttendanceResponse(
            code = 200,
            message = "获取成功",
            termCode = termCode,
            termName = termDisplayName(term),
            data =
                records.sortedWith(
                    compareByDescending<IclassAttendanceRecordDto> { it.teachTime }
                        .thenByDescending { it.classBeginTime }
                ),
        )
      }

  /** 统一的 iClass 会话与登录失效重试逻辑。 */
  private suspend fun <T> withSigninSession(
      failureMessage: String,
      allowRetry: Boolean = true,
      block: suspend (studentId: String, session: LocalSigninSession) -> T,
  ): Result<T> {
    val authSession =
        LocalAuthSessionStore.get() ?: return Result.failure(localUnauthenticatedApiException())
    val studentId = authSession.studentId()
    val session = runCatching { currentSession(studentId) }.getOrNull()
    if (session == null) {
      val error = loginLastError ?: "iclass 登录失败，请以校园网登录查看"
      loginLastError = null
      return Result.failure(ApiCallException(error, code = "signin_error"))
    }

    return try {
      Result.success(block(studentId, session))
    } catch (e: LocalSigninAuthenticationException) {
      if (allowRetry) {
        invalidateSession(studentId)
        withSigninSession(failureMessage, allowRetry = false, block = block)
      } else {
        Result.failure(
            ApiCallException(
                message = "iclass 登录状态已失效，请以校园网登录查看",
                code = "signin_error",
                cause = e,
            )
        )
      }
    } catch (e: ApiCallException) {
      Result.failure(e)
    } catch (e: Exception) {
      Result.failure(ApiCallException(message = failureMessage, code = "signin_error", cause = e))
    }
  }

  private suspend fun fetchTerms(session: LocalSigninSession): List<LocalSigninTerm> {
    val payload =
        iclassGetJson(
            "https://iclass.buaa.edu.cn:8347/app/course/get_base_school_year.action",
            session,
            mapOf("userId" to session.userId, "type" to "2"),
        )
    if (!isSigninStatusSuccess(payload)) return emptyList()
    val items =
        (payload["result"] as? kotlinx.serialization.json.JsonArray)
            ?: (payload["data"] as? kotlinx.serialization.json.JsonArray)
            ?: return emptyList()
    return items.mapNotNull { element ->
      val item = element as? JsonObject ?: return@mapNotNull null
      val code = jsonStringValue(item, "code")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      LocalSigninTerm(
          code = code,
          name = jsonStringValue(item, "name")?.takeIf { it.isNotBlank() },
          isCurrent = jsonStringValue(item, "yearStatus") == "1",
      )
    }
  }

  private fun inferredTerm(): LocalSigninTerm =
      LocalSigninTerm(code = inferredTermCode(), name = null, isCurrent = true)

  private fun termDisplayName(term: LocalSigninTerm): String =
      term.name?.takeIf { it.isNotBlank() } ?: formatTermCode(term.code)

  private fun formatTermCode(code: String): String {
    if (code.length < 9) return code
    val start = code.take(4).toIntOrNull() ?: return code
    val end = code.substring(4, 8).toIntOrNull() ?: return code
    val termName = if (code.last() == '2') "第二学期" else "第一学期"
    return "$start-$end 学年$termName"
  }

  private suspend fun fetchCoursesCached(
      session: LocalSigninSession,
      termCode: String,
  ): List<LocalSigninCourse> {
    courseCache[termCode]?.let {
      return it
    }
    return fetchCourses(session, termCode).also { courseCache[termCode] = it }
  }

  private suspend fun fetchCourses(
      session: LocalSigninSession,
      termCode: String?,
  ): List<LocalSigninCourse> {
    val params = buildMap {
      put("user_type", "1")
      put("id", session.userId)
      termCode?.takeIf { it.isNotBlank() }?.let { put("xq_code", it) }
    }
    val payload =
        iclassGetJson(
            "https://iclass.buaa.edu.cn:8347/app/choosecourse/get_myall_course.action",
            session,
            params,
        )
    if (isSigninNoData(payload)) return emptyList()
    requireSigninStatus(payload)
    val items = payload["result"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return items.mapNotNull { element ->
      val item = element as? JsonObject ?: return@mapNotNull null
      val id =
          (jsonStringValue(item, "course_id") ?: jsonStringValue(item, "courseId"))?.takeIf {
            it.isNotBlank()
          } ?: return@mapNotNull null
      LocalSigninCourse(
          id = id,
          name =
              jsonStringValue(item, "course_name") ?: jsonStringValue(item, "courseName") ?: "未知课程",
          teacherName =
              jsonStringValue(item, "teacher_name") ?: jsonStringValue(item, "teacherName"),
      )
    }
  }

  private suspend fun fetchCourseAttendance(
      session: LocalSigninSession,
      course: LocalSigninCourse,
  ): List<IclassAttendanceRecordDto> {
    val payload =
        iclassGetJson(
            "https://iclass.buaa.edu.cn:8347/app/my/get_my_course_sign_detail.action",
            session,
            mapOf(
                "id" to session.userId,
                "courseId" to course.id,
                "sessionId" to session.sessionId,
            ),
        )
    if (isSigninNoData(payload)) return emptyList()
    requireSigninStatus(payload)
    val items = payload["result"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
    return items.mapNotNull { element ->
      val item = element as? JsonObject ?: return@mapNotNull null
      IclassAttendanceRecordDto(
          courseSchedId =
              jsonStringValue(item, "courseSchedId") ?: jsonStringValue(item, "id") ?: "",
          courseId = course.id,
          courseName =
              jsonStringValue(item, "courseName")
                  ?: jsonStringValue(item, "course_name")
                  ?: course.name,
          teacherName =
              jsonStringValue(item, "teacherName")
                  ?: jsonStringValue(item, "teacher_name")
                  ?: course.teacherName.orEmpty(),
          teachTime =
              jsonStringValue(item, "teachTime") ?: jsonStringValue(item, "teach_time") ?: "",
          classBeginTime =
              jsonStringValue(item, "classBeginTime")
                  ?: jsonStringValue(item, "class_begin_time")
                  ?: "",
          classEndTime =
              jsonStringValue(item, "classEndTime")
                  ?: jsonStringValue(item, "class_end_time")
                  ?: "",
          signStatus = attendanceSignStatus(item),
      )
    }
  }

  private fun attendanceSignStatus(item: JsonObject): Int {
    val primitive = item["signStatus"]?.jsonPrimitive ?: return 0
    primitive.intOrNull?.let {
      return it
    }
    primitive.contentOrNull?.toIntOrNull()?.let {
      return it
    }
    val text = primitive.contentOrNull.orEmpty()
    return when {
      text.contains("已签") -> 1
      text.contains("未签") -> 0
      else -> 0
    }
  }

  private suspend fun iclassGetJson(
      url: String,
      session: LocalSigninSession,
      params: Map<String, String>,
  ): JsonObject {
    val response =
        LocalUpstreamClientProvider.shared().get(localUpstreamUrl(url)) {
          header("sessionId", session.sessionId)
          params.forEach { (key, value) -> parameter(key, value) }
        }
    if (response.status != HttpStatusCode.OK) {
      throw IllegalStateException("iclass 请求失败：HTTP ${response.status.value}")
    }
    return json.parseToJsonElement(response.bodyAsText()).jsonObject
  }

  private fun requireSigninStatus(payload: JsonObject) {
    if (isSigninStatusSuccess(payload)) return
    val message = jsonStringValue(payload, "ERRMSG")?.takeIf { it.isNotBlank() } ?: "请求失败"
    if (message.contains("登录")) throw LocalSigninAuthenticationException(message)
    throw ApiCallException(message, code = "signin_error")
  }

  private fun isSigninStatusSuccess(payload: JsonObject): Boolean {
    val status = jsonStringValue(payload, "STATUS") ?: return true
    return status == "0" || status == "200" || status == "success"
  }

  private fun isSigninNoData(payload: JsonObject): Boolean =
      jsonStringValue(payload, "STATUS") == "2"

  private suspend fun currentSession(studentId: String): LocalSigninSession? {
    sessionMutex
        .withLock { sessionCache[studentId] }
        ?.let {
          return it
        }
    val created = login(studentId) ?: return null
    return sessionMutex.withLock { sessionCache.getOrPut(studentId) { created } }
  }

  private fun invalidateSession(studentId: String) {
    sessionCache.remove(studentId)
    courseCache.clear()
  }

  private suspend fun login(studentId: String): LocalSigninSession? {
    val loginName = resolveLoginName() ?: return null
    val response =
        LocalUpstreamClientProvider.shared().get(
            localUpstreamUrl("https://iclass.buaa.edu.cn:8347/app/user/login.action")
        ) {
          parameter("password", "")
          parameter("phone", loginName)
          parameter("userLevel", "1")
          parameter("verificationType", "2")
          parameter("verificationUrl", "")
        }
    if (response.status != HttpStatusCode.OK) {
      return null
    }

    val payload = json.parseToJsonElement(response.bodyAsText()).jsonObject
    if (!isStatusSuccess(payload)) {
      loginLastError = jsonStringValue(payload, "ERRMSG")?.takeIf { it.isNotBlank() } ?: "登录失败"
      return null
    }

    val result = payload["result"]?.jsonObject ?: return null
    val userId = result["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
    val sessionId =
        result["sessionId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return null
    return LocalSigninSession(userId = userId, sessionId = sessionId)
  }

  private suspend fun resolveLoginName(): String? {
    val client = LocalUpstreamClientProvider.newNoRedirectClient()
    return try {
      // 参考 UBAANext：保持原始 URL 不变，仅在发请求时做 VPN 包装
      var rawUrl = SIGNIN_MY_CENTER_URL
      extractSigninLoginNameFromUrl(rawUrl)?.let {
        return it
      }
      repeat(SIGNIN_LOGIN_REDIRECT_LIMIT) {
        val response = client.get(localUpstreamUrl(rawUrl))
        val finalUrl = LocalWebVpnSupport.fromWebVpnUrl(response.call.request.url.toString())
        extractSigninLoginNameFromUrl(finalUrl)?.let {
          return it
        }

        val location =
            response.headers[HttpHeaders.Location]?.let(LocalWebVpnSupport::fromWebVpnUrl)
        location?.let {
          extractSigninLoginNameFromUrl(it)?.let { loginName ->
            return loginName
          }
        }
        if (response.status.value !in 300..399 || location.isNullOrBlank()) {
          return null
        }
        rawUrl = resolveSigninRedirectUrl(finalUrl, location) ?: return null
        extractSigninLoginNameFromUrl(rawUrl)?.let { loginName ->
          return loginName
        }
      }
      null
    } finally {
      client.close()
    }
  }
}

private data class LocalSigninSession(
    val userId: String,
    val sessionId: String,
)

private data class LocalSigninTerm(
    val code: String,
    val name: String?,
    val isCurrent: Boolean = false,
)

private data class LocalSigninCourse(
    val id: String,
    val name: String,
    val teacherName: String?,
)

internal class LocalSigninAuthenticationException(message: String) : RuntimeException(message)

// 8347 的签到提交入口没有 /eschool 前缀，端口和路径需要一起选择。
private fun localSigninCheckinUrl(directPath: String, webVpnPath: String = directPath): String {
  val upstreamUrl =
      when (ConnectionRuntime.currentMode()) {
        ConnectionMode.WEBVPN -> "https://iclass.buaa.edu.cn:8347/$webVpnPath"
        else -> "http://iclass.buaa.edu.cn:8081/$directPath"
      }
  return localUpstreamUrl(upstreamUrl)
}

private fun mapSigninClass(element: JsonElement): SigninClassDto {
  val payload = element.jsonObject
  return SigninClassDto(
      courseId = payload["id"]?.jsonPrimitive?.content ?: "",
      courseName = payload["courseName"]?.jsonPrimitive?.content ?: "",
      classBeginTime = payload["classBeginTime"]?.jsonPrimitive?.content ?: "",
      classEndTime = payload["classEndTime"]?.jsonPrimitive?.content ?: "",
      signStatus = flexibleIntValue(payload, "signStatus"),
  )
}

/** 兼容字段可能为字符串或整数的情况，统一返回 Int。 */
private fun flexibleIntValue(obj: JsonObject, key: String): Int {
  val prim = obj[key]?.jsonPrimitive ?: return 0
  prim.intOrNull?.let {
    return it
  }
  prim.contentOrNull?.toIntOrNull()?.let {
    return it
  }
  return 0
}

private fun LocalAuthSession.studentId(): String = user.schoolid.ifBlank { username }

private fun currentSigninDate(): String {
  val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
  return buildString {
    append(date.year)
    append(date.month.ordinal.plus(1).toString().padStart(2, '0'))
    append(date.day.toString().padStart(2, '0'))
  }
}

/** 学期接口不可用时按月份推断，例如 2026 年 10 月 -> 202620271。 */
private fun inferredTermCode(): String {
  val date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
  val year = date.year
  val month = date.month.ordinal + 1
  return when {
    month >= 9 -> "${year}${year + 1}1"
    month == 1 -> "${year - 1}${year}1"
    else -> "${year - 1}${year}2"
  }
}

internal fun sanitizeLocalSigninMessage(success: Boolean, rawMessage: String?): String {
  if (success) return rawMessage?.takeIf { it.isNotBlank() } ?: "签到成功"
  val message = rawMessage.orEmpty()
  return when {
    "已签到" in message -> "您今天已经签到过了"
    "未开始" in message -> "当前还未到签到时间"
    "不是上课时间" in message -> "当前不是上课时间，无法签到"
    "已结束" in message -> "本次签到已结束"
    "范围" in message -> "当前不在可签到范围内"
    "用户不存在" in message -> "签到账号不存在，请联系管理员"
    "课程" in message && "不存在" in message -> "未找到对应课程，请刷新后重试"
    else -> "签到失败，请稍后重试"
  }
}
