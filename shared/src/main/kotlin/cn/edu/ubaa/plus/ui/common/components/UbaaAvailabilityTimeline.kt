package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.theme.UbaaColors

internal enum class AvailabilityTone {
  UNAVAILABLE,
  AVAILABLE,
  SELECTED,
}

internal data class AvailabilityRun(val tone: AvailabilityTone, val length: Int)

/** Keep the server's slot order; a gap in actual times must never look continuous. */
internal fun availabilityRuns(
    states: List<AvailabilityTone>,
    breaksBefore: Set<Int> = emptySet(),
): List<AvailabilityRun> {
  val runs = mutableListOf<AvailabilityRun>()
  states.forEachIndexed { index, tone ->
    val last = runs.lastOrNull()
    if (last != null && last.tone == tone && index !in breaksBefore) {
      runs[runs.lastIndex] = last.copy(length = last.length + 1)
    } else runs += AvailabilityRun(tone, 1)
  }
  return runs
}

@Composable
internal fun UbaaAvailabilityTrack(
    states: List<AvailabilityTone>,
    slotWidth: Dp,
    breaksBefore: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
) {
  Row(modifier.width(slotWidth * states.size).height(18.dp)) {
    availabilityRuns(states, breaksBefore).forEach { run ->
      val color =
          when (run.tone) {
            AvailabilityTone.AVAILABLE -> UbaaColors.current.successContainer
            AvailabilityTone.SELECTED -> MaterialTheme.colorScheme.primary
            AvailabilityTone.UNAVAILABLE ->
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
          }
      Box(
          Modifier.width(slotWidth * run.length)
              .fillMaxHeight()
              .padding(horizontal = 1.dp)
              .clip(CircleShape)
              .background(color)
      )
    }
  }
}

@Composable
internal fun UbaaTimelineMarker(label: String, size: Dp) {
  Box(
      Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
      contentAlignment = Alignment.Center,
  ) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
    )
  }
}

@Composable
internal fun UbaaAvailabilityLegend(
    includeSelected: Boolean = false,
    modifier: Modifier = Modifier,
) {
  FlowRow(
      modifier,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    TimelineLegendItem(if (includeSelected) "可预约" else "空闲", AvailabilityTone.AVAILABLE)
    if (includeSelected) TimelineLegendItem("已选", AvailabilityTone.SELECTED)
    TimelineLegendItem(if (includeSelected) "不可选" else "未列为空闲", AvailabilityTone.UNAVAILABLE)
  }
}

@Composable
private fun TimelineLegendItem(label: String, tone: AvailabilityTone) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    UbaaAvailabilityTrack(listOf(tone), 22.dp, modifier = Modifier.height(8.dp))
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
