package cn.edu.ubaa.plus.ui.screens.menu

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import kotlin.math.roundToInt

/** 长按拖动排序 + 显示/隐藏开关。 */
@Composable
fun FeatureOrderScreen(modifier: Modifier = Modifier) {
  val preferences by AppPreferencesStore.state.collectAsState()
  val all = remember { regularFeatureItems() }
  val ordered =
      remember(preferences.featureOrder, all) { orderFeatureItems(preferences.featureOrder, all) }
  var items by remember { mutableStateOf(ordered) }
  LaunchedEffect(ordered) { items = ordered }

  var draggingId by remember { mutableStateOf<String?>(null) }
  var dragOffset by remember { mutableFloatStateOf(0f) }
  var dragStartIndex by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current
  val rowHeightPx = with(density) { RowHeight.toPx() }

  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = "长按右侧手柄拖动排序；开关控制是否在功能页显示。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider()

    items.forEach { feature ->
      val visible = feature.id !in preferences.hiddenFeatures
      val dragging = draggingId == feature.id
      Card(
          modifier =
              Modifier.fillMaxWidth()
                  .height(RowHeight)
                  .zIndex(if (dragging) 1f else 0f)
                  .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
          colors =
              CardDefaults.cardColors(
                  containerColor =
                      if (dragging) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant
              ),
      ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
              imageVector = feature.icon,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(26.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          }
          Switch(
              checked = visible,
              onCheckedChange = { checked ->
                val hidden = preferences.hiddenFeatures.toMutableSet()
                if (checked) hidden.remove(feature.id) else hidden.add(feature.id)
                AppPreferencesStore.setHiddenFeatures(hidden)
              },
          )
          Icon(
              imageVector = Icons.Default.DragHandle,
              contentDescription = "长按拖动排序",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier =
                  Modifier.size(28.dp).pointerInput(feature.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                          draggingId = feature.id
                          dragOffset = 0f
                          dragStartIndex = items.indexOfFirst { it.id == feature.id }
                        },
                        onDragEnd = {
                          draggingId = null
                          dragOffset = 0f
                          AppPreferencesStore.setFeatureOrder(items.map { it.id })
                        },
                        onDragCancel = {
                          draggingId = null
                          dragOffset = 0f
                        },
                        onDrag = { change, amount ->
                          change.consume()
                          dragOffset += amount.y
                          val currentIndex = items.indexOfFirst { it.id == feature.id }
                          if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                          val target =
                              (dragStartIndex + (dragOffset / rowHeightPx).roundToInt()).coerceIn(
                                  0,
                                  items.lastIndex,
                              )
                          if (target != currentIndex) {
                            items =
                                items.toMutableList().apply { add(target, removeAt(currentIndex)) }
                            dragOffset -= (target - currentIndex) * rowHeightPx
                          }
                        },
                    )
                  },
          )
        }
      }
    }

    HorizontalDivider()
    OutlinedButton(
        onClick = { AppPreferencesStore.resetFeatureCustomization() },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("恢复默认顺序与显示")
    }
  }
}

private val RowHeight = 68.dp
