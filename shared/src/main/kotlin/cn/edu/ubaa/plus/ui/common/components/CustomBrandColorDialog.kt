package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.customBrandArgbFromHsv
import cn.edu.ubaa.plus.ui.theme.customBrandArgbHsv
import cn.edu.ubaa.plus.ui.theme.customBrandArgbToHex
import cn.edu.ubaa.plus.ui.theme.normalizeCustomBrandArgb
import cn.edu.ubaa.plus.ui.theme.parseCustomBrandHex

@Composable
fun CustomBrandColorDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
  val initialHsv = remember(initialArgb) { customBrandArgbHsv(initialArgb) }
  var hue by remember(initialArgb) { mutableFloatStateOf(initialHsv[0]) }
  var saturation by remember(initialArgb) { mutableFloatStateOf(initialHsv[1]) }
  var value by remember(initialArgb) { mutableFloatStateOf(initialHsv[2]) }
  var hexText by
      remember(initialArgb) {
        mutableStateOf(customBrandArgbToHex(normalizeCustomBrandArgb(initialArgb)))
      }
  var hexError by remember { mutableStateOf(false) }

  val currentArgb = normalizeCustomBrandArgb(customBrandArgbFromHsv(hue, saturation, value))
  val currentColor = Color(currentArgb)

  fun updateHsv(newHue: Float, newSaturation: Float, newValue: Float) {
    hue = newHue
    saturation = newSaturation
    value = newValue
    hexText =
        customBrandArgbToHex(
            normalizeCustomBrandArgb(customBrandArgbFromHsv(newHue, newSaturation, newValue))
        )
    hexError = false
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("自定义主题色") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(
              modifier =
                  Modifier.fillMaxWidth()
                      .height(56.dp)
                      .background(currentColor, RoundedCornerShape(16.dp))
          )
          Text(
              text = customBrandArgbToHex(currentArgb),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          SliderRow(
              label = "色相",
              valueText = "${hue.toInt()}°",
              value = hue,
              valueRange = 0f..360f,
              onValueChange = { updateHsv(it, saturation, value) },
          )
          SliderRow(
              label = "饱和度",
              valueText = "${(saturation * 100).toInt()}%",
              value = saturation,
              valueRange = 0f..1f,
              onValueChange = { updateHsv(hue, it, value) },
          )
          SliderRow(
              label = "明度",
              valueText = "${(value * 100).toInt()}%",
              value = value,
              valueRange = 0.15f..1f,
              onValueChange = { updateHsv(hue, saturation, it) },
          )
          OutlinedTextField(
              value = hexText,
              onValueChange = { input ->
                hexText = input
                val parsed = parseCustomBrandHex(input)
                hexError = parsed == null
                if (parsed != null) {
                  val hsv = customBrandArgbHsv(normalizeCustomBrandArgb(parsed))
                  hue = hsv[0]
                  saturation = hsv[1]
                  value = hsv[2]
                }
              },
              label = { Text("十六进制") },
              singleLine = true,
              isError = hexError,
              supportingText = { if (hexError) Text("请输入 #RRGGBB 或 #AARRGGBB") },
              modifier = Modifier.fillMaxWidth(),
          )
          Text(
              text = "会自动限制过暗、过亮或过灰的颜色，保证文字可读性。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        TextButton(
            onClick = {
              val parsed = parseCustomBrandHex(hexText)
              if (parsed != null) {
                onApply(normalizeCustomBrandArgb(parsed))
              } else {
                hexError = true
              }
            }
        ) {
          Text("应用")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
  Column {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium)
      Text(valueText, style = MaterialTheme.typography.bodySmall)
    }
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
  }
}
