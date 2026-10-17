package cn.edu.ubaa.plus.ui.screens.bykc

import cn.edu.ubaa.plus.AppForegroundMonitor
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.feature.BykcApi
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

enum class BykcGrabPhase {
  IDLE,
  WAITING,
  RUNNING,
  SUCCESS,
  STOPPED,
  FAILED,
}

enum class BykcGrabStopReason {
  USER,
  BACKGROUND,
  SUCCESS,
  FATAL,
  LOGOUT,
}

data class BykcGrabState(
    val phase: BykcGrabPhase = BykcGrabPhase.IDLE,
    val courseId: Long? = null,
    val courseName: String = "",
    val attempt: Int = 0,
    val message: String = "",
    val logs: List<String> = emptyList(),
    val stopReason: BykcGrabStopReason? = null,
)

/**
 * 博雅抢课控制器。
 *
 * 与运动场地一致的约束：只在应用处于前台时运行；离开详情页但应用仍在前台时继续运行。 选课接口没有验证码，因此循环相对简单：定时等待 → 反复调用 selectCourse →
 * 按错误类型重试或停止。
 */
internal class BykcGrabController(
    private val api: BykcApi,
    private val scope: CoroutineScope,
    private val foreground: StateFlow<Boolean> = AppForegroundMonitor.foreground,
    private val now: () -> kotlin.time.Instant = { Clock.System.now() },
) {
  private val _state = MutableStateFlow(BykcGrabState())
  val state: StateFlow<BykcGrabState> = _state.asStateFlow()

  private var job: Job? = null
  private var stopReason: BykcGrabStopReason? = null
  private val logs = ArrayDeque<String>()
  private var lastLogMessage: String? = null
  private var lastLogAt: kotlin.time.Instant? = null

  fun start(
      courseId: Long,
      courseName: String,
      scheduledStart: LocalDateTime?,
      retryInterval: Duration,
      selectEnd: LocalDateTime?,
      onSucceeded: () -> Unit,
  ) {
    if (job?.isActive == true) return
    stopReason = null
    logs.clear()
    lastLogMessage = null
    lastLogAt = null
    _state.value =
        BykcGrabState(
            phase =
                if (scheduledStart != null && scheduledStart.toInstant(zone()) > now()) {
                  BykcGrabPhase.WAITING
                } else {
                  BykcGrabPhase.RUNNING
                },
            courseId = courseId,
            courseName = courseName,
            message = "准备开始",
        )
    val newJob =
        scope.launch(start = CoroutineStart.LAZY) {
          runGrab(courseId, courseName, scheduledStart, retryInterval, selectEnd, onSucceeded)
        }
    job = newJob
    newJob.start()
  }

  fun stop(reason: BykcGrabStopReason = BykcGrabStopReason.USER) {
    stopReason = stopReason ?: reason
    job?.cancel()
  }

  fun reset() {
    stop(BykcGrabStopReason.USER)
    _state.value = BykcGrabState()
  }

  private suspend fun runGrab(
      courseId: Long,
      courseName: String,
      scheduledStart: LocalDateTime?,
      retryInterval: Duration,
      selectEnd: LocalDateTime?,
      onSucceeded: () -> Unit,
  ) {
    val selfJob = currentCoroutineContext()[Job]
    val watcher =
        scope.launch {
          foreground.collect { isForeground ->
            if (!isForeground && selfJob?.isActive == true) {
              stopReason = BykcGrabStopReason.BACKGROUND
              selfJob.cancel()
            }
          }
        }
    try {
      log("开始抢课：$courseName")

      if (scheduledStart != null) {
        val startInstant = scheduledStart.toInstant(zone())
        if (startInstant > now()) {
          _state.update { it.copy(phase = BykcGrabPhase.WAITING) }
          waitUntil(startInstant)
        }
      }

      var attempt = 0
      var unknownFailures = 0
      val grabStartedAt = now()
      while (true) {
        currentCoroutineContext().ensureActive()
        attempt++
        _state.update { it.copy(phase = BykcGrabPhase.RUNNING, attempt = attempt) }

        val result = api.selectCourse(courseId)
        if (result.isSuccess) {
          stopReason = BykcGrabStopReason.SUCCESS
          _state.update {
            it.copy(
                phase = BykcGrabPhase.SUCCESS,
                stopReason = BykcGrabStopReason.SUCCESS,
                message = "抢课成功",
            )
          }
          log("抢课成功（第 $attempt 次尝试）")
          runCatching { onSucceeded() }
          return
        }

        val error = result.exceptionOrNull()
        val code = (error as? ApiCallException)?.code
        val message = error?.message.orEmpty()
        when {
          code == "already_selected" -> {
            stopReason = BykcGrabStopReason.SUCCESS
            _state.update {
              it.copy(
                  phase = BykcGrabPhase.SUCCESS,
                  stopReason = BykcGrabStopReason.SUCCESS,
                  message = "已选上该课程",
              )
            }
            log("服务端返回已选，视为抢课成功")
            runCatching { onSucceeded() }
            return
          }
          code == "unauthenticated" || code == "invalid_token" -> {
            throw BykcGrabFatalException("登录状态已失效，请重新登录")
          }
          code == "course_full" || code == "select_failed" || code == "bykc_timeout" -> {
            logThrottled("暂时没抢到：$message，继续重试（第 $attempt 次）")
          }
          code == "course_not_selectable" -> {
            logThrottled("课程暂不可选，继续重试（第 $attempt 次）")
            if (now() - grabStartedAt > NOT_SELECTABLE_GRACE) {
              throw BykcGrabFatalException("课程持续不可选，已停止抢课")
            }
            if (selectEnd != null && now() > selectEnd.toInstant(zone())) {
              throw BykcGrabFatalException("选课已截止，已停止抢课")
            }
          }
          else -> {
            unknownFailures++
            logThrottled("抢课失败：${message.ifBlank { "网络异常" }}（第 $attempt 次）")
            if (unknownFailures >= MAX_UNKNOWN_FAILURES) {
              throw BykcGrabFatalException(message.ifBlank { "连续未知错误，已停止抢课" })
            }
          }
        }

        delay(retryInterval.coerceIn(200.milliseconds, 10.seconds))
      }
    } catch (cancelled: CancellationException) {
      if (stopReason == null) stopReason = BykcGrabStopReason.USER
    } catch (fatal: BykcGrabFatalException) {
      stopReason = BykcGrabStopReason.FATAL
      _state.update { it.copy(message = fatal.message.orEmpty()) }
      log(fatal.message.orEmpty())
    } catch (error: Exception) {
      stopReason = BykcGrabStopReason.FATAL
      _state.update { it.copy(message = error.message ?: "抢课失败") }
      log("抢课失败：${error.message}")
    } finally {
      watcher.cancel()
      // 旧任务的 finally 不应覆盖刚启动的新任务。
      if (job === selfJob) {
        job = null
        finalizeState()
      }
    }
  }

  private suspend fun waitUntil(target: kotlin.time.Instant) {
    var lastLoggedMinute = Long.MIN_VALUE
    while (true) {
      currentCoroutineContext().ensureActive()
      val remaining = target - now()
      if (remaining <= Duration.ZERO) return
      val minutes = remaining.inWholeMinutes
      if (minutes != lastLoggedMinute) {
        lastLoggedMinute = minutes
        log("距开抢还有 $minutes 分钟")
      }
      delay(
          when {
            remaining > 60.seconds -> (remaining - 3.seconds).coerceAtMost(30.seconds)
            remaining > 3.seconds -> 500.milliseconds
            else -> 1.milliseconds
          }
      )
    }
  }

  private fun finalizeState() {
    val reason = stopReason ?: BykcGrabStopReason.USER
    val (phase, message) =
        when (reason) {
          BykcGrabStopReason.SUCCESS -> return
          BykcGrabStopReason.BACKGROUND -> BykcGrabPhase.STOPPED to "应用进入后台，抢课已停止"
          BykcGrabStopReason.LOGOUT -> BykcGrabPhase.STOPPED to "已退出登录"
          BykcGrabStopReason.USER -> BykcGrabPhase.STOPPED to "已停止抢课"
          BykcGrabStopReason.FATAL -> BykcGrabPhase.FAILED to _state.value.message
        }
    _state.update { it.copy(phase = phase, stopReason = reason, message = message) }
  }

  private fun log(message: String) {
    if (message.isBlank()) return
    val timestamp = now()
    if (message == lastLogMessage && lastLogAt != null && timestamp - lastLogAt!! < 2.seconds) {
      return
    }
    lastLogMessage = message
    lastLogAt = timestamp
    logs.addLast(message)
    while (logs.size > MAX_LOG_LINES) logs.removeFirst()
    _state.update { it.copy(logs = logs.toList()) }
  }

  private fun logThrottled(message: String) = log(message)

  private fun zone(): TimeZone = TimeZone.currentSystemDefault()

  private companion object {
    const val MAX_LOG_LINES = 100
    const val MAX_UNKNOWN_FAILURES = 3
    val NOT_SELECTABLE_GRACE = 60.seconds
  }
}

private class BykcGrabFatalException(message: String) : Exception(message)
