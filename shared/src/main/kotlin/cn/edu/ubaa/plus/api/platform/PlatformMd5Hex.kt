package cn.edu.ubaa.plus.api.platform

import java.security.MessageDigest

internal object PlatformMd5Hex {
  fun digest(input: ByteArray): String =
      MessageDigest.getInstance("MD5").digest(input).joinToString("") { "%02x".format(it) }
}
