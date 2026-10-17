package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 抢课/抢场地确认弹窗中反复使用的简短免责声明。 */
const val GRAB_DISCLAIMER =
    "免责声明：自动化抢课或抢场地可能违反学校规定，也可能触发风控、限流或审计。" + "账号被警告、限制或封禁的后果由你自行承担。请确认你已了解并同意自行承担相关风险。"

/**
 * 开始抢课/抢场地的确认弹窗。
 *
 * @param targetLines 本次要抢的内容，一行一项。
 */
@Composable
fun GrabConfirmDialog(
    title: String,
    targetLines: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
              text = "即将开始：",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(6.dp))
          targetLines.forEach { line ->
            Text(text = "· $line", style = MaterialTheme.typography.bodyMedium)
          }
          Spacer(modifier = Modifier.height(12.dp))
          Card(
              modifier = Modifier.fillMaxWidth(),
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.errorContainer
                  ),
          ) {
            Text(
                text = GRAB_DISCLAIMER,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      },
      confirmButton = { TextButton(onClick = onConfirm) { Text("确认开始") } },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}
