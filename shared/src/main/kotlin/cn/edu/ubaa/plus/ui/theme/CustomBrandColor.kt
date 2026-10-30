package cn.edu.ubaa.plus.ui.theme

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 自定义主题色的可读性约束：亮度/饱和度过低或过高都会导致对比度问题。 */
private const val MIN_SATURATION = 0.15f
private const val MAX_SATURATION = 0.92f
private const val MIN_VALUE = 0.28f
private const val MAX_VALUE = 0.88f

fun normalizeCustomBrandArgb(argb: Int): Int {
  val (h, s, v) = rgbToHsv(argb)
  val safeS = s.coerceIn(MIN_SATURATION, MAX_SATURATION)
  val safeV = v.coerceIn(MIN_VALUE, MAX_VALUE)
  return (0xFF shl 24) or hsvToRgb(h, safeS, safeV)
}

fun customBrandArgbToHex(argb: Int): String {
  val red = (argb shr 16) and 0xFF
  val green = (argb shr 8) and 0xFF
  val blue = argb and 0xFF
  return "#%02X%02X%02X".format(red, green, blue)
}

fun parseCustomBrandHex(input: String): Int? {
  val text = input.trim().removePrefix("#")
  if (text.length != 6 && text.length != 8) return null
  if (text.any { it !in "0123456789abcdefABCDEF" }) return null
  val parsed = text.toLong(16)
  return if (text.length == 6) {
    (0xFF shl 24) or parsed.toInt()
  } else {
    parsed.toInt()
  }
}

fun customBrandArgbHsv(argb: Int): FloatArray {
  val (hue, saturation, value) = rgbToHsv(argb)
  return floatArrayOf(hue, saturation, value)
}

fun customBrandArgbFromHsv(hue: Float, saturation: Float, value: Float): Int =
    (0xFF shl 24) or hsvToRgb(hue, saturation, value)

private fun rgbToHsv(argb: Int): Triple<Float, Float, Float> {
  val r = ((argb shr 16) and 0xFF) / 255f
  val g = ((argb shr 8) and 0xFF) / 255f
  val b = (argb and 0xFF) / 255f
  val maxValue = max(r, max(g, b))
  val minValue = min(r, min(g, b))
  val delta = maxValue - minValue

  val hue =
      when {
        delta < 0.00001f -> 0f
        maxValue == r -> (60f * (((g - b) / delta) % 6f) + 360f) % 360f
        maxValue == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
      }
  val saturation = if (maxValue <= 0.00001f) 0f else delta / maxValue
  return Triple(hue, saturation, maxValue)
}

private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int {
  val h = ((hue % 360f) + 360f) % 360f
  val c = value * saturation
  val x = c * (1f - abs((h / 60f) % 2f - 1f))
  val m = value - c
  val (r1, g1, b1) =
      when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
      }
  val red = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
  val green = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
  val blue = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
  return (red shl 16) or (green shl 8) or blue
}
