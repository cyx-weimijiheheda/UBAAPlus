package cn.edu.ubaa.plus.api.auth

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.model.dto.LoginStatsConnectionMode
import cn.edu.ubaa.plus.model.dto.LoginStatsReportRequest
import cn.edu.ubaa.plus.model.dto.LoginStatsSuccessMode
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal object LoginStatsReporter {
  internal var reporter: suspend (LoginStatsReportRequest) -> Unit = defaultReporter()

  /**
   * 上报统计不能阻塞登录。
   *
   * 统计服务不可达时连接超时会让登录界面多等数秒；默认放到后台协程，失败静默忽略。 测试可把 [reportSynchronously] 置为 true 并注入 reporter，继续同步断言。
   */
  internal var reportSynchronously: Boolean = false

  internal var reportScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  suspend fun reportSuccess(
      username: String,
      successMode: LoginStatsSuccessMode,
      connectionMode: ConnectionMode,
  ) {
    if (username.isBlank()) {
      return
    }
    val request =
        LoginStatsReportRequest(
            username = username,
            successMode = successMode,
            connectionMode = connectionMode.toLoginStatsConnectionMode(),
        )
    val task: suspend () -> Unit = {
      runCatching { withTimeout(STATS_TIMEOUT_MILLIS) { reporter(request) } }
    }
    if (reportSynchronously) {
      task()
    } else {
      reportScope.launch { task() }
    }
  }

  fun reset() {
    reporter = defaultReporter()
    reportSynchronously = false
    reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  }

  private fun defaultReporter(): suspend (LoginStatsReportRequest) -> Unit = { request ->
    ApiClientProvider.shared.getClient().post("api/v1/auth/login-stats") {
      contentType(ContentType.Application.Json)
      setBody(request)
    }
  }

  private const val STATS_TIMEOUT_MILLIS = 3_000L
}

private fun ConnectionMode.toLoginStatsConnectionMode(): LoginStatsConnectionMode =
    when (this) {
      ConnectionMode.DIRECT -> LoginStatsConnectionMode.DIRECT
      ConnectionMode.WEBVPN -> LoginStatsConnectionMode.WEBVPN
    }
