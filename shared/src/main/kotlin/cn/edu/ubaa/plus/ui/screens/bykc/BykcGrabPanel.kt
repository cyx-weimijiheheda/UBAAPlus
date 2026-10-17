package cn.edu.ubaa.plus.ui.screens.bykc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.common.components.GrabConfirmDialog
import kotlinx.datetime.LocalDateTime

/**
 * 预告课程详情页的抢课面板。
 *
 * 默认在选课开始时间自动开抢；也可以关闭定时立即开始。抢课任务由 BykcViewModel 持有， 因此离开页面但应用仍在前台时会继续。
 */
@Composable
fun BykcGrabPanel(
    courseId: Long?,
    courseName: String,
    courseSelectStart: LocalDateTime?,
    grabState: BykcGrabState,
    onStart: (scheduledStart: LocalDateTime?, retrySeconds: Double) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var useSchedule by remember(courseId) { mutableStateOf(courseSelectStart != null) }
  var retrySeconds by remember(courseId) { mutableStateOf("1.0") }
  var showConfirm by remember(courseId) { mutableStateOf(false) }
  val effectiveInterval = retrySeconds.toDoubleOrNull()?.coerceIn(0.2, 10.0) ?: 1.0
  val scheduledStart = if (useSchedule) courseSelectStart else null

  val active = grabState.phase == BykcGrabPhase.WAITING || grabState.phase == BykcGrabPhase.RUNNING
  val activeForThisCourse = active && grabState.courseId == courseId
  val activeForOtherCourse = active && grabState.courseId != null && grabState.courseId != courseId
  val lastMessage =
      when {
        activeForThisCourse -> grabState.logs.lastOrNull() ?: "准备中…"
        activeForOtherCourse -> "正在为《${grabState.courseName}》抢课"
        grabState.courseId == courseId -> grabState.message
        else -> null
      }

  Card(
      modifier = modifier.fillMaxWidth(),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "抢课",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
          text = courseSelectStart?.let { "选课开始：${formatDateTimeDisplay(it)}" } ?: "该课程没有公布选课开始时间",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
      )

      if (active) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text =
                if (activeForThisCourse) {
                  "尝试次数：${grabState.attempt} · $lastMessage"
                } else {
                  lastMessage.orEmpty()
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Icon(Icons.Default.Stop, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(if (activeForThisCourse) "停止抢课" else "停止其他课程的抢课")
        }
        return@Column
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = useSchedule && courseSelectStart != null,
            onCheckedChange = { useSchedule = it },
            enabled = courseSelectStart != null,
        )
        Text(
            text = "到选课开始时间自动开抢",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      }

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
            value = retrySeconds,
            onValueChange = { value ->
              if (value.isEmpty() || value.matches(Regex("^\\d{0,2}(\\.\\d{0,2})?$"))) {
                retrySeconds = value
              }
            },
            label = { Text("重试间隔（秒）") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { showConfirm = true },
            modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Default.Bolt, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("开始抢课")
        }
      }

      if (lastMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = lastMessage,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (grabState.phase == BykcGrabPhase.FAILED) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onTertiaryContainer
                },
        )
      }
    }
  }

  if (showConfirm) {
    GrabConfirmDialog(
        title = "确认开始抢课",
        targetLines =
            listOf(
                "课程：$courseName",
                scheduledStart?.let { "开抢时间：${formatDateTimeDisplay(it)}" } ?: "开抢时间：立即开始",
                "重试间隔：${effectiveInterval} 秒",
            ),
        onConfirm = {
          showConfirm = false
          onStart(scheduledStart, effectiveInterval)
        },
        onDismiss = { showConfirm = false },
    )
  }
}
