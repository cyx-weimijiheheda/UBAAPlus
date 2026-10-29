package cn.edu.ubaa.plus.ui.screens.libbook

import java.net.URLDecoder

/**
 * 解析座位二维码。
 *
 * 官方 H5 直接把二维码 URL 的 query 原样交给 `/v4/space/qr_scan`。这里支持三种内容：
 * 1. 完整 URL（取 `?` 之后、`#` 之前）；
 * 2. 已经是 `area_id=...&seat_id=...` 的 query 串；
 * 3. 纯 token（包装成 `query=...`）。
 */
internal fun parseLibBookQrParams(rawContent: String): Map<String, String> {
  val text = rawContent.trim()
  if (text.isEmpty()) return emptyMap()
  val queryPart =
      when {
        '?' in text -> text.substringAfter('?').substringBefore('#')
        '=' in text -> text.substringBefore('#')
        else -> "query=${encode(text)}"
      }
  if (queryPart.isBlank()) return emptyMap()
  return queryPart
      .split('&')
      .mapNotNull { pair ->
        val key = decode(pair.substringBefore('=', "")).trim()
        if (key.isBlank()) return@mapNotNull null
        key to decode(pair.substringAfter('=', "")).trim()
      }
      .toMap()
}

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8) }.getOrDefault(value)

private fun encode(value: String): String =
    runCatching { java.net.URLEncoder.encode(value, Charsets.UTF_8) }.getOrDefault(value)
