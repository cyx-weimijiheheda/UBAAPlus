package cn.edu.ubaa.plus

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程级前后台状态。
 *
 * 抢场地与抢课只在应用位于前台时运行。这里使用 [ProcessLifecycleOwner] 而不是 Activity 生命周期： 旋转屏幕或 Activity
 * 重建时进程始终处于前台，不会被误判为进入后台； 而按 Home 键、切换到其它应用会真正回调 ON_STOP。
 */
object AppForegroundMonitor {
  private val _foreground = MutableStateFlow(true)

  /** 当前进程是否位于前台。 */
  val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

  @Volatile private var started = false

  /** 必须在 Application.onCreate 中调用一次。 */
  fun start() {
    if (started) return
    synchronized(this) {
      if (started) return
      started = true
      ProcessLifecycleOwner.get()
          .lifecycle
          .addObserver(
              object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                  _foreground.value = true
                }

                override fun onStop(owner: LifecycleOwner) {
                  _foreground.value = false
                }
              }
          )
      _foreground.value =
          ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
  }
}
