package cn.edu.ubaa.plus.api.platform

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object PlatformAesEcbPkcs5Padding {
  fun encrypt(input: ByteArray, key: ByteArray): ByteArray =
      runCipher(Cipher.ENCRYPT_MODE, input, key)

  fun decrypt(input: ByteArray, key: ByteArray): ByteArray =
      runCipher(Cipher.DECRYPT_MODE, input, key)

  private fun runCipher(mode: Int, input: ByteArray, key: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
    cipher.init(mode, SecretKeySpec(key, "AES"))
    return cipher.doFinal(input)
  }
}
