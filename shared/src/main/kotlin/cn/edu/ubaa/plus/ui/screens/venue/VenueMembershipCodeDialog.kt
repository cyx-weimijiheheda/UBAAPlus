package cn.edu.ubaa.plus.ui.screens.venue

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.AppForegroundMonitor
import cn.edu.ubaa.plus.model.format.formatUbaaDateTime
import kotlinx.coroutines.delay

/**
 * 场馆会员预约码弹窗。
 *
 * 打开时立即请求一次，之后每 10 秒刷新；应用进入后台时暂停轮询。
 */
@Composable
fun VenueMembershipCodeDialog(viewModel: VenueViewModel, onDismiss: () -> Unit) {
  val state by viewModel.membershipCodeState.collectAsState()
  val foreground by AppForegroundMonitor.foreground.collectAsState()

  LaunchedEffect(foreground) {
    if (!foreground) return@LaunchedEffect
    while (true) {
      viewModel.loadMembershipCode()
      delay(10_000)
    }
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("会员预约码") },
      text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val code = state.code
          val bitmap = decodeVenueImage(code?.qrCode)

          when {
            code == null && state.isLoading ->
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            bitmap != null ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "会员预约码",
                    modifier = Modifier.size(240.dp),
                )
            else ->
                Text(
                    text = "暂无会员卡信息",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
          }

          code
              ?.dueDate
              ?.takeIf { it.isNotBlank() }
              ?.let {
                Text(
                    text = "有效期至：${formatUbaaDateTime(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
              }
          Text(
              text = "每 10 秒自动刷新",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          state.error?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}
