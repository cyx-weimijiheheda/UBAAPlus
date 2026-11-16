package cn.edu.ubaa.plus.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.api.storage.ThemeMode
import cn.edu.ubaa.plus.api.storage.WidgetColorMode
import cn.edu.ubaa.plus.ui.theme.brandColor

internal data class WidgetPalette(
    val background: Int,
    val text: Int,
    val accent: Int,
    val cell: Int,
)

internal fun widgetPalette(context: Context): WidgetPalette {
  val prefs = AppPreferencesStore.state.value
  val dark =
      when (prefs.widgetThemeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM ->
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
      }
  val brand =
      if (prefs.widgetColorMode == WidgetColorMode.CUSTOM) Color(prefs.widgetCustomArgb)
      else prefs.themePalette.brandColor(prefs.customBrandArgb)
  if (prefs.widgetColorMode == WidgetColorMode.WALLPAPER && Build.VERSION.SDK_INT >= 31) {
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    return WidgetPalette(
        lerp(scheme.surface, scheme.primaryContainer, 0.40f).toArgb(),
        scheme.onSurface.toArgb(),
        scheme.primary.toArgb(),
        scheme.primaryContainer.toArgb(),
    )
  }
  val background =
      lerp(if (dark) Color(0xFF14171C) else Color.White, brand, if (dark) 0.18f else 0.10f)
  return WidgetPalette(
      background.toArgb(),
      (if (dark) Color(0xFFE8EDF5) else Color(0xFF18212D)).toArgb(),
      lerp(brand, if (dark) Color.White else Color.Black, if (dark) 0.60f else 0.12f).toArgb(),
      lerp(background, brand, if (dark) 0.22f else 0.15f).toArgb(),
  )
}
