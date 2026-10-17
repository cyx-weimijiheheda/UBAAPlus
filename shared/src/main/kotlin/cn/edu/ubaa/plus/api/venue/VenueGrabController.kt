package cn.edu.ubaa.plus.api.venue

import cn.edu.ubaa.plus.AppForegroundMonitor
import cn.edu.ubaa.plus.api.auth.ApiCallException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

enum class VenueGrabPhase {
  IDLE,
  WAITING,
  RUNNING,
  NEEDS_CAPTCHA,
  SUCCESS,
  STOPPED,
  FAILED,
}

enum class VenueGrabStopReason {
  USER,
  BACKGROUND,
  SUCCESS,
  FATAL,
  CAPTCHA_CANCELLED,
  LOGOUT,
}

/** 用户在验证码图片上点击后回传的答案。 */
data class VenueCaptchaAnswer(
    val points: List<VenuePoint>,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class VenueGrabState(
    val phase: VenueGrabPhase = VenueGrabPhase.IDLE,
    val attempt: Int = 0,
    val message: String = "",
    val logs: List<String> = emptyList(),
    val pendingCaptcha: VenueCaptchaChallenge? = null,
    val success: VenueOrderResult? = null,
    val payUrl: String? = null,
    val stopReason: VenueGrabStopReason? = null,
)

/**
 * 运动场地抢单控制器。
 *
 * 任务运行在传入的 [scope]（通常是 ViewModel 的 viewModelScope）里：离开页面不会中断， 只有用户停止、注销、切换连接模式或应用进入后台才会停止。
 */
internal class VenueGrabController(
    private val api: VenueApi,
    private val scope: CoroutineScope,
    private val foreground: StateFlow<Boolean> = AppForegroundMonitor.foreground,
    private val now: () -> kotlin.time.Instant = { Clock.System.now() },
    private val random: Random = Random.Default,
) {
  private val _state = MutableStateFlow(VenueGrabState())
  val state: StateFlow<VenueGrabState> = _state.asStateFlow()

  private var job: Job? = null
  private var stopReason: VenueGrabStopReason? = null
  private var captchaDeferred: CompletableDeferred<VenueCaptchaAnswer?>? = null
  private var cachedCaptcha: VenueCaptchaResult? = null
  private val logs = ArrayDeque<String>()
  private var lastLogMessage: String? = null
  private var lastLogAt: kotlin.time.Instant? = null

  fun start(task: VenueGrabTask) {
    if (job?.isActive == true) return
    stopReason = null
    cachedCaptcha = null
    lastLogMessage = null
    lastLogAt = null
    logs.clear()
    _state.value =
        VenueGrabState(
            phase = if (task.grabAt != null) VenueGrabPhase.WAITING else VenueGrabPhase.RUNNING,
            message = "准备开始",
        )
    val newJob = scope.launch(start = CoroutineStart.LAZY) { runGrab(task) }
    job = newJob
    newJob.start()
  }

  fun stop(reason: VenueGrabStopReason = VenueGrabStopReason.USER) {
    stopReason = stopReason ?: reason
    captchaDeferred?.complete(null)
    job?.cancel()
  }

  fun submitCaptcha(answer: VenueCaptchaAnswer) {
    captchaDeferred?.complete(answer)
  }

  fun cancelCaptcha() {
    captchaDeferred?.complete(null)
  }

  fun reset() {
    stop(VenueGrabStopReason.USER)
    _state.value = VenueGrabState()
  }

  private suspend fun runGrab(task: VenueGrabTask) {
    val selfJob = currentCoroutineContext()[Job]
    val watcher =
        scope.launch {
          foreground.collect { isForeground ->
            if (!isForeground && selfJob?.isActive == true) {
              stopReason = VenueGrabStopReason.BACKGROUND
              selfJob.cancel()
            }
          }
        }
    try {
      executeTask(task)
      if (stopReason == null) stopReason = VenueGrabStopReason.SUCCESS
    } catch (cancelled: CancellationException) {
      if (stopReason == null) stopReason = VenueGrabStopReason.USER
    } catch (fatal: VenueGrabFatalException) {
      stopReason = VenueGrabStopReason.FATAL
      _state.update { it.copy(message = fatal.message.orEmpty()) }
      log(fatal.message.orEmpty())
    } catch (error: Exception) {
      stopReason = VenueGrabStopReason.FATAL
      _state.update { it.copy(message = error.message ?: "抢场地失败") }
      log("抢场地失败：${error.message}")
    } finally {
      watcher.cancel()
      captchaDeferred?.complete(null)
      captchaDeferred = null
      // 只有在当前任务仍是活动任务时才收尾，避免旧任务的 finally 覆盖刚启动的新任务。
      if (job === selfJob) {
        job = null
        finalizeState()
      }
    }
  }

  private suspend fun executeTask(task: VenueGrabTask) {
    log("开始抢场地：${task.date} ${task.startBeginTime} · ${task.durationHours} 小时")

    val preflight = api.getDayInfo(task.siteId, task.date)
    preflight
        .onSuccess { day ->
          log(if (day == null) "该日期尚未放票，将持续重试" else "该日期已放票，共 ${day.spaces.size} 个场地")
        }
        .onFailure { log("预检失败：${it.message}") }

    if (task.grabAt != null) {
      _state.update { it.copy(phase = VenueGrabPhase.WAITING) }
      waitUntil(task.grabAt)
    }

    var attempt = 0
    var reloginDone = false
    while (true) {
      currentCoroutineContext().ensureActive()
      attempt++
      _state.update { it.copy(phase = VenueGrabPhase.RUNNING, attempt = attempt) }

      val dayResult = api.getDayInfo(task.siteId, task.date)
      val day = dayResult.getOrNull()
      if (day == null) {
        if (dayResult.isFailure) {
          val action = handleFailure(dayResult.exceptionOrNull(), reloginDone)
          if (action == VenueFailureAction.RELOGIN) reloginDone = true
          if (!resolveAction(action, task.pollInterval)) {
            throw VenueGrabFatalException(dayResult.exceptionOrNull()?.message ?: "获取场地信息失败")
          }
        } else {
          logThrottled("尚未放票，等待中（第 $attempt 次）")
          delay(task.pollInterval)
        }
        continue
      }

      val targetSlots = resolveVenueTargetSlots(day.slots, task.startBeginTime, task.durationHours)
      if (targetSlots.isEmpty()) {
        logThrottled("未找到目标时段 ${task.startBeginTime}，重试中")
        delay(task.pollInterval)
        continue
      }

      val selections =
          pickVenueSelection(
              dayInfo = day,
              targetSlots = targetSlots,
              preferredSpaceByBegin = task.preferredSpaceByBegin,
              fallbackAnySpace = task.fallbackAnySpace,
              randomSpace = task.randomSpace,
              random = random,
          )
      if (selections == null) {
        logThrottled("目标时段暂无空闲场地，等待空位（第 $attempt 次）")
        delay(task.pollInterval)
        continue
      }

      val captcha = if (day.isCaptchaCheck) ensureCaptcha() else null
      val price = totalFee(day, selections)
      val request =
          VenueOrderRequest(
              siteId = task.siteId,
              date = task.date,
              selections = selections,
              phone = day.phone?.takeIf { it.isNotBlank() } ?: task.phone,
              buddyIds = task.buddyIds,
              orderPrice = price,
              captchaVerification = captcha?.verification,
              captchaToken = captcha?.captchaToken,
              orderToken = day.orderToken.takeIf { it.isNotBlank() },
          )

      val result = api.submitOrder(request)
      val order = result.getOrNull()
      if (order != null) {
        val payUrl = payIfNeeded(order, price)
        stopReason = VenueGrabStopReason.SUCCESS
        _state.update {
          it.copy(
              phase = VenueGrabPhase.SUCCESS,
              success = order,
              payUrl = payUrl,
              message = "预约成功",
              stopReason = VenueGrabStopReason.SUCCESS,
          )
        }
        log("预约成功：${order.tradeNo ?: order.orderId ?: ""}")
        return
      }

      val action = handleFailure(result.exceptionOrNull(), reloginDone)
      if (action == VenueFailureAction.RELOGIN) reloginDone = true
      if (!resolveAction(action, task.pollInterval)) {
        throw VenueGrabFatalException(result.exceptionOrNull()?.message ?: "预约失败，已停止")
      }
    }
  }

  private suspend fun ensureCaptcha(): VenueCaptchaResult {
    cachedCaptcha?.let {
      return it
    }
    val challenge =
        api.getCaptcha().getOrElse { throw VenueGrabFatalException("验证码加载失败：${it.message}") }
    val deferred = CompletableDeferred<VenueCaptchaAnswer?>()
    captchaDeferred = deferred
    _state.update {
      it.copy(phase = VenueGrabPhase.NEEDS_CAPTCHA, pendingCaptcha = challenge, message = "请完成验证码")
    }
    log("需要人工完成点击文字验证码（${challenge.wordList.size} 个字符）")

    val answer = deferred.await()
    captchaDeferred = null
    _state.update { it.copy(phase = VenueGrabPhase.RUNNING, pendingCaptcha = null) }
    if (answer == null) {
      if (stopReason == null) stopReason = VenueGrabStopReason.CAPTCHA_CANCELLED
      throw CancellationException("验证码已取消")
    }

    val verified =
        api.verifyCaptcha(challenge, answer.points, answer.imageWidth, answer.imageHeight)
            .getOrElse {
              cachedCaptcha = null
              throw VenueGrabFatalException("验证码校验失败：${it.message}")
            }
    cachedCaptcha = verified
    return verified
  }

  private suspend fun payIfNeeded(order: VenueOrderResult, price: Double?): String? {
    val tradeNo = order.tradeNo?.takeIf { it.isNotBlank() } ?: return null
    if ((price ?: 0.0) <= 0.0) return null
    return api.pay(tradeNo)
        .fold(
            onSuccess = {
              if (!it.payUrl.isNullOrBlank()) log("已生成支付链接，请手动完成支付")
              it.payUrl
            },
            onFailure = {
              log("生成支付链接失败：${it.message}")
              null
            },
        )
  }

  private suspend fun waitUntil(grabAt: LocalTime) {
    val zone = TimeZone.currentSystemDefault()
    val nowLocal = now().toLocalDateTime(zone)
    var target = LocalDateTime(nowLocal.date, grabAt)
    if (target <= nowLocal) {
      target = LocalDateTime(nowLocal.date.plus(1, DateTimeUnit.DAY), grabAt)
    }
    log("定时开抢：${target.date} ${target.time}")

    var lastLogMinutes = Long.MIN_VALUE
    var lastKeepalive = now()
    while (true) {
      currentCoroutineContext().ensureActive()
      val remaining = target.toInstant(zone) - now()
      if (remaining <= Duration.ZERO) return

      val minutes = remaining.inWholeMinutes
      if (minutes != lastLogMinutes) {
        lastLogMinutes = minutes
        log("距开抢还有 $minutes 分钟")
      }

      if (now() - lastKeepalive >= 10.minutes) {
        lastKeepalive = now()
        log("保持登录状态")
        runCatching { api.resumeSession() }
      }

      val sleep =
          when {
            remaining > 60.seconds -> (remaining - 3.seconds).coerceAtMost(30.seconds)
            remaining > 3.seconds -> 500.milliseconds
            else -> 1.milliseconds
          }
      delay(sleep)
    }
  }

  private fun handleFailure(error: Throwable?, reloginDone: Boolean): VenueFailureAction {
    val message = error?.message
    val code = (error as? ApiCallException)?.code
    return when (classifyVenueFailure(message, code)) {
      VenueFailureKind.RETRY -> {
        logThrottled("重试：${message.orEmpty()}")
        VenueFailureAction.RETRY
      }
      VenueFailureKind.NOT_RELEASED -> {
        logThrottled("尚未放票，继续等待")
        VenueFailureAction.RETRY
      }
      VenueFailureKind.CAPTCHA_INVALID -> {
        cachedCaptcha = null
        logThrottled("验证码失效，将重新获取")
        VenueFailureAction.RETRY
      }
      VenueFailureKind.CAPTCHA_RATE_LIMITED -> {
        cachedCaptcha = null
        logThrottled("验证码尝试次数超出，冷却 10 秒")
        VenueFailureAction.COOLDOWN
      }
      VenueFailureKind.AUTH_EXPIRED ->
          if (reloginDone) VenueFailureAction.STOP else VenueFailureAction.RELOGIN
      VenueFailureKind.STOP -> VenueFailureAction.STOP
    }
  }

  private suspend fun resolveAction(action: VenueFailureAction, pollInterval: Duration): Boolean =
      when (action) {
        VenueFailureAction.RETRY -> {
          delay(pollInterval)
          true
        }
        VenueFailureAction.COOLDOWN -> {
          delay(10.seconds)
          true
        }
        VenueFailureAction.RELOGIN -> {
          api.logout()
          log("登录状态失效，尝试重新建立会话")
          val outcome = api.resumeSession()
          if (outcome is VenueAuthOutcome.Ready) {
            true
          } else {
            log("重新登录失败，请返回运动场地页面重新登录")
            false
          }
        }
        VenueFailureAction.STOP -> false
      }

  private fun totalFee(day: VenueDayInfo, selections: List<VenueSelection>): Double? {
    val total =
        selections.sumOf { selection ->
          day.spaces.firstOrNull { it.spaceId == selection.spaceId }?.slot(selection.timeId)?.fee
              ?: 0.0
        }
    return total.takeIf { it > 0.0 }
  }

  private fun finalizeState() {
    val reason = stopReason ?: VenueGrabStopReason.USER
    when (reason) {
      VenueGrabStopReason.SUCCESS -> Unit
      VenueGrabStopReason.BACKGROUND ->
          _state.update {
            it.copy(
                phase = VenueGrabPhase.STOPPED,
                stopReason = VenueGrabStopReason.BACKGROUND,
                pendingCaptcha = null,
                message = "应用进入后台，抢场地已停止",
            )
          }
      VenueGrabStopReason.CAPTCHA_CANCELLED ->
          _state.update {
            it.copy(
                phase = VenueGrabPhase.STOPPED,
                stopReason = VenueGrabStopReason.CAPTCHA_CANCELLED,
                pendingCaptcha = null,
                message = "验证码已取消，抢场地已停止",
            )
          }
      VenueGrabStopReason.LOGOUT ->
          _state.update {
            it.copy(
                phase = VenueGrabPhase.STOPPED,
                stopReason = VenueGrabStopReason.LOGOUT,
                pendingCaptcha = null,
                message = "已退出登录",
            )
          }
      VenueGrabStopReason.USER ->
          _state.update {
            it.copy(
                phase = VenueGrabPhase.STOPPED,
                stopReason = VenueGrabStopReason.USER,
                pendingCaptcha = null,
                message = "已停止抢场地",
            )
          }
      VenueGrabStopReason.FATAL ->
          _state.update { it.copy(phase = VenueGrabPhase.FAILED, pendingCaptcha = null) }
    }
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

  private fun logThrottled(message: String) {
    log(message)
  }

  private companion object {
    const val MAX_LOG_LINES = 200
  }
}

private enum class VenueFailureAction {
  RETRY,
  COOLDOWN,
  RELOGIN,
  STOP,
}

private class VenueGrabFatalException(message: String) : Exception(message)
