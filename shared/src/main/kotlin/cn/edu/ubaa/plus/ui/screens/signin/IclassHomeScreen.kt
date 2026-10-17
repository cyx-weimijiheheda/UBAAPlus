package cn.edu.ubaa.plus.ui.screens.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.IclassAttendanceRecordDto

/** 智慧教室：今日课程一键签到 + 本学期考勤查询。扫码入口在标题栏。 */
@Composable
fun IclassHomeScreen(viewModel: SigninViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(uiState.signinResult) {
    uiState.signinResult?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearSigninResult()
    }
  }

  Scaffold(
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      floatingActionButton = {
        FloatingActionButton(
            onClick = {
              viewModel.loadTodayClasses()
              viewModel.loadAttendance()
            }
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "刷新")
        }
      },
  ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
        ) {
          Text(
              text = "智慧教室需连接校园网后使用，请以校园网登录查看。",
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      item { SectionTitle("今日课程") }

      when {
        uiState.isLoading ->
            item {
              Box(
                  modifier = Modifier.fillMaxWidth().height(80.dp),
                  contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            }
        uiState.error != null ->
            item {
              RetryRow(message = uiState.error.orEmpty(), onRetry = viewModel::loadTodayClasses)
            }
        uiState.classes.isEmpty() -> item { EmptyHint("今日无课程安排") }
        else ->
            items(uiState.classes) { clazz ->
              SigninClassCard(
                  clazz = clazz,
                  onSigninClick = { viewModel.performSignin(clazz.courseId) },
                  isSigningIn = uiState.signingInCourseId == clazz.courseId,
              )
            }
      }

      item {
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))
      }

      item {
        SectionTitle(
            text = "考勤查询",
            subtitle = uiState.attendanceTermName ?: "本学期",
        )
      }

      when {
        uiState.attendanceLoading && uiState.attendanceRecords.isEmpty() ->
            item {
              Box(
                  modifier = Modifier.fillMaxWidth().height(80.dp),
                  contentAlignment = Alignment.Center,
              ) {
                CircularProgressIndicator()
              }
            }
        uiState.attendanceError != null ->
            item {
              RetryRow(
                  message = uiState.attendanceError.orEmpty(),
                  onRetry = viewModel::loadAttendance,
              )
            }
        uiState.attendanceRecords.isEmpty() -> item { EmptyHint("暂无考勤记录") }
        else -> {
          val groups =
              uiState.attendanceRecords.groupBy { record -> recordDate(record).ifBlank { "未知日期" } }
          groups.forEach { (date, records) ->
            item(key = "attendance-date-$date") {
              Text(
                  text = date,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
              )
            }
            items(records) { record -> AttendanceRecordCard(record) }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String, subtitle: String? = null) {
  Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
    )
    subtitle?.let {
      Text(
          text = it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyHint(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
  )
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
      Text(
          text = message,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
      )
      TextButton(onClick = onRetry) { Text("重试") }
    }
  }
}

@Composable
private fun AttendanceRecordCard(record: IclassAttendanceRecordDto) {
  val signed = record.signStatus == 1
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (signed) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceVariant
          ),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = record.courseName.ifBlank { "未知课程" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        if (record.teacherName.isNotBlank()) {
          Text(
              text = record.teacherName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
              text = attendanceTimeText(record),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (signed) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "已签到",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
      } else {
        Text(
            text = attendanceStatusText(record.signStatus),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun recordDate(record: IclassAttendanceRecordDto): String {
  val raw = record.teachTime.ifBlank { record.classBeginTime }
  return Regex("""\d{4}-\d{2}-\d{2}""").find(raw)?.value ?: raw
}

private fun attendanceTimeText(record: IclassAttendanceRecordDto): String {
  val begin = record.classBeginTime.takeIf { it.isNotBlank() }?.take(5)
  val end = record.classEndTime.takeIf { it.isNotBlank() }?.take(5)
  return when {
    begin != null && end != null -> "$begin-$end"
    begin != null -> begin
    else -> record.teachTime
  }
}

private fun attendanceStatusText(status: Int): String =
    when (status) {
      0 -> "未签到"
      1 -> "已签到"
      else -> "状态 $status"
    }
