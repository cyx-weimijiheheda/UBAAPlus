package cn.edu.ubaa.plus.api.platform

import android.graphics.BitmapFactory

internal data class LocalCgyyImageData(
    val width: Int,
    val height: Int,
    val argb: IntArray,
)

internal object PlatformImageRasterDecoder {
  fun decode(input: ByteArray): LocalCgyyImageData {
    val bitmap =
        BitmapFactory.decodeByteArray(input, 0, input.size)
            ?: error("Failed to decode CGYY captcha image")
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return LocalCgyyImageData(
        width = bitmap.width,
        height = bitmap.height,
        argb = pixels,
    )
  }
}
