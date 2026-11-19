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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.storage.GlassMaterial

/** 液态玻璃效果开关；默认开启，设置页可关闭。 */
val LocalUbaaGlassEnabled = staticCompositionLocalOf { true }

val LocalUbaaGlassMaterial = staticCompositionLocalOf { GlassMaterial() }

/** Adjust only the surface paint; content and school images retain their original colors. */
internal fun glassMaterialColor(color: Color, material: GlassMaterial): Color {
  val value = material.normalized()
  val saturation = value.saturation / 50f
  val contrast = 0.7f + value.contrast * 0.006f
  val brightness = (value.brightness - 50) / 250f
  val gray = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
  fun channel(c: Float) =
      (((gray + (c - gray) * saturation) - 0.5f) * contrast + 0.5f + brightness).coerceIn(0f, 1f)
  return Color(channel(color.red), channel(color.green), channel(color.blue), color.alpha)
}

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

  val material = LocalUbaaGlassMaterial.current
  val dark = scheme.surface.luminance() < 0.5f
  val container =
      lerp(
          if (chrome) scheme.surfaceContainerHigh else scheme.surfaceContainer,
          scheme.primary,
          if (dark) 0.085f else 0.045f,
      )
  val alpha = material.opacity.coerceIn(0, 100) / 100f
  val brush =
      Brush.linearGradient(
          listOf(
              glassMaterialColor(container, material).copy(alpha = alpha),
              glassMaterialColor(scheme.surfaceContainerLow, material).copy(alpha = alpha * 0.98f),
              glassMaterialColor(scheme.surfaceContainerLow, material).copy(alpha = alpha * 0.96f),
          )
      )
  return this.clip(shape)
      .background(brush, shape)
      .border(
          0.5.dp,
          scheme.outlineVariant.copy(alpha = material.edgeStrength.coerceIn(0, 100) / 100f),
          shape,
      )
}

/** 导航专用透亮玻璃；顶栏不描边且底色渐隐，底部浮动栏保留柔和边缘。 */
@Composable
fun Modifier.ubaaLiquidGlass(shape: Shape, outlined: Boolean = true): Modifier {
  val scheme = MaterialTheme.colorScheme
  if (!UbaaGlass.enabled)
      return if (outlined) this.ubaaGlassSurface(shape = shape)
      else this.background(scheme.surfaceContainerLow, shape)
  val dark = scheme.surface.luminance() < 0.5f
  // Single shape-clipped layer: no rectangular gleam or shadow inside the transparent surface.
  val surface =
      this.clip(shape)
          .background(
              Brush.verticalGradient(
                  listOf(
                      scheme.surface.copy(alpha = if (dark) 0.54f else 0.68f),
                      scheme.primary.copy(alpha = if (dark) 0.09f else 0.04f),
                      scheme.surface.copy(
                          alpha = if (!outlined) 0f else if (dark) 0.42f else 0.56f
                      ),
                  )
              ),
              shape,
          )
  return if (!outlined) surface
  else
      surface.border(
          0.5.dp,
          Brush.linearGradient(
              listOf(
                  Color.White.copy(alpha = if (dark) 0.24f else 0.65f),
                  scheme.outlineVariant.copy(alpha = 0.12f),
                  Color.White.copy(alpha = if (dark) 0.08f else 0.30f),
              )
          ),
          shape,
      )
}

/** 柔和的主题色背景，给玻璃表面提供透出的底色；静态，无动画、无强高光。 */
@Composable
fun UbaaAmbientBackground(modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val dark = scheme.surface.luminance() < 0.5f
  val primaryAlpha = if (dark) 0.10f else 0.07f
  val secondaryAlpha = if (dark) 0.06f else 0.04f
  val tertiaryAlpha = if (dark) 0.04f else 0.03f
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
