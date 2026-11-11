package cn.edu.ubaa.plus.ui.screens.judge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ElevatedAssistChip
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
import cn.edu.ubaa.plus.model.dto.JudgeAssignmentDetailDto
import cn.edu.ubaa.plus.model.dto.JudgeProblemDto
import cn.edu.ubaa.plus.model.format.formatUbaaDateTime
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionCard
import cn.edu.ubaa.plus.ui.common.components.UbaaToast
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect
import cn.edu.ubaa.plus.ui.common.util.addAssignmentDeadlineToCalendar
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

/** 希冀作业详情页。 */
@Composable
fun JudgeAssignmentDetailScreen(
    viewModel: JudgeViewModel,
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("暂无作业详情")
            }
        else ->
            Column(
                modifier =
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Card(
                  modifier = Modifier.fillMaxWidth().ubaaGlassSurface(),
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
                }
              }

              detail.judgeDetailInfoSections().forEach { section ->
                DetailInfoCard(
                    title = section.title,
                    lines = section.lines,
                    onAddToCalendar =
                        if (section.title == "提交信息") {
                          {
                            val ok =
                                addAssignmentDeadlineToCalendar(
                                    context = context,
                                    title = detail.title ?: "作业",
                                    courseName = detail.courseName,
                                    dueTime = detail.dueTime,
                                )
                            UbaaToast.show(if (ok) "已打开日历" else "未找到可用的日历应用")
                          }
                        } else {
                          null
                        },
                )
              }

              detail.problems.forEach { problem -> JudgeProblemCard(problem) }
            }
      }
    }
  }
}

internal data class JudgeDetailInfoSection(
    val title: String,
    val lines: List<String>,
)

internal fun JudgeAssignmentDetailDto.judgeDetailInfoSections(): List<JudgeDetailInfoSection> =
    listOf(
        JudgeDetailInfoSection(
            title = "提交信息",
            lines =
                buildList {
                  add("状态：$submissionStatusText")
                  add("开始时间：${formatUbaaDateTime(startTime).ifBlank { "未知" }}")
                  add("截止时间：${formatUbaaDateTime(dueTime).ifBlank { "未知" }}")
                  if (totalProblems > 0) {
                    add("进度：$submittedCount/$totalProblems")
                  }
                  maxScore
                      ?.takeIf { it.isNotBlank() }
                      ?.let { maxScore ->
                        val scoreText = myScore?.takeIf { it.isNotBlank() } ?: "无"
                        add("分数：$scoreText / $maxScore")
                      }
                },
        ),
        JudgeDetailInfoSection(
            title = "题目明细",
            lines = if (problems.isEmpty()) listOf("暂无题目明细") else emptyList(),
        ),
    )

/** 作业详情信息卡片：统一带复制按钮，提交信息卡片额外提供「添加到日程」。 */
@Composable
private fun DetailInfoCard(
    title: String,
    lines: List<String>,
    onAddToCalendar: (() -> Unit)? = null,
) {
  val clipboard = LocalClipboardManager.current
  UbaaSectionCard(title = title, modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (onAddToCalendar != null) {
        IconButton(onClick = onAddToCalendar) {
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
          }
      ) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "复制$title",
            modifier = Modifier.size(18.dp),
        )
      }
    }
    lines.forEach {
      Text(
          text = it,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun JudgeProblemCard(problem: JudgeProblemDto) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = problem.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        ElevatedAssistChip(onClick = {}, label = { Text(problem.statusText) })
      }

      problem.maxScore
          ?.takeIf { it.isNotBlank() }
          ?.let { maxScore ->
            val scoreText = problem.score?.takeIf { it.isNotBlank() } ?: "无"
            Text(
                text = "分数：$scoreText / $maxScore",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
    }
  }
}
