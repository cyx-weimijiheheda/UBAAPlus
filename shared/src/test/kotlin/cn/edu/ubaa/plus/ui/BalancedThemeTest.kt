package cn.edu.ubaa.plus.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cn.edu.ubaa.plus.ui.theme.balancedSurfaces
import kotlin.test.*

class BalancedThemeTest {
  private fun contrast(a: Color, b: Color): Float {
    val l = listOf(a.luminance(), b.luminance())
    return (l.max() + 0.05f) / (l.min() + 0.05f)
  }

  @Test
  fun cardsRemainReadableAndDistinctInBothModesAcrossPaletteExtremes() {
    for (brand in
        listOf(Color(0xFF005BAC), Color(0xFF006B3C), Color(0xFFC56E8B), Color.Black, Color.White)) {
      for (dark in listOf(true, false)) {
        val theme =
            balancedSurfaces(if (dark) darkColorScheme() else lightColorScheme(), brand, dark)
        for (surface in
            listOf(theme.background, theme.surfaceContainerLow, theme.surfaceContainerHigh)) {
          assertTrue(contrast(theme.onSurface, surface) >= 4.5f)
          assertTrue(contrast(theme.onSurfaceVariant, surface) >= 4.5f)
        }
        assertTrue(
            kotlin.math.abs(theme.background.luminance() - theme.surfaceContainerLow.luminance()) >
                0.01f
        )
        if (!dark) assertTrue(theme.background.luminance() < 0.9f)
        else assertTrue(theme.surfaceContainerLow.luminance() > theme.background.luminance())
      }
    }
  }
}
