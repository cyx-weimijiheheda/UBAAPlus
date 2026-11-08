package cn.edu.ubaa.plus.reminder

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import cn.edu.ubaa.plus.api.feature.BykcApi
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.model.dto.BykcChosenCourseDto
import cn.edu.ubaa.plus.repository.ScheduleStore
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 登录态下的后台提醒服务。
 *
 * 通知只在“确实有事要做”的时候发出：课程开课、签到/签退窗口开启、新作业发布、作业即将截止。 服务启动只拉起常驻通知与定时循环，不会为了证明自己在跑而推送打扰。
 *
 * 课程事件按本地课表生成定点时刻，睡到下一个事件再醒；作业与新作业只能靠轮询，按较短节奏检查并首次见到的 key 才算“新”。
 */
class ReminderForegroundService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var loopJob: Job? = null
  private val engine by lazy { ReminderPollEngine() }
  private val verifier by lazy { ReminderSigninVerifier() }
  private val bykcApi by lazy { BykcApi() }

  override fun onCreate() {
    super.onCreate()
    ReminderNotifications.ensureChannels(this)
    val notification = ReminderNotifications.buildServiceNotification(this)
    if (Build.VERSION.SDK_INT >= 34) {
      startForeground(
          ReminderNotifications.SERVICE_NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(ReminderNotifications.SERVICE_NOTIFICATION_ID, notification)
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (LocalAuthSessionStore.get() == null) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (loopJob == null) {
      loopJob = scope.launch { runLoop() }
    }
    return START_STICKY
  }

  private suspend fun runLoop() {
    var bykcCache = emptyList<BykcChosenCourseDto>()
    var lastBykcRefresh: Instant? = null
    var lastHomeworkCheck: Instant? = null

    while (scope.isActive) {
      val userKey = LocalAuthSessionStore.get()?.username
      if (userKey.isNullOrBlank()) {
        stopSelf()
        return
      }

      val nowInstant = Clock.System.now()
      val now = nowInstant.toLocalDateTime(ReminderScheduleBuilder.zone)
      val zone = ReminderScheduleBuilder.zone

      val needsBykcRefresh =
          lastBykcRefresh?.let { nowInstant - it >= BYKC_REFRESH_INTERVAL } ?: true
      if (needsBykcRefresh) {
        runCatching { bykcApi.getChosenCourses() }.getOrNull()?.getOrNull()?.let { bykcCache = it }
        lastBykcRefresh = nowInstant
      }

      val semesters =
          runCatching { ScheduleStore.account()?.let { ScheduleStore.read(it) } }
              .getOrNull()
              .orEmpty()
      val events =
          ReminderScheduleBuilder.merge(
              ReminderScheduleBuilder.timetableEvents(semesters, now),
              ReminderScheduleBuilder.bykcEvents(bykcCache, now),
          )

      events.forEach { event -> fireIfDue(userKey, event, nowInstant) }

      val needsHomeworkCheck =
          lastHomeworkCheck?.let { nowInstant - it >= HOMEWORK_INTERVAL } ?: true
      if (needsHomeworkCheck) {
        pollHomework(userKey, nowInstant)
        lastHomeworkCheck = nowInstant
      }

      val nextFireAt =
          events.mapNotNull { it.fireAt.toInstant(zone) }.filter { it > nowInstant }.minOrNull()
      val untilNext = nextFireAt?.let { it - nowInstant } ?: MAX_SLEEP
      val sleep = untilNext.coerceAtMost(MAX_SLEEP).coerceAtLeast(1.seconds)
      delay(sleep)
    }
  }

  /** 到点且落在补发窗口内才发；已签到的课程直接记为已处理，不再打扰。 */
  private suspend fun fireIfDue(userKey: String, event: ReminderEvent, nowInstant: Instant) {
    val elapsed = nowInstant - event.fireAt.toInstant(ReminderScheduleBuilder.zone)
    if (elapsed < Duration.ZERO || elapsed > event.kind.catchUpWindow()) return
    val shouldNotify = runCatching { verifier.shouldNotify(event) }.getOrDefault(true)
    if (!shouldNotify) {
      ReminderSeenStore.markIfNew(userKey, event.key)
      return
    }
    if (!ReminderSeenStore.markIfNew(userKey, event.key)) return
    when (event.kind) {
      ReminderEventKind.NEW_ASSIGNMENT,
      ReminderEventKind.ASSIGNMENT_DUE_SOON ->
          ReminderNotifications.notifyHomework(
              context = this,
              key = event.key,
              title = event.title,
              text = event.text,
              route = event.route,
          )
      else ->
          ReminderNotifications.notifyCourse(
              context = this,
              key = event.key,
              title = event.title,
              text = event.text,
              route = event.route,
          )
    }
  }

  /**
   * 作业轮询。
   *
   * 首次运行时 [ReminderPollEngine] 只建立基线（seed），所以刚启动不会把历史作业全推一遍；之后每一轮新出现的作业、 刚进入 24 小时截止窗口的作业
   * 都会走和课程事件同一套去重逻辑。
   */
  private suspend fun pollHomework(userKey: String, nowInstant: Instant) {
    val now = nowInstant.toLocalDateTime(ReminderScheduleBuilder.zone)
    runCatching {
          engine.collectHomework(userKey, now).forEach { candidate ->
            fireIfDue(userKey, candidate.toEvent(now), nowInstant)
          }
        }
        .onFailure { /* 作业接口失败不影响下一次课程提醒 */ }
  }

  override fun onDestroy() {
    loopJob?.cancel()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private companion object {
    val BYKC_REFRESH_INTERVAL = 15.minutes
    val HOMEWORK_INTERVAL = 30.minutes
    val MAX_SLEEP = 15.minutes
  }
}
