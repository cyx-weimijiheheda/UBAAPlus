package cn.edu.ubaa.plus.ui.common.util

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 返回键处理，委托给 androidx 的 OnBackPressedDispatcher。 */
@Composable
fun BackHandlerCompat(enabled: Boolean = true, onBack: () -> Unit) {
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentOnBack by rememberUpdatedState(onBack)

  val backCallback = remember {
    object : OnBackPressedCallback(enabled) {
      override fun handleOnBackPressed() {
        currentOnBack()
      }
    }
  }

  SideEffect { backCallback.isEnabled = enabled }

  DisposableEffect(lifecycleOwner, backDispatcher) {
    backDispatcher?.addCallback(lifecycleOwner, backCallback)
    onDispose { backCallback.remove() }
  }
}
