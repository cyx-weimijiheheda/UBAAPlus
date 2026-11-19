package cn.edu.ubaa.plus.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.common.components.UbaaAlertDialog
import cn.edu.ubaa.plus.ui.common.components.UbaaCheckboxRow
import cn.edu.ubaa.plus.ui.common.components.UbaaConfirmAction
import cn.edu.ubaa.plus.ui.common.components.UbaaIconAction
import cn.edu.ubaa.plus.ui.common.components.UbaaSegmentedTabs
import cn.edu.ubaa.plus.ui.screens.judge.JudgeSortField
import cn.edu.ubaa.plus.ui.screens.spoc.SpocSortField

/** SPOC 作业列表的排序与筛选对话框。 */
@Composable
internal fun SpocSortFilterDialog(
    sortField: SpocSortField,
    sortAscending: Boolean,
    showExpired: Boolean,
    showOnlyUnsubmitted: Boolean,
    onDismiss: () -> Unit,
    onApply: (SpocSortField, Boolean, Boolean, Boolean) -> Unit,
) {
  var selectedSortField by remember(sortField) { mutableStateOf(sortField) }
  var selectedSortAscending by remember(sortAscending) { mutableStateOf(sortAscending) }
  var selectedShowExpired by remember(showExpired) { mutableStateOf(showExpired) }
  var selectedShowOnlyUnsubmitted by
      remember(showOnlyUnsubmitted) { mutableStateOf(showOnlyUnsubmitted) }

  UbaaAlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("排序和筛选") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("排序字段", style = MaterialTheme.typography.titleSmall)
          UbaaSegmentedTabs(
              labels = listOf("按截止时间", "按开始时间"),
              selectedIndex = if (selectedSortField == SpocSortField.DUE_TIME) 0 else 1,
              onSelected = {
                selectedSortField =
                    if (it == 0) SpocSortField.DUE_TIME else SpocSortField.START_TIME
              },
          )
          Text("排序方向", style = MaterialTheme.typography.titleSmall)
          UbaaSegmentedTabs(
              labels = listOf("升序", "降序"),
              selectedIndex = if (selectedSortAscending) 0 else 1,
              onSelected = { selectedSortAscending = it == 0 },
          )

          Text("筛选条件", style = MaterialTheme.typography.titleSmall)
          DialogCheckboxRow(
              label = "仅显示未提交",
              checked = selectedShowOnlyUnsubmitted,
              onCheckedChange = { selectedShowOnlyUnsubmitted = it },
          )
          DialogCheckboxRow(
              label = "显示已截止",
              checked = selectedShowExpired,
              onCheckedChange = { selectedShowExpired = it },
          )
        }
      },
      confirmButton = {
        UbaaConfirmAction(
            onClick = {
              onApply(
                  selectedSortField,
                  selectedSortAscending,
                  selectedShowExpired,
                  selectedShowOnlyUnsubmitted,
              )
            },
            description = "应用",
        )
      },
      dismissButton = {
        UbaaIconAction(onClick = onDismiss, description = "取消", icon = Icons.Default.Close)
      },
  )
}

/** 希冀作业列表的排序与筛选对话框。 */
@Composable
internal fun JudgeSortFilterDialog(
    sortField: JudgeSortField,
    sortAscending: Boolean,
    showExpired: Boolean,
    showOnlyUnfinished: Boolean,
    onDismiss: () -> Unit,
    onApply: (JudgeSortField, Boolean, Boolean, Boolean) -> Unit,
) {
  var selectedSortField by remember(sortField) { mutableStateOf(sortField) }
  var selectedSortAscending by remember(sortAscending) { mutableStateOf(sortAscending) }
  var selectedShowExpired by remember(showExpired) { mutableStateOf(showExpired) }
  var selectedShowOnlyUnfinished by
      remember(showOnlyUnfinished) { mutableStateOf(showOnlyUnfinished) }

  UbaaAlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("排序和筛选") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text("排序字段", style = MaterialTheme.typography.titleSmall)
          UbaaSegmentedTabs(
              labels = listOf("按截止时间", "按开始时间"),
              selectedIndex = if (selectedSortField == JudgeSortField.DUE_TIME) 0 else 1,
              onSelected = {
                selectedSortField =
                    if (it == 0) JudgeSortField.DUE_TIME else JudgeSortField.START_TIME
              },
          )
          Text("排序方向", style = MaterialTheme.typography.titleSmall)
          UbaaSegmentedTabs(
              labels = listOf("升序", "降序"),
              selectedIndex = if (selectedSortAscending) 0 else 1,
              onSelected = { selectedSortAscending = it == 0 },
          )

          Text("筛选条件", style = MaterialTheme.typography.titleSmall)
          DialogCheckboxRow(
              label = "仅显示未完成",
              checked = selectedShowOnlyUnfinished,
              onCheckedChange = { selectedShowOnlyUnfinished = it },
          )
          DialogCheckboxRow(
              label = "显示已截止",
              checked = selectedShowExpired,
              onCheckedChange = { selectedShowExpired = it },
          )
        }
      },
      confirmButton = {
        UbaaConfirmAction(
            onClick = {
              onApply(
                  selectedSortField,
                  selectedSortAscending,
                  selectedShowExpired,
                  selectedShowOnlyUnfinished,
              )
            },
            description = "应用",
        )
      },
      dismissButton = {
        UbaaIconAction(onClick = onDismiss, description = "取消", icon = Icons.Default.Close)
      },
  )
}

@Composable
private fun DialogCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
  UbaaCheckboxRow(label, checked, onCheckedChange)
}
