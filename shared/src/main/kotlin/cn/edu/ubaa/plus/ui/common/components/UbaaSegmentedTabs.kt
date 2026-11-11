package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

/**
 * 统一的圆角分段选择器，替代 Material 默认的方角 TabRow。
 *
 * 图书借阅的「当前借阅 / 借阅历史」这类栏目切换都用它，保证和圆角选择框观感一致。
 */
@Composable
fun UbaaSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  if (labels.isEmpty()) return
  val shape = RoundedCornerShape(14.dp)
  Row(
      modifier = modifier.ubaaGlassSurface(shape = shape).fillMaxWidth().padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    labels.forEachIndexed { index, label ->
      val selected = index == selectedIndex
      Surface(
          modifier = Modifier.weight(1f).clickable { onSelected(index) },
          shape = RoundedCornerShape(10.dp),
          color =
              if (selected) MaterialTheme.colorScheme.secondaryContainer
              else androidx.compose.ui.graphics.Color.Transparent,
          contentColor =
              if (selected) MaterialTheme.colorScheme.onSecondaryContainer
              else MaterialTheme.colorScheme.onSurfaceVariant,
      ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        )
      }
    }
  }
}
