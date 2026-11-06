package cn.edu.ubaa.plus.api

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AUTO 模式的传输探测。
 *
 * 校园网可达即直连；否则 WebVPN 网关可达则走 WebVPN；都不通则保留上次传输或直连。 探测只用于连接模式选择，不参与业务请求重试。
 */
internal object AutoTransportProbe {
  internal const val CAMPUS_PROBE_URL = "https://gw.buaa.edu.cn/cgi-bin/rad_user_info"
  internal const val WEBVPN_PROBE_URL = "https://d.buaa.edu.cn/"

  internal var reachability: suspend (String) -> Boolean = ::defaultReachability

  suspend fun resolve(previous: ConnectionMode?): ConnectionMode {
    if (reachability(CAMPUS_PROBE_URL)) return ConnectionMode.DIRECT
    if (reachability(WEBVPN_PROBE_URL)) return ConnectionMode.WEBVPN
    return previous?.takeIf { it != ConnectionMode.AUTO } ?: ConnectionMode.DIRECT
  }

  private suspend fun defaultReachability(url: String): Boolean =
      withContext(Dispatchers.IO) {
        runCatching {
              val connection = URL(url).openConnection() as HttpURLConnection
              connection.connectTimeout = 3_000
              connection.readTimeout = 3_000
              connection.requestMethod = "GET"
              connection.instanceFollowRedirects = false
              try {
                connection.responseCode in 200..499
              } finally {
                connection.disconnect()
              }
            }
            .getOrDefault(false)
      }
}
