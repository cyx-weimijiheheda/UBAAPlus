package cn.edu.ubaa.plus.api.venue

import cn.edu.ubaa.plus.api.platform.PlatformAesCbcNoPadding
import cn.edu.ubaa.plus.api.platform.PlatformAesEcbPkcs5Padding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.roundToInt

/**
 * 运动场地系统的加解密。
 * - 点击文字验证码：坐标先归一到 310×155，再用服务端下发的 secretKey 做 AES-ECB/PKCS7 + Base64。
 * - 下单指纹 orderPin：固定坐标字符串做 AES-CBC/PKCS7，输出小写 hex。
 *
 * 这些常量与算法来自场地系统前端，和 Python 版 BUAAVenueSelector 完全一致。
 */
internal object VenueCrypto {
  const val CANVAS_WIDTH = 310
  const val CANVAS_HEIGHT = 155

  private const val PIN_KEY = "c1h2i5n6g2o2k4a7"
  private const val PIN_IV = "C2H3I4N5G2O3K1E4"
  private const val DEFAULT_PIN_PLAIN = "640,720"

  /** 把原始图片上的点击坐标换算到验证码画布坐标系。 */
  fun normalizePoints(
      points: List<VenuePoint>,
      imageWidth: Int,
      imageHeight: Int,
  ): List<VenuePoint> {
    if (imageWidth <= 0 || imageHeight <= 0) return points
    return points.map { point ->
      VenuePoint(
          x = (CANVAS_WIDTH * point.x / imageWidth).roundToInt().toFloat(),
          y = (CANVAS_HEIGHT * point.y / imageHeight).roundToInt().toFloat(),
      )
    }
  }

  /** 与服务端一致的紧凑 JSON（无空格，按点击顺序）。 */
  fun pointJson(points: List<VenuePoint>): String =
      points.joinToString(separator = ",", prefix = "[", postfix = "]") {
        "{\"x\":${it.x.roundToInt()},\"y\":${it.y.roundToInt()}}"
      }

  @OptIn(ExperimentalEncodingApi::class)
  fun encryptEcbBase64(plainText: String, secretKey: String): String {
    val key = secretKey.encodeToByteArray()
    require(key.isNotEmpty()) { "验证码密钥为空" }
    return Base64.encode(PlatformAesEcbPkcs5Padding.encrypt(plainText.encodeToByteArray(), key))
  }

  /** 生成验证码校验所需的 pointJson 与 captchaVerification。 */
  fun captchaPayload(
      challenge: VenueCaptchaChallenge,
      points: List<VenuePoint>,
      imageWidth: Int,
      imageHeight: Int,
  ): VenueCaptchaPayload {
    val normalized = normalizePoints(points, imageWidth, imageHeight)
    val json = pointJson(normalized)
    return VenueCaptchaPayload(
        pointJson = encryptEcbBase64(json, challenge.secretKey),
        verification = encryptEcbBase64("${challenge.token}---$json", challenge.secretKey),
    )
  }

  /** 下单接口要求携带的 orderPin。 */
  fun orderPin(plain: String = DEFAULT_PIN_PLAIN): String {
    val padded = plain.encodeToByteArray().pkcs7Pad()
    val encrypted =
        PlatformAesCbcNoPadding.encrypt(
            padded,
            PIN_KEY.encodeToByteArray(),
            PIN_IV.encodeToByteArray(),
        )
    return encrypted.toHexString()
  }

  /** reservationOrderJson 的紧凑 JSON：ID 必须是字符串。 */
  fun selectionJson(selections: List<VenueSelection>): String =
      selections.joinToString(separator = ",", prefix = "[", postfix = "]") { selection ->
        buildString {
          append("{\"spaceId\":\"")
          append(selection.spaceId)
          append("\",\"timeId\":\"")
          append(selection.timeId)
          append('"')
          selection.groupId?.let {
            append(",\"venueSpaceGroupId\":")
            append(it)
          }
          append('}')
        }
      }

  private fun ByteArray.pkcs7Pad(blockSize: Int = 16): ByteArray {
    val padLength = blockSize - (size % blockSize)
    return this + ByteArray(padLength) { padLength.toByte() }
  }

  private fun ByteArray.toHexString(): String {
    val digits = "0123456789abcdef"
    return buildString(size * 2) {
      for (byte in this@toHexString) {
        val value = byte.toInt() and 0xFF
        append(digits[value ushr 4])
        append(digits[value and 0x0F])
      }
    }
  }
}

internal data class VenueCaptchaPayload(val pointJson: String, val verification: String)
