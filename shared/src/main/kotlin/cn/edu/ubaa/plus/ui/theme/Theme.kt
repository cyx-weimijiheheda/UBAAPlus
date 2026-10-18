package cn.edu.ubaa.plus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import cn.edu.ubaa.plus.api.storage.ThemePalette

/** 默认品牌色（北航蓝），也用于 launcher/预览的兜底。 */
val UbaaBrandBlue = Color(0xFF005BAC)

val UbaaOnBrandBlue = Color(0xFFFFFFFF)

/** 当前主题的顶栏/底栏品牌色。 */
@Immutable data class UbaaBrandColors(val brand: Color, val onBrand: Color)

private val LocalUbaaBrandColors = staticCompositionLocalOf {
  UbaaBrandColors(UbaaBrandBlue, UbaaOnBrandBlue)
}

object UbaaBrand {
  val current: UbaaBrandColors
    @Composable @ReadOnlyComposable get() = LocalUbaaBrandColors.current
}

/** 配色的主题种子色，用于派生 Material 配色；需要保证文字对比度。 */
fun ThemePalette.brandColor(): Color =
    when (this) {
      ThemePalette.BUAA_BLUE -> UbaaBrandBlue
      ThemePalette.PKU_RED -> Color(0xFF7B0A0A)
      ThemePalette.THU_PURPLE -> Color(0xFF660874)
      ThemePalette.BJFU_GREEN -> Color(0xFF006B3C)
      ThemePalette.ROOMMATE_PINK -> Color(0xFFC56E8B)
    }

/** 设置页色块使用的展示色；粉色比主题种子更淡，避免过于刺眼。 */
fun ThemePalette.swatchColor(): Color =
    when (this) {
      ThemePalette.ROOMMATE_PINK -> Color(0xFFF0B6C6)
      else -> brandColor()
    }

private fun onColorFor(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF111318) else Color.White

private fun lightSchemeFor(brand: Color) =
    lightColorScheme(
        primary = brand,
        onPrimary = onColorFor(brand),
        primaryContainer = lerp(Color.White, brand, 0.18f),
        onPrimaryContainer = lerp(Color.Black, brand, 0.55f),
        inversePrimary = lerp(Color.White, brand, 0.55f),
        secondary = lerp(Color(0xFF4A5C78), brand, 0.25f),
        onSecondary = Color.White,
        secondaryContainer = lerp(Color.White, brand, 0.12f),
        onSecondaryContainer = lerp(Color.Black, brand, 0.55f),
        tertiary = lerp(Color(0xFF3D6373), brand, 0.25f),
        onTertiary = Color.White,
        tertiaryContainer = lerp(Color.White, brand, 0.12f),
        onTertiaryContainer = lerp(Color.Black, brand, 0.55f),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = lerp(Color(0xFFF9F9FF), brand, 0.015f),
        onBackground = Color(0xFF1A1C1E),
        surface = lerp(Color(0xFFF9F9FF), brand, 0.015f),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = lerp(Color(0xFFDDE3F0), brand, 0.04f),
        onSurfaceVariant = Color(0xFF44474F),
        surfaceTint = brand,
        inverseSurface = lerp(Color(0xFF2F3033), brand, 0.05f),
        inverseOnSurface = Color(0xFFF1F0F4),
        outline = Color(0xFF737A87),
        outlineVariant = Color(0xFFC3C7D2),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceDim = lerp(Color(0xFFD9DCE3), brand, 0.02f),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = lerp(Color(0xFFF7F9FF), brand, 0.02f),
        surfaceContainer = lerp(Color(0xFFF1F4FB), brand, 0.025f),
        surfaceContainerHigh = lerp(Color(0xFFEBEEF5), brand, 0.03f),
        surfaceContainerHighest = lerp(Color(0xFFE5E9F0), brand, 0.035f),
    )

private fun darkSchemeFor(brand: Color) =
    darkColorScheme(
        primary = lerp(Color.White, brand, 0.55f),
        onPrimary = lerp(Color.Black, brand, 0.7f),
        primaryContainer = lerp(Color.Black, brand, 0.65f),
        onPrimaryContainer = lerp(Color.White, brand, 0.2f),
        inversePrimary = brand,
        secondary = lerp(Color(0xFFB3C7E3), brand, 0.2f),
        onSecondary = Color(0xFF1C2B40),
        secondaryContainer = lerp(Color(0xFF33445C), brand, 0.15f),
        onSecondaryContainer = Color(0xFFD7E3F7),
        tertiary = lerp(Color(0xFFA5CCDD), brand, 0.2f),
        onTertiary = Color(0xFF0A3543),
        tertiaryContainer = lerp(Color(0xFF244C5B), brand, 0.15f),
        onTertiaryContainer = Color(0xFFC1E8F7),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = lerp(Color(0xFF111318), brand, 0.03f),
        onBackground = Color(0xFFE3E2E6),
        surface = lerp(Color(0xFF111318), brand, 0.03f),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = lerp(Color(0xFF3F4754), brand, 0.04f),
        onSurfaceVariant = Color(0xFFC4C6D0),
        surfaceTint = lerp(Color.White, brand, 0.55f),
        inverseSurface = Color(0xFFE3E2E6),
        inverseOnSurface = Color(0xFF2F3033),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474F),
        scrim = Color(0xFF000000),
        surfaceBright = lerp(Color(0xFF37393E), brand, 0.04f),
        surfaceDim = lerp(Color(0xFF111318), brand, 0.03f),
        surfaceContainerLowest = lerp(Color(0xFF0C0E13), brand, 0.03f),
        surfaceContainerLow = lerp(Color(0xFF191C20), brand, 0.03f),
        surfaceContainer = lerp(Color(0xFF1D2024), brand, 0.035f),
        surfaceContainerHigh = lerp(Color(0xFF282A2F), brand, 0.04f),
        surfaceContainerHighest = lerp(Color(0xFF33353A), brand, 0.045f),
    )

/** M3 没有「成功」「信息」这类语义角色，但业务界面需要（例如评教完成、空闲教室、预约状态）。 用 CompositionLocal 扩展，避免各处继续写死颜色。 */
@Immutable
data class UbaaExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val info: Color,
)

private fun lightExtendedColors(brand: Color) =
    UbaaExtendedColors(
        success = Color(0xFF2E7D32),
        onSuccess = Color(0xFFFFFFFF),
        successContainer = Color(0xFFC8E6C9),
        onSuccessContainer = Color(0xFF0B3D0F),
        info = brand,
    )

private fun darkExtendedColors(brand: Color) =
    UbaaExtendedColors(
        success = Color(0xFF81C784),
        onSuccess = Color(0xFF0B3D0F),
        successContainer = Color(0xFF1B5E20),
        onSuccessContainer = Color(0xFFC8E6C9),
        info = lerp(Color.White, brand, 0.55f),
    )

private val LocalUbaaExtendedColors = staticCompositionLocalOf {
  lightExtendedColors(UbaaBrandBlue)
}

/** 语义色访问入口：`UbaaColors.current.success`。 */
object UbaaColors {
  val current: UbaaExtendedColors
    @Composable @ReadOnlyComposable get() = LocalUbaaExtendedColors.current
}

@Composable
fun UBAATheme(
    palette: ThemePalette = ThemePalette.BUAA_BLUE,
    followWallpaper: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
  val useDynamicColor =
      (followWallpaper || dynamicColor) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val context = LocalContext.current
  val colorScheme =
      when {
        useDynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkSchemeFor(palette.brandColor())
        else -> lightSchemeFor(palette.brandColor())
      }

  val brand = if (useDynamicColor) colorScheme.primary else palette.brandColor()
  val onBrand = onColorFor(brand)
  val extendedColors = if (darkTheme) darkExtendedColors(brand) else lightExtendedColors(brand)

  CompositionLocalProvider(
      LocalUbaaExtendedColors provides extendedColors,
      LocalUbaaBrandColors provides UbaaBrandColors(brand, onBrand),
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = getAppTypography()) {
      Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) { content() }
    }
  }
}
