package cn.edu.ubaa.plus.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CustomBrandColorTest {
  @Test
  fun `normalize forces opaque and clamps readability`() {
    val transparent = normalizeCustomBrandArgb(0x00336699)
    assertEquals(0xFF, (transparent ushr 24) and 0xFF)

    val nearBlack = normalizeCustomBrandArgb(0xFF050505.toInt())
    val hsv = customBrandArgbHsv(nearBlack)
    assertEquals(0.28f, hsv[2], absoluteTolerance = 0.02f)
  }

  @Test
  fun `hex round trip`() {
    val argb = 0xFF336699.toInt()
    assertEquals("#336699", customBrandArgbToHex(argb))
    assertEquals(argb, parseCustomBrandHex("#336699"))
    assertEquals(argb, parseCustomBrandHex("336699"))
    assertEquals(argb, parseCustomBrandHex("#FF336699"))
  }

  @Test
  fun `invalid hex returns null`() {
    assertNull(parseCustomBrandHex("#12345"))
    assertNull(parseCustomBrandHex("zzzzzz"))
    assertNotNull(parseCustomBrandHex("#005BAC"))
  }

  @Test
  fun `hsv conversion keeps a readable seed`() {
    val argb = customBrandArgbFromHsv(210f, 0.8f, 0.7f)
    val normalized = normalizeCustomBrandArgb(argb)
    val hsv = customBrandArgbHsv(normalized)
    assertEquals(210f, hsv[0], absoluteTolerance = 1f)
    assertEquals(0.8f, hsv[1], absoluteTolerance = 0.02f)
    assertEquals(0.7f, hsv[2], absoluteTolerance = 0.02f)
  }
}
