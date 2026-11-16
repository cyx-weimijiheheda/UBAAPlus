package cn.edu.ubaa.plus.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** 液态玻璃效果开关；默认开启，设置页可关闭。 */
val LocalUbaaGlassEnabled = staticCompositionLocalOf { true }

object UbaaGlass {
  val enabled: Boolean
    @Composable @ReadOnlyComposable get() = LocalUbaaGlassEnabled.current
}

/**
 * 主题色磨砂表面。
 *
 * 不做真实背景模糊：Compose 的 blur 只能模糊自身内容，且 API 31 以下无效。 这里用低透明度渐变 + 细边框模拟柔和的玻璃质感，避免刺眼。
 */
@Composable
fun Modifier.ubaaGlassSurface(
    shape: Shape = MaterialTheme.shapes.medium,
    chrome: Boolean = false,
    solidColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
): Modifier {
  val scheme = MaterialTheme.colorScheme
  if (!UbaaGlass.enabled)
      return this.background(solidColor, shape)
          .border(
              0.5.dp,
              scheme.outlineVariant.copy(alpha = 0.35f),
              shape,
          )

  val dark = scheme.surface.luminance() < 0.5f
  val container = if (chrome) scheme.surfaceContainer else scheme.surface
  val alpha = if (dark) 0.88f else 0.90f
  val brush =
      Brush.linearGradient(
          listOf(
              container.copy(alpha = alpha),
              scheme.surfaceContainerLow.copy(alpha = alpha * 0.96f),
              scheme.surfaceContainerLow.copy(alpha = alpha * 0.94f),
          )
      )
  return this.clip(shape)
      .background(brush, shape)
      .border(
          0.5.dp,
          scheme.outlineVariant.copy(alpha = if (dark) 0.32f else 0.48f),
          shape,
      )
}

/** 导航专用透亮玻璃：分层反光、柔和阴影与渐变边缘；内容卡片继续用磨砂。 */
@Composable
fun Modifier.ubaaLiquidGlass(shape: Shape): Modifier {
  val scheme = MaterialTheme.colorScheme
  if (!UbaaGlass.enabled) return this.ubaaGlassSurface(shape = shape)
  val dark = scheme.surface.luminance() < 0.5f
  return this.shadow(12.dp, shape, clip = false)
      .clip(shape)
      .background(
          Brush.verticalGradient(
              listOf(
                  scheme.surface.copy(alpha = if (dark) 0.78f else 0.88f),
                  scheme.primary.copy(alpha = if (dark) 0.16f else 0.08f),
                  scheme.surface.copy(alpha = if (dark) 0.64f else 0.76f),
              )
          )
      )
      .border(
          1.dp,
          Brush.linearGradient(
              listOf(
                  Color.White.copy(alpha = if (dark) 0.40f else 0.88f),
                  scheme.primary.copy(alpha = 0.12f),
                  Color.White.copy(alpha = if (dark) 0.12f else 0.60f),
              )
          ),
          shape,
      )
      .drawWithCache {
        val gleam =
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = if (dark) 0.09f else 0.24f), Color.Transparent),
                center = Offset(size.width * 0.2f, 0f),
                radius = size.width * 0.7f,
            )
        onDrawWithContent {
          drawRect(gleam)
          drawContent()
        }
      }
}

/** 柔和的主题色背景，给玻璃表面提供透出的底色；静态，无动画、无强高光。 */
@Composable
fun UbaaAmbientBackground(modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val dark = scheme.surface.luminance() < 0.5f
  val primaryAlpha = if (dark) 0.18f else 0.11f
  val secondaryAlpha = if (dark) 0.12f else 0.07f
  val tertiaryAlpha = if (dark) 0.10f else 0.06f
  Canvas(modifier = modifier.fillMaxSize()) {
    drawRect(color = scheme.background)
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(scheme.primary.copy(alpha = primaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.02f),
                radius = size.minDimension * 0.85f,
            ),
        radius = size.minDimension * 0.85f,
        center = Offset(size.width * 0.12f, size.height * 0.02f),
    )
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(scheme.secondary.copy(alpha = secondaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.35f),
                radius = size.minDimension * 0.80f,
            ),
        radius = size.minDimension * 0.80f,
        center = Offset(size.width * 0.95f, size.height * 0.35f),
    )
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(scheme.tertiary.copy(alpha = tertiaryAlpha), Color.Transparent),
                center = Offset(size.width * 0.20f, size.height * 1.02f),
                radius = size.minDimension * 0.75f,
            ),
        radius = size.minDimension * 0.75f,
        center = Offset(size.width * 0.20f, size.height * 1.02f),
    )
  }
}

/**
 * 卡片统一磨砂背景。
 *
 * 之前各页面用 `CardDefaults.cardColors(containerColor = surfaceVariant)` 直接铺一块实色，和页面背景的
 * 玻璃风格对不上。改用这个：卡片容器透明、由 [ubaaGlassSurface] 画磨砂层，边框/圆角/明暗都一致。
 *
 * 用法：`Card(colors = ubaaGlassCardColors(), modifier = Modifier.<布局>.ubaaGlassSurface())`
 */
@Composable
fun ubaaGlassCardColors(): CardColors =
    CardDefaults.cardColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
