package cn.edu.ubaa.plus.ui.screens.libbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.LibBookQrScanResultDto
import cn.edu.ubaa.plus.model.dto.LibBookSeatAction
import cn.edu.ubaa.plus.model.format.formatUbaaDateWithTimeRange

/**
 * 座位操作弹窗集合。
 *
 * 「我的座位预约」与图书馆座位主界面共用：扫码或点列表项后都会先识别座位，再在这里选择具体操作。
 *
 * @param onConfirmLeave 临时离开/完全离开的确认回调（需要先取定位）。
 */
@Composable
fun LibBookSeatActionDialogs(
    context: LibBookQrScanResultDto?,
    pendingAction: LibBookSeatAction?,
    submitting: Boolean,
    locating: Boolean,
    onDismiss: () -> Unit,
    onAction: (LibBookSeatAction) -> Unit,
    onConfirmLeave: (LibBookSeatAction) -> Unit,
) {
  if (context == null) return
  if (pendingAction == LibBookSeatAction.LEAVE || pendingAction == LibBookSeatAction.CHECKOUT) {
    val checkout = pendingAction == LibBookSeatAction.CHECKOUT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (checkout) "完全离开" else "临时离开") },
        text = {
          Text(
              if (checkout) {
                "确认结束使用并释放座位？释放后需要重新预约。将获取并提交当前位置用于平台校验。"
              } else {
                "确认临时离开？离馆时间过长会由系统自动签退并释放座位。将获取并提交当前位置用于平台校验。"
              }
          )
        },
        confirmButton = {
          TextButton(
              onClick = { onConfirmLeave(pendingAction) },
              enabled = !submitting && !locating,
          ) {
            Text(if (checkout) "确认离开" else "确认暂离")
          }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    return
  }

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("座位操作") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
              listOf(context.areaName, context.seatNo)
                  .filter { it.isNotBlank() }
                  .joinToString(" / ")
                  .ifBlank { "已识别座位" },
              fontWeight = FontWeight.Bold,
          )
          if (context.beginTime.isNotBlank() || context.endTime.isNotBlank()) {
            Text(formatUbaaDateWithTimeRange(context.day, context.beginTime, context.endTime))
          }
          // 优先展示上游文案或已翻译的码义，不再只丢一个裸返回值。
          Text(
              text = context.scanResultMessage(),
              color =
                  if (context.qrScanLanded()) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = if (context.qrScanLanded()) FontWeight.Medium else FontWeight.Normal,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (context.canReturnSignIn) {
              AssistChip(
                  onClick = { onAction(LibBookSeatAction.RETURN_SIGN_IN) },
                  enabled = !submitting,
                  label = { Text("返回签到") },
              )
            } else if (context.canSignIn) {
              AssistChip(
                  onClick = { onAction(LibBookSeatAction.SIGN_IN) },
                  enabled = !submitting,
                  label = { Text("签到") },
              )
            }
            if (context.canTemporaryLeave) {
              AssistChip(
                  onClick = { onAction(LibBookSeatAction.LEAVE) },
                  enabled = !submitting,
                  label = { Text("临时离开") },
              )
            }
            if (context.canCheckout) {
              AssistChip(
                  onClick = { onAction(LibBookSeatAction.CHECKOUT) },
                  enabled = !submitting,
                  label = { Text("完全离开") },
              )
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}

/** 确认预约前的规则确认框：座位规则全文只在这里出现。 */
@Composable
fun LibBookReserveConfirmDialog(
    summary: String,
    submitting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("确认预约座位") },
      text = {
        Column(
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(summary, fontWeight = FontWeight.Medium)
          Text(
              "座位使用规则",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
          )
          LIB_BOOK_RULES.forEachIndexed { index, rule ->
            Text("${index + 1}. $rule", style = MaterialTheme.typography.bodySmall)
          }
        }
      },
      confirmButton = {
        TextButton(onClick = onConfirm, enabled = !submitting) {
          Text(if (submitting) "提交中..." else "确认")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

/** 座位规则全文弹窗，供顶部小问号入口使用。 */
@Composable
fun LibBookRulesDialog(onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("图书馆座位使用规则") },
      text = {
        Column(
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          LIB_BOOK_RULES.forEachIndexed { index, rule ->
            Text("${index + 1}. $rule", style = MaterialTheme.typography.bodyMedium)
          }
        }
      },
      confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
  )
}
