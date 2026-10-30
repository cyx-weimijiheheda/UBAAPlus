package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.storage.ThemePalette
import cn.edu.ubaa.plus.ui.theme.customBrandArgbToHex
import cn.edu.ubaa.plus.ui.theme.swatchColor

/** 主题色选择器：5 套固定配色 + 自定义颜色 + 跟随壁纸。 */
@Composable
fun ThemePalettePicker(
    selected: ThemePalette,
    followWallpaper: Boolean,
    customBrandArgb: Int,
    onPaletteSelected: (ThemePalette) -> Unit,
    onCustomColorSelected: (Int) -> Unit,
    onFollowWallpaperChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
  var showCustomDialog by remember { mutableStateOf(false) }
  val presets = ThemePalette.entries.filter { it != ThemePalette.CUSTOM }
  val customColor = Color(customBrandArgb)

  if (showCustomDialog) {
    CustomBrandColorDialog(
        initialArgb = customBrandArgb,
        onDismiss = { showCustomDialog = false },
        onApply = { argb ->
          onCustomColorSelected(argb)
          showCustomDialog = false
        },
    )
  }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      presets.forEach { palette ->
        Column(
            modifier =
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !followWallpaper) { onPaletteSelected(palette) }
                    .padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          val swatch = palette.swatchColor(customBrandArgb)
          Box(
              modifier =
                  Modifier.size(38.dp)
                      .clip(CircleShape)
                      .background(swatch)
                      .border(
                          BorderStroke(
                              width = if (!followWallpaper && palette == selected) 2.dp else 0.dp,
                              color = MaterialTheme.colorScheme.onSurface,
                          ),
                          CircleShape,
                      ),
              contentAlignment = Alignment.Center,
          ) {
            if (!followWallpaper && palette == selected) {
              Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = if (swatch.luminance() > 0.5f) Color(0xFF111318) else Color.White,
                  modifier = Modifier.size(18.dp),
              )
            }
          }
          Text(
              text = palette.displayName,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (palette == selected) FontWeight.Bold else FontWeight.Normal,
              maxLines = 1,
          )
        }
      }
    }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !followWallpaper) { showCustomDialog = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
          modifier =
              Modifier.size(38.dp)
                  .clip(CircleShape)
                  .background(
                      Brush.sweepGradient(
                          listOf(
                              Color(0xFFE53935),
                              Color(0xFFFFB300),
                              Color(0xFF43A047),
                              Color(0xFF00ACC1),
                              Color(0xFF3949AB),
                              Color(0xFF8E24AA),
                              Color(0xFFE53935),
                          )
                      )
                  )
                  .border(
                      BorderStroke(
                          width =
                              if (!followWallpaper && selected == ThemePalette.CUSTOM) 2.dp
                              else 0.dp,
                          color = MaterialTheme.colorScheme.onSurface,
                      ),
                      CircleShape,
                  ),
          contentAlignment = Alignment.Center,
      ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(customColor),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text("自定义主题色", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = customBrandArgbToHex(customBrandArgb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (!followWallpaper && selected == ThemePalette.CUSTOM) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
      }
      Icon(
          Icons.Default.Edit,
          contentDescription = "编辑自定义颜色",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("跟随壁纸", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Android 12 及以上生效；低版本使用当前配色",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = followWallpaper, onCheckedChange = onFollowWallpaperChange)
    }
  }
}
