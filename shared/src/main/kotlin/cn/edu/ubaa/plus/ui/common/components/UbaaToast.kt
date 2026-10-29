package cn.edu.ubaa.plus.ui.common.components

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** 全局 Toast 出口，供 ViewModel 或非 Compose 代码使用；未初始化时静默忽略。 */
object UbaaToast {
  @Volatile private var appContext: android.content.Context? = null

  fun initialize(context: android.content.Context) {
    appContext = context.applicationContext
  }

  fun show(message: String?, long: Boolean = true) {
    val text = message?.trim().orEmpty()
    if (text.isEmpty()) return
    val context = appContext ?: return
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
  }
}

/** 在页面里监听一次性错误消息并用系统 Toast 展示；展示后回调清除。 */
@Composable
fun UbaaToastEffect(message: String?, onShown: () -> Unit) {
  val context = LocalContext.current
  LaunchedEffect(message) {
    if (!message.isNullOrBlank()) {
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      onShown()
    }
  }
}
