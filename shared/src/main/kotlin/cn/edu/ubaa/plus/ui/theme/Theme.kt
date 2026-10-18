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
import androidx.compose.ui.platform.LocalContext

/** 固定的品牌色，用于标题栏、底部导航等需要始终保持「北航蓝底白字」的区域。 */
val UbaaBrandBlue = Color(0xFF005BAC)

val UbaaOnBrandBlue = Color(0xFFFFFFFF)

/**
 * 品牌配色以北航蓝（#005BAC）为种子。
 *
 * 未使用 dynamic color 作为默认值：动态取色会用壁纸颜色覆盖品牌色，导致不同设备上 视觉不一致。需要时可通过 [UBAATheme] 的 `dynamicColor` 参数打开。
 */
private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF005BAC),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF001B3D),
        inversePrimary = Color(0xFFA9C7FF),
        secondary = Color(0xFF4A5C78),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD7E3F7),
        onSecondaryContainer = Color(0xFF0F1D2E),
        tertiary = Color(0xFF3D6373),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC1E8F7),
        onTertiaryContainer = Color(0xFF061E28),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFDDE3F0),
        onSurfaceVariant = Color(0xFF44474F),
        surfaceTint = Color(0xFF005BAC),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        outline = Color(0xFF737A87),
        outlineVariant = Color(0xFFC3C7D2),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceDim = Color(0xFFD9DCE3),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F9FF),
        surfaceContainer = Color(0xFFF1F4FB),
        surfaceContainerHigh = Color(0xFFEBEEF5),
        surfaceContainerHighest = Color(0xFFE5E9F0),
    )

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFA9C7FF),
        onPrimary = Color(0xFF003062),
        primaryContainer = Color(0xFF00468A),
        onPrimaryContainer = Color(0xFFD6E3FF),
        inversePrimary = Color(0xFF005BAC),
        secondary = Color(0xFFB3C7E3),
        onSecondary = Color(0xFF1C2B40),
        secondaryContainer = Color(0xFF33445C),
        onSecondaryContainer = Color(0xFFD7E3F7),
        tertiary = Color(0xFFA5CCDD),
        onTertiary = Color(0xFF0A3543),
        tertiaryContainer = Color(0xFF244C5B),
        onTertiaryContainer = Color(0xFFC1E8F7),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE3E2E6),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = Color(0xFF3F4754),
        onSurfaceVariant = Color(0xFFC4C6D0),
        surfaceTint = Color(0xFFA9C7FF),
        inverseSurface = Color(0xFFE3E2E6),
        inverseOnSurface = Color(0xFF2F3033),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474F),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF37393E),
        surfaceDim = Color(0xFF111318),
        surfaceContainerLowest = Color(0xFF0C0E13),
        surfaceContainerLow = Color(0xFF191C20),
        surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
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

private val LightExtendedColors =
    UbaaExtendedColors(
        success = Color(0xFF2E7D32),
        onSuccess = Color(0xFFFFFFFF),
        successContainer = Color(0xFFC8E6C9),
        onSuccessContainer = Color(0xFF0B3D0F),
        info = Color(0xFF005BAC),
    )

private val DarkExtendedColors =
    UbaaExtendedColors(
        success = Color(0xFF81C784),
        onSuccess = Color(0xFF0B3D0F),
        successContainer = Color(0xFF1B5E20),
        onSuccessContainer = Color(0xFFC8E6C9),
        info = Color(0xFF90CAF9),
    )

private val LocalUbaaExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** 语义色访问入口：`UbaaColors.current.success`。 */
object UbaaColors {
  val current: UbaaExtendedColors
    @Composable @ReadOnlyComposable get() = LocalUbaaExtendedColors.current
}

@Composable
fun UBAATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
  val colorScheme =
      when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
          val context = LocalContext.current
          if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
      }

  val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

  CompositionLocalProvider(LocalUbaaExtendedColors provides extendedColors) {
    MaterialTheme(colorScheme = colorScheme, typography = getAppTypography()) {
      Surface(modifier = Modifier.fillMaxSize(), color = colorScheme.background) { content() }
    }
  }
}
