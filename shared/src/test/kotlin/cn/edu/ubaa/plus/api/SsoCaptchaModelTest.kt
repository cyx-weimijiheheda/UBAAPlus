package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.local.SsoCaptchaModel
import cn.edu.ubaa.plus.api.local.decodeCaptcha
import cn.edu.ubaa.plus.api.local.loadCaptchaModelBytes
import java.util.zip.GZIPInputStream
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.serialization.json.Json

class SsoCaptchaModelTest {
  @Test
  fun modelLoaderSupportsAaptExpandedAssetsAndOriginalGzip() {
    val raw = "synthetic model bytes".toByteArray()
    val compressed =
        java.io
            .ByteArrayOutputStream()
            .also { output -> java.util.zip.GZIPOutputStream(output).use { it.write(raw) } }
            .toByteArray()
    assertContentEquals(
        raw,
        loadCaptchaModelBytes { name ->
          assertEquals("captcha/common.onnx", name)
          java.io.ByteArrayInputStream(raw)
        },
    )
    assertContentEquals(
        raw,
        loadCaptchaModelBytes { name ->
          when (name) {
            "captcha/common.onnx" -> throw java.io.FileNotFoundException(name)
            "captcha/common.onnx.gz" -> java.io.ByteArrayInputStream(compressed)
            else -> error("unexpected asset")
          }
        },
    )
  }

  @Test
  fun builtInModelReadsUpstreamSyntheticFixture() {
    val image = ImageIO.read(javaClass.getResourceAsStream("/captcha/captcha_synthetic.png"))
    val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    val bytes =
        GZIPInputStream(javaClass.getResourceAsStream("/captcha/common.onnx.gz")).use {
          it.readBytes()
        }
    val charset =
        Json.decodeFromString<List<String>>(
            javaClass.getResourceAsStream("/captcha/charset.json")!!.bufferedReader().readText()
        )
    SsoCaptchaModel(bytes, charset).use { model ->
      assertEquals("a2b3c4", model.recognize(image.width, image.height, pixels))
      listOf(0.8, 0.9, 1.1, 1.2).forEach { brightness ->
        val adjusted =
            pixels
                .map { argb ->
                  fun channel(shift: Int) =
                      (((argb ushr shift) and 255) * brightness).toInt().coerceIn(0, 255)
                  (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
                }
                .toIntArray()
        assertEquals("a2b3c4", model.recognize(image.width, image.height, adjusted))
      }
    }
  }

  @Test
  fun ctcBlankAndDuplicateRulesRejectNonSixCharacterResults() {
    val charset = listOf("", "a", "2", "b", "3", "c", "4")
    fun scores(vararg values: Int) =
        values.flatMap { v -> charset.indices.map { if (v == it) 1f else 0f } }.toFloatArray()
    assertEquals("a2b3c4", decodeCaptcha(scores(1, 1, 0, 2, 3, 0, 4, 5, 6), charset))
    assertNull(decodeCaptcha(scores(1, 1, 0, 2), charset))
  }
}
