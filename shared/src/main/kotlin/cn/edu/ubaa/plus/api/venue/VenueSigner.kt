package cn.edu.ubaa.plus.api.venue

import cn.edu.ubaa.plus.api.platform.PlatformMd5Hex

/**
 * 运动场地接口签名。
 *
 * 规则：`salt + path + 按 key 排序的标量 key+value + timestamp + " " + salt`，取小写 MD5。 字符串参数会先
 * trim，空串与对象/数组不参与签名；GET 请求额外加入 `nocache`。
 */
internal class VenueSigner(
    private val salt: String = SIGN_SALT,
    val appKey: String = APP_KEY,
) {
  fun sign(path: String, params: Map<String, Any?>, timestamp: Long): String {
    val normalizedPath = if (path.startsWith("/")) path else "/$path"
    val raw = buildString {
      append(salt)
      append(normalizedPath)
      params.keys.sorted().forEach { key ->
        val value = scalar(value = params[key]) ?: return@forEach
        append(key)
        append(value)
      }
      append(timestamp)
      append(' ')
      append(salt)
    }
    return PlatformMd5Hex.digest(raw.encodeToByteArray())
  }

  /** GET 请求必须把 nocache 同时放进签名与实际参数。 */
  fun addNoCache(params: Map<String, Any?>, timestamp: Long): Map<String, Any?> =
      if ("nocache" in params) params else params + ("nocache" to timestamp)

  private fun scalar(value: Any?): String? =
      when (value) {
        null -> null
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is Iterable<*> -> null
        is Array<*> -> null
        is Map<*, *> -> null
        else -> value.toString()
      }

  companion object {
    const val APP_KEY = "8fceb735082b5a529312040b58ea780b"
    const val SIGN_SALT = "c640ca392cd45fb3a55b00a63a86c618"
  }
}
