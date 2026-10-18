package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** 首次使用与「关于」页共用的完整免责声明。 */
val FIRST_RUN_DISCLAIMER: List<String> =
    listOf(
        "UBAAPlus 是个人自用的北航校园服务客户端，用于查询课表、成绩、考试、校园卡、寝室用电等信息，并为部分预约类功能提供便捷入口。它不是学校官方应用，也不代表学校立场。",
        "使用者需要自行确认并遵守北航的信息化服务使用协议、场馆与教室预约规定、选课规定以及图书馆借阅规则。是否允许自动化操作最终由学校认定；如果学校明示禁止，请立即停用并删除本应用。",
        "自动化或高频请求可能触发风控、限流或审计。账号被警告、限制或封禁的后果由使用者自行承担。请勿把轮询间隔调得过低，这既增加账号风险，也会影响其他同学正常使用。",
        "本应用没有利用任何服务端漏洞：它只是用你自己的账号和凭证完成登录与查询。支付环节不会自动化，最后一步必须由你本人在校方支付平台确认。",
        "校方随时可能改版，接口、页面或常量失效时以线上实际版本为准。部分功能依赖校园网环境，离开校园网后可能无法使用。继续使用即表示你已理解并同意自行承担相关风险。",
    )

/** 首次启动的免责声明，必须点击同意后才能继续使用。 */
@Composable
fun FirstRunDisclaimerDialog(onAccept: () -> Unit) {
  AlertDialog(
      onDismissRequest = {},
      title = { Text("使用前请阅读") },
      text = {
        Column(
            modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
              text = "免责声明",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.error,
          )
          FIRST_RUN_DISCLAIMER.forEach { paragraph ->
            Text(text = paragraph, style = MaterialTheme.typography.bodyMedium)
          }
        }
      },
      confirmButton = { TextButton(onClick = onAccept) { Text("我已知悉并同意") } },
  )
}

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
