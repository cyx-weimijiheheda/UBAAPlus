package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

/** 全应用统一的圆角下拉选择框。 */
private val UbaaSelectorShape = RoundedCornerShape(14.dp)

/**
 * 带磨砂与圆角的选择框。
 *
 * 之前各页面直接用 [OutlinedButton]，默认是方角矩形，和整体的圆角/磨砂风格对不上。统一走这里， 保证「校区」「楼馆」这类栏目选择框外观一致。
 */
@Composable
fun UbaaDropdownSelector(
    selectedText: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
  var expanded by remember { mutableStateOf(false) }
  val text = if (label.isNullOrBlank()) selectedText else "$label：$selectedText"

  // 磨砂层画在外层 Box 上，按钮本身完全透明且不画边框：
  // 直接把玻璃挂在 OutlinedButton 上时，按钮自带的 1dp 描边会内缩，视觉上比磨砂块小一圈。
  Row(
      modifier = modifier.ubaaGlassSurface(shape = UbaaSelectorShape).fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedButton(
        onClick = { expanded = true },
        enabled = enabled,
        shape = UbaaSelectorShape,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent),
        border = null,
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
    }
  }

  DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      shape = RoundedCornerShape(14.dp),
  ) {
    options.forEachIndexed { index, option ->
      DropdownMenuItem(
          text = { Text(option) },
          onClick = {
            expanded = false
            onSelected(index)
          },
      )
    }
  }
}
