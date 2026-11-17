package cn.edu.ubaa.plus.api.storage

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.ApiCallException
import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 不收集异常原文、URL、堆栈、账号和学校正文。仅本地保留，用户主动导出。 */
internal object LocalDiagnostics {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: Settings().also { backing = it }
    set(value) {
      backing = value
    }

  @Serializable
  data class Issue(
      val id: String,
      val at: Long,
      val feature: String,
      val operation: String,
      val route: String?,
      val kind: String,
      val status: Int?,
  )

  private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }
  private val allowedFeatures =
      setOf(
          "schedule",
          "grades",
          "exams",
          "spoc",
          "judge",
          "bykc",
          "libbook",
          "card",
          "network",
          "auth",
          "cgyy",
          "venue",
          "signin",
          "ygdk",
      )

  @Synchronized
  fun record(feature: String, operation: String, error: Throwable): String? =
      runCatching {
            if (feature !in allowedFeatures) return null
            val e = error as? ApiCallException
            val status = e?.status?.value
            val kind =
                when {
                  e?.code == "outcome_unknown" -> "outcome_unknown"
                  status == 401 -> "authentication"
                  status == 429 -> "rate_limited"
                  status != null && status >= 500 -> "upstream"
                  status != null && status >= 400 -> "rejected"
                  error is kotlinx.serialization.SerializationException -> "parse"
                  error is java.io.IOException || error.cause is java.io.IOException -> "network"
                  else -> "unknown"
                }
            val id = java.util.UUID.randomUUID().toString().take(8)
            val issue =
                Issue(
                    id,
                    Clock.System.now().toEpochMilliseconds(),
                    feature,
                    operation.takeIf { it in setOf("read", "write", "login", "restore") } ?: "read",
                    ConnectionRuntime.currentMode()?.storageKey,
                    kind,
                    status,
                )
            settings.putString(
                "local_diagnostics",
                json.encodeToString((issues() + issue).takeLast(80)),
            )
            id
          }
          .getOrNull()

  fun issues(): List<Issue> =
      runCatching {
            json.decodeFromString<List<Issue>>(settings.getString("local_diagnostics", "[]"))
          }
          .getOrDefault(emptyList())

  @Serializable
  private data class Report(
      val schemaVersion: Int = 1,
      val appVersion: String = cn.edu.ubaa.plus.BuildKonfig.VERSION,
      val issues: List<Issue>,
  )

  fun export(): String = json.encodeToString(Report(issues = issues()))

  fun clear() {
    settings.remove("local_diagnostics")
  }
}
