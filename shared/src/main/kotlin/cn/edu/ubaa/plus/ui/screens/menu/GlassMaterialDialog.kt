package cn.edu.ubaa.plus.ui.screens.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.storage.GlassMaterial
import cn.edu.ubaa.plus.ui.common.components.*
import cn.edu.ubaa.plus.ui.theme.LocalUbaaGlassMaterial
import kotlin.math.roundToInt

@Composable
internal fun GlassMaterialDialog(
    initial: GlassMaterial,
    onDismiss: () -> Unit,
    onApply: (GlassMaterial) -> Unit,
) {
  var draft by remember(initial) { mutableStateOf(initial.normalized()) }
  UbaaAlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("磨砂材质") },
      titleActions = {
        UbaaIconAction(
            onClick = { draft = GlassMaterial() },
            description = "恢复默认材质",
            icon = Icons.Default.RestartAlt,
        )
      },
      confirmButton = { UbaaConfirmAction(onClick = { onApply(draft) }, description = "应用磨砂材质") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          CompositionLocalProvider(LocalUbaaGlassMaterial provides draft) {
            UbaaSectionCard(title = "材质预览") {
              Text("课程、待办与功能卡片", style = MaterialTheme.typography.bodyMedium)
              Text(
                  "文字和图片保持原色",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Column(
              Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            MaterialSlider("对比度", draft.contrast) { draft = draft.copy(contrast = it) }
            MaterialSlider("饱和度", draft.saturation) { draft = draft.copy(saturation = it) }
            MaterialSlider("亮度", draft.brightness) { draft = draft.copy(brightness = it) }
            MaterialSlider("不透明度", draft.opacity) { draft = draft.copy(opacity = it) }
            MaterialSlider("边缘强度", draft.edgeStrength) { draft = draft.copy(edgeStrength = it) }
            Text(
                "范围 0–100。对比度、饱和度和亮度以 50 为中性值；仅调整内容磨砂，导航玻璃与弹窗保持原样。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
  )
}

@Composable
private fun MaterialSlider(label: String, value: Int, onChange: (Int) -> Unit) {
  Column {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.bodyMedium)
      Text(
          value.toString(),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt().coerceIn(0, 100)) },
        valueRange = 0f..100f,
        steps = 99,
        modifier = Modifier.semantics { contentDescription = label },
    )
  }
}
