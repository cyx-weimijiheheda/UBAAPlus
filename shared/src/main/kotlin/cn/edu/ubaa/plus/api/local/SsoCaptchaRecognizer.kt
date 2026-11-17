package cn.edu.ubaa.plus.api.local

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.BitmapFactory
import java.nio.FloatBuffer
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** UBAA 6c4dba8d 的本地 ddddocr 模型；只接受六位字母数字，识别失败交回人工输入。 */
object SsoCaptchaRecognizer {
  @Volatile private var context: Context? = null
  private val model by lazy {
    context?.assets?.let { assets ->
      GZIPInputStream(assets.open("captcha/common.onnx.gz")).use { input ->
        SsoCaptchaModel(
            input.readBytes(),
            Json.decodeFromString(
                assets.open("captcha/charset.json").bufferedReader().use { it.readText() }
            ),
        )
      }
    }
  }

  fun initialize(context: Context) {
    this.context = context.applicationContext
  }

  internal suspend fun recognize(encoded: String): String? =
      withContext(Dispatchers.Default) {
        try {
          val bytes = Base64.getDecoder().decode(encoded.substringAfter(','))
          if (bytes.isEmpty() || bytes.size > 512 * 1024) return@withContext null
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
          if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024) return@withContext null
          val bitmap =
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
          try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            model?.recognize(bitmap.width, bitmap.height, pixels)
          } finally {
            bitmap.recycle()
          }
        } catch (_: Exception) {
          null
        } catch (_: LinkageError) {
          null
        }
      }
}

internal class SsoCaptchaModel(bytes: ByteArray, private val charset: List<String>) :
    AutoCloseable {
  private val environment = OrtEnvironment.getEnvironment()
  private val session =
      OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(1)
        environment.createSession(bytes, options)
      }

  fun recognize(width: Int, height: Int, pixels: IntArray): String? {
    if (
        width !in 1..1024 ||
            height !in 1..1024 ||
            pixels.size != width * height ||
            charset.size != 8210
    )
        return null
    val values = captchaInput(width, height, pixels)
    OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), longArrayOf(1, 1, 64, 170))
        .use { tensor ->
          session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
            val output = result[0] as? OnnxTensor ?: return null
            if (output.info.shape.lastOrNull() != charset.size.toLong()) return null
            val buffer = output.floatBuffer
            val scores = FloatArray(buffer.remaining())
            buffer.get(scores)
            return decodeCaptcha(scores, charset)
          }
        }
  }

  override fun close() {
    session.close()
  }
}

/** 五像素白边、固定 170×64 灰度输入。 */
internal fun captchaInput(width: Int, height: Int, pixels: IntArray): FloatArray {
  fun luminance(x: Int, y: Int): Float {
    if (x !in 5 until width + 5) return 255f
    val argb = pixels[y.coerceIn(0, height - 1) * width + x - 5]
    return ((argb ushr 16 and 255) * 0.2126f +
        (argb ushr 8 and 255) * 0.7152f +
        (argb and 255) * 0.0722f)
  }
  return FloatArray(170 * 64) { index ->
    val x = ((index % 170 + 0.5f) * (width + 10) / 170f - 0.5f).coerceIn(0f, width + 9f)
    val y = ((index / 170 + 0.5f) * height / 64f - 0.5f).coerceIn(0f, height - 1f)
    val ix = x.toInt()
    val iy = y.toInt()
    val dx = x - ix
    val dy = y - iy
    ((luminance(ix, iy) * (1 - dx) + luminance(ix + 1, iy) * dx) * (1 - dy) +
        (luminance(ix, iy + 1) * (1 - dx) + luminance(ix + 1, iy + 1) * dx) * dy) / 255f
  }
}

internal fun decodeCaptcha(scores: FloatArray, charset: List<String>): String? {
  if (charset.isEmpty() || scores.size % charset.size != 0) return null
  var previous = -1
  val text =
      buildString {
            for (offset in scores.indices step charset.size) {
              var best = 0
              for (i in 1 until charset.size) if (scores[offset + i] > scores[offset + best])
                  best = i
              if (best != previous && best != 0) append(charset[best])
              previous = best
            }
          }
          .lowercase()
  return text.takeIf { it.length == 6 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } }
}
