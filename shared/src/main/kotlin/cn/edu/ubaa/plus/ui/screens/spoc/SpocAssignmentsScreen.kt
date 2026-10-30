package cn.edu.ubaa.plus.ui.screens.spoc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.SpocAssignmentSummaryDto
import cn.edu.ubaa.plus.model.format.formatUbaaDateTime
import cn.edu.ubaa.plus.ui.common.components.UbaaEmptyState
import cn.edu.ubaa.plus.ui.common.components.UbaaErrorState
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionHeader
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect

/** SPOC 作业列表页。 */
@Composable
fun SpocAssignmentsScreen(
    viewModel: SpocViewModel,
    onAssignmentClick: (SpocAssignmentSummaryDto) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  UbaaToastEffect(uiState.error) {}
  val groupedAssignments = uiState.visibleAssignments.groupBy { it.courseName }
  var showSearchDialog by remember { mutableStateOf(false) }

  if (showSearchDialog) {
    SearchAssignmentsDialog(
        initialQuery = uiState.searchQuery,
        onDismiss = { showSearchDialog = false },
        onApply = { query ->
          viewModel.setSearchQuery(query)
          showSearchDialog = false
        },
        onClear = {
          viewModel.setSearchQuery("")
          showSearchDialog = false
        },
    )
  }

  Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
      floatingActionButton = {
        FloatingActionButton(onClick = { showSearchDialog = true }) {
          Icon(Icons.Default.Search, contentDescription = "搜索")
        }
      },
  ) { padding ->
    UbaaPullToRefresh(
        refreshing = uiState.isRefreshing,
        onRefresh = { viewModel.loadAssignments(refresh = true) },
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      when {
        uiState.isLoading && uiState.assignmentsResponse == null ->
            UbaaLoadingState(showIndicator = true, modifier = Modifier.fillMaxSize())
        uiState.error != null && uiState.assignmentsResponse == null ->
            UbaaErrorState(
                message = uiState.error!!,
                onRetry = { viewModel.loadAssignments() },
                modifier = Modifier.fillMaxSize(),
            )
        groupedAssignments.isEmpty() ->
            UbaaEmptyState(title = "暂无符合条件的 SPOC 作业", modifier = Modifier.fillMaxSize())
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              groupedAssignments.forEach { (courseName, assignments) ->
                item { UbaaSectionHeader(title = courseName) }

                items(assignments) { assignment ->
                  SpocAssignmentCard(
                      assignment = assignment,
                      onClick = { onAssignmentClick(assignment) },
                  )
                }
              }
            }
      }
    }
  }
}

@Composable
private fun SearchAssignmentsDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
    onClear: () -> Unit,
) {
  var query by remember(initialQuery) { mutableStateOf(initialQuery) }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("搜索作业") },
      text = {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("课程名称或作业标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = { TextButton(onClick = { onApply(query) }) { Text("搜索") } },
      dismissButton = {
        TextButton(
            onClick = {
              if (initialQuery.isNotBlank()) {
                onClear()
              } else {
                onDismiss()
              }
            }
        ) {
          Text(if (initialQuery.isNotBlank()) "清空" else "取消")
        }
      },
  )
}

@Composable
private fun SpocAssignmentCard(
    assignment: SpocAssignmentSummaryDto,
    onClick: () -> Unit,
) {
  Card(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = assignment.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        ElevatedAssistChip(
            onClick = onClick,
            label = { Text(assignment.submissionStatusText) },
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      if (!assignment.teacherName.isNullOrBlank()) {
        Text(
            text = "教师：${assignment.teacherName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
          text = "开始：${formatUbaaDateTime(assignment.startTime).ifBlank { "未知" }}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          text = "截止：${formatUbaaDateTime(assignment.dueTime).ifBlank { "未知" }}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      assignment.score
          ?.takeIf { it.isNotBlank() }
          ?.let {
            Text(
                text = "分值：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
    }
  }
}
