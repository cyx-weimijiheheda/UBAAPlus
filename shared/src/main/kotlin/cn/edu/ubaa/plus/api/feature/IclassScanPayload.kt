package cn.edu.ubaa.plus.api.feature

import java.net.URLDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 从 iClass 签到二维码中解析出的签到参数。 */
internal data class IclassSigninPayload(val courseSchedId: String, val timestamp: String?)

private val courseSchedIdRegex = Regex("""(?i)courseSchedId=([^&#\s]+)""")
private val timestampRegex = Regex("""(?i)timestamp=([^&#\s]+)""")

/**
 * 解析扫码结果。
 *
 * 支持完整 URL、带 `#` 的 URL、纯 query 以及 `a=1&b=2` 形式；参数名大小写不敏感。 缺少 `courseSchedId` 时返回 null。
 */
internal fun parseIclassSigninPayload(raw: String): IclassSigninPayload? {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return null

  // 部分客户端会把签到参数编码成 JSON；优先按 JSON 解析。
  if (trimmed.startsWith("{")) {
    val payload = runCatching { Json.parseToJsonElement(trimmed).jsonObject }.getOrNull()
    val id = payload?.stringField("courseSchedId", "courseSchedID", "course_sched_id")
    if (!id.isNullOrBlank()) {
      val timestamp =
          payload.stringField("timestamp", "timeStamp")?.takeIf {
            it.isNotBlank() && it.all(Char::isDigit)
          }
      return IclassSigninPayload(courseSchedId = id, timestamp = timestamp)
    }
  }

  val query = trimmed.substringAfter('?', trimmed).substringBefore('#').trim()
  val params = parseQuery(query)

  val courseSchedId =
      params.entries
          .firstOrNull { it.key.equals("courseSchedId", ignoreCase = true) }
          ?.value
          ?.takeIf { it.isNotBlank() }
          ?: courseSchedIdRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let(::decode)?.takeIf {
            it.isNotBlank()
          }
          ?: return null

  val timestamp =
      params.entries
          .firstOrNull { it.key.equals("timestamp", ignoreCase = true) }
          ?.value
          ?.let(::decode)
          ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
          ?: timestampRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let(::decode)?.takeIf {
            it.isNotBlank() && it.all(Char::isDigit)
          }

  return IclassSigninPayload(courseSchedId = decode(courseSchedId), timestamp = timestamp)
}

private fun parseQuery(query: String): Map<String, String> =
    query
        .split('&')
        .mapNotNull { part ->
          val index = part.indexOf('=')
          if (index <= 0) {
            null
          } else {
            part.substring(0, index).trim() to part.substring(index + 1).trim()
          }
        }
        .toMap()

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

private fun JsonObject.stringField(vararg keys: String): String? {
  for (key in keys) {
    val value = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    if (value != null) return value
  }
  return null
}
