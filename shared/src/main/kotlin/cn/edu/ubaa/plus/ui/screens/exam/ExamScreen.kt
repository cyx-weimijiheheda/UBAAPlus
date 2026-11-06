package cn.edu.ubaa.plus.ui.screens.exam

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.ubaa.plus.model.dto.Exam
import cn.edu.ubaa.plus.model.format.formatUbaaCompactDate
import cn.edu.ubaa.plus.model.format.formatUbaaDate
import cn.edu.ubaa.plus.model.format.formatUbaaTime
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import cn.edu.ubaa.plus.ui.common.components.UbaaEmptyState
import cn.edu.ubaa.plus.ui.common.components.UbaaErrorState
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionCard
import cn.edu.ubaa.plus.ui.common.components.UbaaSectionHeader
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect
import kotlin.collections.get
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.*

@Composable
fun ExamScreen(viewModel: ExamViewModel) {
  val uiState by viewModel.uiState.collectAsState()

  UbaaToastEffect(uiState.error) {}

  UbaaPullToRefresh(
      refreshing = uiState.isLoading,
      onRefresh = { viewModel.ensureLoaded(forceRefresh = true) },
  ) {
    when {
      uiState.isLoading ->
          UbaaLoadingState(
              text = "正在加载考试安排...",
              showIndicator = false,
              modifier = Modifier.fillMaxSize(),
          )
      uiState.error != null ->
          UbaaErrorState(
              message = "加载失败: ${uiState.error}",
              onRetry = { viewModel.ensureLoaded(forceRefresh = true) },
              modifier = Modifier.fillMaxSize(),
          )
      uiState.examData != null -> {
        ExamTimelineList(
            arranged = uiState.examData!!.arranged,
            notArranged = uiState.examData!!.notArranged,
        )
      }
    }
  }
}

@Composable
fun ExamTimelineList(arranged: List<Exam>, notArranged: List<Exam>) {
  var showFinished by remember { mutableStateOf(false) }
  val (finished, upcoming) = arranged.partition { isExamFinished(it) }

  LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (upcoming.isNotEmpty()) {
      item { UbaaSectionHeader(title = "即将到来", subtitle = "共 ${upcoming.size} 场") }
      items(upcoming.sortedBy { it.examDate.orEmpty() }) { exam ->
        ExamCard(exam = exam, showSeat = true)
      }
    }

    if (notArranged.isNotEmpty()) {
      item { UbaaSectionHeader(title = "未安排/其他", subtitle = "共 ${notArranged.size} 场") }
      items(notArranged) { exam -> ExamCard(exam = exam, showSeat = false) }
    }

    if (finished.isNotEmpty()) {
      item {
        Surface(
            onClick = { showFinished = !showFinished },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
                text = "已结束考试 (${finished.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector =
                    if (showFinished) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showFinished) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      if (showFinished) {
        items(finished.sortedByDescending { it.examDate.orEmpty() }) { exam ->
          ExamCard(exam = exam, showSeat = true, isFinished = true)
        }
      }
    }

    if (upcoming.isEmpty() && notArranged.isEmpty() && finished.isEmpty()) {
      item {
        UbaaEmptyState(
            title = "暂无考试安排",
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        )
      }
    }
  }
}

@Composable
fun ExamTimelineItem(
    exam: Exam,
    dateString: String?,
    isLastInGroup: Boolean,
    isLastGlobal: Boolean,
    isFinished: Boolean = false,
) {
  val timelineColor =
      if (isFinished) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary

  Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    // 左侧：日期与时间线
    Column(modifier = Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      if (dateString != null && dateString != "待定") {
        Text(
            text = formatUbaaCompactDate(dateString),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = timelineColor,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp,
        )
      }

      Box(modifier = Modifier.weight(1f).width(20.dp), contentAlignment = Alignment.TopCenter) {
        Canvas(modifier = Modifier.fillMaxHeight().width(2.dp)) {
          val lineEnd = if (isLastGlobal) size.height * 0.2f else size.height
          drawLine(
              color = timelineColor.copy(alpha = 0.3f),
              start = Offset(size.width / 2, 0f),
              end = Offset(size.width / 2, lineEnd),
              strokeWidth = 2.dp.toPx(),
          )
          drawCircle(
              color = timelineColor,
              radius = 4.dp.toPx(),
              center = Offset(size.width / 2, 16.dp.toPx()), // 圆点与卡片顶部对齐
          )
        }
      }
    }

    // 右侧：考试卡片
    Box(modifier = Modifier.weight(1f).padding(bottom = if (isLastInGroup) 24.dp else 12.dp)) {
      ExamCard(exam, showSeat = true, isFinished = isFinished)
    }
  }
}

@Composable
fun ExamCard(exam: Exam, showSeat: Boolean, isFinished: Boolean = false) {
  val alpha = if (isFinished) 0.65f else 1f
  UbaaSectionCard(
      modifier = Modifier.fillMaxWidth().alpha(alpha),
      glass = false,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = exam.courseName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
      )
      if (showSeat && !exam.examSeatNo.isNullOrBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
                Icons.Default.Chair,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${exam.examSeatNo}号",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
    }

    val dateText = exam.examDate?.let { formatUbaaDate(it) }
    val begin = parseUbaaTime(exam.startTime)
    val end = parseUbaaTime(exam.endTime)
    val timeText =
        if (begin != null && end != null) {
          "${formatUbaaTime(begin)}-${formatUbaaTime(end)}"
        } else {
          exam.examTimeDescription
        }
    val timeLine = listOfNotNull(dateText, timeText?.takeIf { it.isNotBlank() }).joinToString(" ")
    if (timeLine.isNotBlank()) ExamInfoLine("时间", timeLine)
    exam.examPlace?.takeIf { it.isNotBlank() }?.let { ExamInfoLine("地点", it) }
    exam.examType?.takeIf { it.isNotBlank() }?.let { ExamInfoLine("类型", it) }
    exam.examStatus?.let { ExamInfoLine("状态", examStatusText(it)) }
    exam.week?.let { ExamInfoLine("周次", "第${it}周") }
  }
}

private fun examStatusText(status: Int): String =
    when (status) {
      0 -> "未开始"
      1 -> "进行中"
      2 -> "已结束"
      else -> "状态 $status"
    }

@Composable
private fun ExamInfoLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "$label：",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(48.dp),
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium)
  }
}

@OptIn(ExperimentalTime::class)
private fun isExamFinished(exam: Exam): Boolean {
  val dateStr = exam.examDate?.substringBefore(" ") ?: return false
  val endTimeStr = exam.endTime ?: "23:59"
  return try {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val examDate = LocalDate.parse(dateStr)
    val examEndTime =
        try {
          LocalTime.parse(endTimeStr)
        } catch (e: Exception) {
          LocalTime(23, 59)
        }

    if (now.date > examDate) true else if (now.date < examDate) false else now.time > examEndTime
  } catch (e: Exception) {
    false
  }
}
