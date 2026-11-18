package cn.edu.ubaa.plus.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** 扫码结果对话框：打开浏览器或复制内容。 */
@Composable
fun QrScanResultDialog(
    content: String,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  val isUrl = content.trim().startsWith("http://") || content.trim().startsWith("https://")

  UbaaAlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("扫描结果") },
      text = {
        Box(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
          Text(content)
        }
      },
      confirmButton = {
        UbaaTextButton(
            onClick = { onOpenUrl(content.trim()) },
            enabled = isUrl,
        ) {
          Text("在浏览器打开")
        }
      },
      dismissButton = {
        UbaaIconAction(
            onClick = {
              clipboard.setText(AnnotatedString(content))
              onDismiss()
            },
            description = "复制",
            icon = Icons.Default.ContentCopy,
        )
      },
  )
}
