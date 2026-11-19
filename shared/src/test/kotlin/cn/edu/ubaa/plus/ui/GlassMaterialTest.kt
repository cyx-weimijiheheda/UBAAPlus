package cn.edu.ubaa.plus.ui

import androidx.compose.ui.graphics.Color
import cn.edu.ubaa.plus.api.storage.GlassMaterial
import cn.edu.ubaa.plus.ui.theme.glassMaterialColor
import kotlin.test.*

class GlassMaterialTest {
  @Test
  fun `中性参数保留表面颜色和透明度`() {
    val color = Color(0.2f, 0.4f, 0.7f, 0.8f)
    val result = glassMaterialColor(color, GlassMaterial())
    assertEquals(color.red, result.red, 0.001f)
    assertEquals(color.green, result.green, 0.001f)
    assertEquals(color.blue, result.blue, 0.001f)
    assertEquals(color.alpha, result.alpha)
  }

  @Test
  fun `各参数实际改变色彩且极值不产生非法色值`() {
    val color = Color(0.2f, 0.4f, 0.7f)
    val gray = glassMaterialColor(color, GlassMaterial(saturation = 0))
    assertEquals(gray.red, gray.green, 0.001f)
    assertEquals(gray.green, gray.blue, 0.001f)
    assertTrue(glassMaterialColor(color, GlassMaterial(brightness = 100)).red > color.red)
    assertTrue(glassMaterialColor(color, GlassMaterial(brightness = 0)).red < color.red)
    assertTrue(glassMaterialColor(color, GlassMaterial(contrast = 100)).red < color.red)
    for (c in listOf(-100, 0, 50, 100, 200)) {
      val result = glassMaterialColor(color, GlassMaterial(c, c, c, c, c))
      assertTrue(
          listOf(result.red, result.green, result.blue).all { it.isFinite() && it in 0f..1f }
      )
    }
  }
}
