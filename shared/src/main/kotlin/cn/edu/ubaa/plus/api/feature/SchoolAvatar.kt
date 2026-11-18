@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.LocalPrivateRead
import cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider
import cn.edu.ubaa.plus.api.local.LocalWebVpnSupport
import cn.edu.ubaa.plus.api.local.localUpstreamUrl
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable

/** Only image bytes are cached, not a private image URL or the rest of the identity response. */
@Serializable data class SchoolAvatarDto(val owner: String, val encodedImage: String? = null)

internal fun schoolAvatarUrl(source: String): String {
  val uri =
      runCatching { URI("https://it.buaa.edu.cn/").resolve(source) }.getOrNull()
          ?: throw ApiCallException("头像地址格式异常", code = "avatar_url")
  if (
      uri.host != "it.buaa.edu.cn" ||
          uri.scheme != "https" ||
          uri.port !in setOf(-1, 443) ||
          uri.rawUserInfo != null
  )
      throw ApiCallException("学校头像来源不受支持", code = "avatar_url")
  return uri.toString()
}

internal suspend fun fetchSchoolAvatar(owner: String, source: String): SchoolAvatarDto {
  var target = schoolAvatarUrl(source)
  LocalUpstreamClientProvider.newNoRedirectClient().use { client ->
    repeat(4) {
      val response =
          client.get(localUpstreamUrl(target)) {
            attributes.put(LocalPrivateRead, true)
            header(HttpHeaders.Accept, "image/png,image/jpeg,image/webp")
            header(HttpHeaders.Referrer, localUpstreamUrl("https://it.buaa.edu.cn/home/"))
          }
      if (response.status.value in setOf(301, 302, 303, 307, 308)) {
        val next = response.headers[HttpHeaders.Location] ?: throw ApiCallException("头像跳转异常")
        target =
            schoolAvatarUrl(
                LocalWebVpnSupport.fromWebVpnUrl(
                    URI(localUpstreamUrl(target)).resolve(next).toString()
                )
            )
      } else {
        val mime =
            response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
        if (!response.status.isSuccess() || mime !in setOf("image/png", "image/jpeg", "image/webp"))
            throw ApiCallException("学校头像暂不可用", code = "avatar_response")
        val output = ByteArrayOutputStream()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(8192)
        while (true) {
          val count = channel.readAvailable(buffer, 0, buffer.size)
          if (count < 0) break
          if (output.size() + count > 1024 * 1024)
              throw ApiCallException("学校头像文件过大", code = "avatar_size")
          output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        if (!validAvatarSignature(bytes, mime))
            throw ApiCallException("学校头像格式异常", code = "avatar_format")
        return SchoolAvatarDto(owner, Base64.encode(bytes))
      }
    }
  }
  throw ApiCallException("头像跳转次数过多", code = "avatar_redirect")
}

internal fun validAvatarSignature(bytes: ByteArray, mime: String?): Boolean =
    when (mime) {
      "image/png" ->
          bytes.size >= 24 &&
              bytes
                  .take(8)
                  .toByteArray()
                  .contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
      "image/jpeg" ->
          bytes.size >= 4 &&
              bytes[0] == (-1).toByte() &&
              bytes[1] == (-40).toByte() &&
              bytes[bytes.size - 2] == (-1).toByte() &&
              bytes.last() == (-39).toByte()
      "image/webp" ->
          bytes.size >= 16 &&
              String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
              String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
      else -> false
    }
