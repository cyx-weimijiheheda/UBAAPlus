package cn.edu.ubaa.plus.ui.screens.spoc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.format.formatUbaaDateTime
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionCard
import cn.edu.ubaa.plus.ui.common.components.UbaaToast
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect
import cn.edu.ubaa.plus.ui.common.util.addAssignmentDeadlineToCalendar
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

/** SPOC 作业详情页。 */
@Composable
fun SpocAssignmentDetailScreen(
    viewModel: SpocViewModel,
    onRetry: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  val detail = uiState.assignmentDetail
  val context = LocalContext.current

  UbaaToastEffect(uiState.detailError) {}

  Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) { padding ->
    UbaaPullToRefresh(
        refreshing = uiState.isDetailLoading,
        onRefresh = onRetry,
        modifier = Modifier.padding(padding),
    ) {
      when {
        uiState.isDetailLoading ->
            UbaaLoadingState(
                text = "正在加载作业详情...",
                showIndicator = false,
                modifier = Modifier.fillMaxSize(),
            )
        uiState.detailError != null ->
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = uiState.detailError!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
              }
            }
        detail == null ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
              Text("暂无作业详情")
            }
        else ->
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Card(
                  modifier = Modifier.ubaaGlassSurface().fillMaxWidth(),
                  colors = CardDefaults.cardColors(containerColor = Color.Transparent),
              ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                  Text(
                      text = detail.title,
                      style = MaterialTheme.typography.headlineSmall,
                      fontWeight = FontWeight.Bold,
                  )
                  Text(
                      text = detail.courseName,
                      style = MaterialTheme.typography.titleMedium,
                      color = MaterialTheme.colorScheme.primary,
                  )
                  if (!detail.teacherName.isNullOrBlank()) {
                    Text(
                        text = "教师：${detail.teacherName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }

              DetailInfoCard(
                  title = "提交信息",
                  onAddToCalendar = {
                    val ok =
                        addAssignmentDeadlineToCalendar(
                            context = context,
                            title = detail.title,
                            courseName = detail.courseName,
                            dueTime = detail.dueTime,
                        )
                    UbaaToast.show(if (ok) "已打开日历" else "未找到可用的日历应用")
                  },
                  lines =
                      buildList {
                        add("状态：${detail.submissionStatusText}")
                        add("开始时间：${formatUbaaDateTime(detail.startTime).ifBlank { "未知" }}")
                        add("截止时间：${formatUbaaDateTime(detail.dueTime).ifBlank { "未知" }}")
                        detail.score?.takeIf { it.isNotBlank() }?.let { add("分值：$it") }
                        detail.submittedAt
                            ?.takeIf { it.isNotBlank() }
                            ?.let { add("提交时间：${formatUbaaDateTime(it)}") }
                      },
              )

              DetailInfoCard(
                  title = "作业内容",
                  lines = listOf(detail.contentPlainText ?: "暂无作业说明"),
              )
            }
      }
    }
  }
}

/**
 * 作业详情里的信息卡片。
 *
 * 三个卡片都带复制按钮；[onAddToCalendar] 只在提交信息卡片上传入，用来把截止时间写进日历。
 */
@Composable
private fun DetailInfoCard(
    title: String,
    lines: List<String>,
    onAddToCalendar: (() -> Unit)? = null,
) {
  val clipboard = LocalClipboardManager.current
  UbaaSectionCard(
      title = title,
      modifier = Modifier.fillMaxWidth(),
      // 按钮放在标题行右侧，与标题齐平，而不是另起一行占掉卡片顶部。
      trailing = {
        if (onAddToCalendar != null) {
          IconButton(onClick = onAddToCalendar, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Event,
                contentDescription = "添加到日程",
                modifier = Modifier.size(18.dp),
            )
          }
        }
        IconButton(
            onClick = {
              clipboard.setText(AnnotatedString("${title}\n" + lines.joinToString("\n")))
              UbaaToast.show("已复制$title")
            },
            modifier = Modifier.size(32.dp),
        ) {
          Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "复制$title",
              modifier = Modifier.size(18.dp),
          )
        }
      },
  ) {
    lines.forEach {
      Text(
          text = it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
