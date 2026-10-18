package cn.edu.ubaa.plus.ui.screens.menu

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore

/**
 * 功能排序与显示。
 *
 * 列表分成「显示」和「不显示」两层：开关在两层之间移动功能，上下按钮调整层内顺序。 不使用拖动，避免长按拖动在滚动容器中不跟手。
 */
@Composable
fun FeatureOrderScreen(modifier: Modifier = Modifier) {
  val preferences by AppPreferencesStore.state.collectAsState()
  val all = remember { regularFeatureItems() }
  val ordered =
      remember(preferences.featureOrder, all) { orderFeatureItems(preferences.featureOrder, all) }
  val visible = ordered.filter { it.id !in preferences.hiddenFeatures }
  val hidden = ordered.filter { it.id in preferences.hiddenFeatures }
  var showResetConfirm by remember { mutableStateOf(false) }

  fun persist(newVisible: List<FeatureItem>, newHidden: List<FeatureItem>) {
    AppPreferencesStore.updateFeatureCustomization(
        order = (newVisible + newHidden).map { it.id },
        hidden = newHidden.map { it.id }.toSet(),
    )
  }

  fun moveVisible(index: Int, delta: Int) {
    persist(swapped(visible, index, delta), hidden)
  }

  fun moveHidden(index: Int, delta: Int) {
    persist(visible, swapped(hidden, index, delta))
  }

  Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
        text = "上方是功能页显示的功能，下方是隐藏的功能。开关在两层之间移动功能，↑ / ↓ 调整顺序。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    FeatureSectionHeader("显示的功能（${visible.size}）")
    if (visible.isEmpty()) {
      EmptySectionHint("所有功能都已隐藏，功能页会是空的。")
    } else {
      visible.forEachIndexed { index, feature ->
        FeatureOrderRow(
            feature = feature,
            visible = true,
            canMoveUp = index > 0,
            canMoveDown = index < visible.lastIndex,
            onMoveUp = { moveVisible(index, -1) },
            onMoveDown = { moveVisible(index, 1) },
            onVisibilityChange = { checked ->
              if (checked) {
                persist(visible + feature, hidden - feature)
              } else {
                persist(visible - feature, hidden + feature)
              }
            },
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    FeatureSectionHeader("不显示的功能（${hidden.size}）")
    if (hidden.isEmpty()) {
      EmptySectionHint("没有被隐藏的功能。")
    } else {
      hidden.forEachIndexed { index, feature ->
        FeatureOrderRow(
            feature = feature,
            visible = false,
            canMoveUp = index > 0,
            canMoveDown = index < hidden.lastIndex,
            onMoveUp = { moveHidden(index, -1) },
            onMoveDown = { moveHidden(index, 1) },
            onVisibilityChange = { checked ->
              if (checked) {
                persist(visible + feature, hidden - feature)
              } else {
                persist(visible - feature, hidden + feature)
              }
            },
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    OutlinedButton(
        onClick = { showResetConfirm = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text("恢复默认顺序与显示")
    }
  }

  if (showResetConfirm) {
    AlertDialog(
        onDismissRequest = { showResetConfirm = false },
        title = { Text("恢复默认设置") },
        text = { Text("所有功能将恢复默认顺序，并重新显示全部功能。此操作不可撤销。") },
        confirmButton = {
          TextButton(
              onClick = {
                showResetConfirm = false
                AppPreferencesStore.resetFeatureCustomization()
              }
          ) {
            Text("恢复默认")
          }
        },
        dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("取消") } },
    )
  }
}

@Composable
private fun FeatureSectionHeader(title: String) {
  Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(top = 4.dp),
  )
}

@Composable
private fun EmptySectionHint(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 6.dp),
  )
}

@Composable
private fun FeatureOrderRow(
    feature: FeatureItem,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
) {
  Card(
      modifier = Modifier.fillMaxWidth().height(RowHeight),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (visible) MaterialTheme.colorScheme.surfaceContainerLow
                  else MaterialTheme.colorScheme.surfaceVariant
          ),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
          imageVector = feature.icon,
          contentDescription = null,
          tint =
              if (visible) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(24.dp),
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
      Switch(checked = visible, onCheckedChange = onVisibilityChange)
      IconButton(
          onClick = onMoveUp,
          enabled = canMoveUp,
          modifier = Modifier.size(32.dp),
      ) {
        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
      }
      IconButton(
          onClick = onMoveDown,
          enabled = canMoveDown,
          modifier = Modifier.size(32.dp),
      ) {
        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
      }
    }
  }
}

private fun <T> swapped(items: List<T>, index: Int, delta: Int): List<T> {
  val target = index + delta
  if (index !in items.indices || target !in items.indices) return items
  return items.toMutableList().apply {
    val current = this[index]
    this[index] = this[target]
    this[target] = current
  }
}

private val RowHeight = 64.dp
