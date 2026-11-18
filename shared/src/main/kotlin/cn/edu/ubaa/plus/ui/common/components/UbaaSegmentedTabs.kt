package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

/** 统一的单选分段栏；空间不足时横向滚动，完整保留长选项文字。 */
@Composable
fun UbaaSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable (Int) -> Unit)? = null,
) {
  UbaaSegmentedOptions(
      labels,
      { it == selectedIndex },
      onSelected,
      modifier,
      false,
      enabled,
      leadingContent,
  )
}

/** 与单选栏共享外观，保留每项独立勾选与再次点击取消的行为。 */
@Composable
fun UbaaSegmentedMultiSelect(
    labels: List<String>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
  UbaaSegmentedOptions(labels, { it in selectedIndices }, onToggle, modifier, true, true, null)
}

@Composable
private fun UbaaSegmentedOptions(
    labels: List<String>,
    isSelected: (Int) -> Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier,
    multiple: Boolean,
    enabled: Boolean,
    leadingContent: (@Composable (Int) -> Unit)?,
) {
  if (labels.isEmpty()) return
  val textMeasurer = rememberTextMeasurer()
  val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
  val density = LocalDensity.current
  BoxWithConstraints(
      modifier =
          modifier.ubaaGlassSurface(shape = RoundedCornerShape(14.dp)).fillMaxWidth().padding(4.dp)
  ) {
    val segmentWidth = (maxWidth - 4.dp * (labels.size - 1)) / labels.size
    val widths =
        labels.map { label ->
          val textWidth = with(density) { textMeasurer.measure(label, style).size.width.toDp() }
          (textWidth + 24.dp + (if (leadingContent == null) 0.dp else 24.dp)).coerceAtLeast(64.dp)
        }
    val scroll = widths.any { it > segmentWidth }
    val scrollState = rememberScrollState()
    val selectedIndex = if (multiple) -1 else labels.indices.firstOrNull(isSelected) ?: -1
    LaunchedEffect(widths, selectedIndex, scroll) {
      if (scroll && selectedIndex >= 0) {
        withFrameNanos {}
        val offset = widths.take(selectedIndex).fold(0.dp) { sum, width -> sum + width + 4.dp }
        scrollState.scrollTo(
            with(density) { offset.roundToPx() }.coerceAtMost(scrollState.maxValue)
        )
      }
    }
    Row(
        modifier =
            (if (scroll) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth()).then(
                if (multiple) Modifier else Modifier.selectableGroup()
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      labels.forEachIndexed { index, label ->
        val selected = isSelected(index)
        val interaction =
            if (multiple) {
              Modifier.toggleable(
                  value = selected,
                  enabled = enabled,
                  role = Role.Checkbox,
                  onValueChange = { onClick(index) },
              )
            } else {
              Modifier.selectable(
                  selected = selected,
                  enabled = enabled,
                  role = Role.Tab,
                  onClick = { onClick(index) },
              )
            }
        Surface(
            modifier =
                (if (scroll) Modifier.width(widths[index]) else Modifier.weight(1f)).then(
                    interaction
                ),
            shape = RoundedCornerShape(10.dp),
            color =
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            contentColor =
                (if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant)
                    .let { if (enabled) it else it.copy(alpha = 0.38f) },
        ) {
          Row(
              modifier =
                  Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            leadingContent?.invoke(index)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
          }
        }
      }
    }
  }
}
