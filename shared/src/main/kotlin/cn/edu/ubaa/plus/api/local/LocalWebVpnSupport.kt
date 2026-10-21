package cn.edu.ubaa.plus.api.local

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.platform.PlatformAesCfbNoPadding
import io.ktor.http.Url

internal object LocalWebVpnSupport {
  private const val gatewayHost = "d.buaa.edu.cn"
  private const val keyText = "wrdvpnisthebest!"
  private val keyBytes = keyText.encodeToByteArray()
  private val ivBytes = keyText.encodeToByteArray()

  fun toWebVpnUrl(url: String): String {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return url
    if (parsed.host.equals(gatewayHost, ignoreCase = true)) return url
    val protocolPart =
        when {
          parsed.specifiedPort == DEFAULT_HTTP_PORT && parsed.protocol.name == "http" -> "http"
          parsed.specifiedPort == DEFAULT_HTTPS_PORT && parsed.protocol.name == "https" -> "https"
          parsed.specifiedPort <= 0 -> parsed.protocol.name
          else -> "${parsed.protocol.name}-${parsed.specifiedPort}"
        }
    val encodedHost = encryptHost(parsed.host)
    val queryPart = queryPart(url)
    val fragmentPart = parsed.fragment.takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty()
    return "https://$gatewayHost/$protocolPart/$encodedHost${parsed.encodedPath}$queryPart$fragmentPart"
  }

  fun fromWebVpnUrl(url: String): String {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return url
    if (!parsed.host.equals(gatewayHost, ignoreCase = true)) return url
    val segments = parsed.encodedPath.split('/').filter { it.isNotBlank() }
    if (segments.size < 2) return url
    val protocolPart = segments[0]
    val encodedHost = segments[1]
    val (scheme, port) =
        protocolPart.split('-', limit = 2).let { parts ->
          parts.firstOrNull().orEmpty() to parts.getOrNull(1)?.toIntOrNull()
        }
    if (scheme.isBlank()) return url
    val host =
        runCatching { decryptHost(encodedHost) }
            .getOrElse {
              return url
            }
    val authority =
        when (port) {
          null -> "$scheme://$host"
          else -> "$scheme://$host:$port"
        }
    // 仅移除协议和加密主机前缀；重组非空片段会丢失 /web/ 的尾斜杠，使登录循环。
    val pathPart = parsed.encodedPath.split('/', limit = 4).getOrNull(3)?.let { "/$it" }.orEmpty()
    val queryPart = queryPart(url)
    val fragmentPart = parsed.fragment.takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty()
    return "$authority$pathPart$queryPart$fragmentPart"
  }

  fun isSsoUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return fromWebVpnUrl(url).contains("sso.buaa.edu.cn", ignoreCase = true)
  }

  /**
   * 修正上游 302 中的协议降级。
   *
   * `uc.buaa.edu.cn/api/login` 现在会 302 到 `http://sso.buaa.edu.cn/login?service=...`。 CAS 的 CASTGC 是
   * Secure cookie，HTTP 请求不会携带，CAS 会再次返回登录页， 导致 UC 会话始终建立不起来、登录后的会话校验失败。这里统一升级为 HTTPS； WEBVPN
   * 模式下还要把网关的 `/http/` 隧道改回 `/https/`，否则同样拿不到 SSO 会话。
   */
  fun normalizeRedirectUrl(url: String): String {
    val upgraded =
        if (url.startsWith("http://sso.buaa.edu.cn", ignoreCase = true)) {
          "https://" + url.substring("http://".length)
        } else {
          url
        }
    if (ConnectionRuntime.currentMode() != ConnectionMode.WEBVPN) return upgraded
    return when {
      upgraded.startsWith("https://$gatewayHost/http/", ignoreCase = true) ->
          upgraded.replaceFirst("/http/", "/https/")
      upgraded.startsWith("https://sso.buaa.edu.cn", ignoreCase = true) -> toWebVpnUrl(upgraded)
      else -> upgraded
    }
  }

  // 片段中的 ?type=...&token=... 不属于请求查询，不能在转换时复制到 # 之前。
  private fun queryPart(url: String): String =
      url.substringBefore('#')
          .substringAfter('?', "")
          .takeIf { it.isNotBlank() }
          ?.let { "?$it" }
          .orEmpty()

  private fun encryptHost(host: String): String {
    val plain = host.encodeToByteArray()
    val padded = plain + ByteArray((16 - plain.size % 16) % 16) { '0'.code.toByte() }
    val cipherText = PlatformAesCfbNoPadding.encrypt(padded, keyBytes, ivBytes)
    return ivBytes.toHex() + cipherText.toHex().take(plain.size * 2)
  }

  private fun decryptHost(encodedHost: String): String {
    require(encodedHost.length >= 32) { "Invalid WebVPN host payload" }
    val iv = encodedHost.substring(0, 32).hexToBytes()
    val cipherHex =
        encodedHost.substring(32).let { text -> text + "0".repeat((32 - text.length % 32) % 32) }
    val decrypted =
        PlatformAesCfbNoPadding.decrypt(cipherHex.hexToBytes(), keyBytes, iv)
            .copyOf(encodedHost.length / 2 - 16)
    return decrypted.decodeToString()
  }

  private fun ByteArray.toHex(): String =
      joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }

  private fun String.hexToBytes(): ByteArray =
      ByteArray(length / 2) { index ->
        ((this[index * 2].digitToInt(16) shl 4) or this[index * 2 + 1].digitToInt(16)).toByte()
      }

  private const val DEFAULT_HTTP_PORT = 80
  private const val DEFAULT_HTTPS_PORT = 443
}

internal fun localUpstreamUrl(url: String): String =
    when (ConnectionRuntime.currentMode()) {
      ConnectionMode.WEBVPN -> LocalWebVpnSupport.toWebVpnUrl(url)
      else -> url
    }

/** CGYY (cgyy.buaa.edu.cn) is publicly accessible without campus network, always use direct URL. */
internal fun localCgyyUpstreamUrl(url: String): String = url

internal fun localIsSsoUrl(url: String?): Boolean = LocalWebVpnSupport.isSsoUrl(url)
