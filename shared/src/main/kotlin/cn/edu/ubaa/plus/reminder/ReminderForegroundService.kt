package cn.edu.ubaa.plus.reminder

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import cn.edu.ubaa.plus.api.feature.BykcApi
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
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
 * 不做“每 5 分钟查一次签到”的轮询：每次醒来按本地课表生成当天/明天的定点事件， 睡到下一个事件时刻再发通知；只有作业仍按 30 分钟节奏检查。
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
    var bykcCache = emptyList<cn.edu.ubaa.plus.model.dto.BykcChosenCourseDto>()
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

      events.forEach { event ->
        val elapsed = nowInstant - event.fireAt.toInstant(zone)
        if (elapsed < Duration.ZERO || elapsed > event.kind.catchUpWindow()) return@forEach
        val shouldNotify = runCatching { verifier.shouldNotify(event) }.getOrDefault(true)
        if (!shouldNotify) {
          ReminderSeenStore.markIfNew(userKey, event.key)
          return@forEach
        }
        if (ReminderSeenStore.markIfNew(userKey, event.key)) {
          ReminderNotifications.notifyCourse(
              context = this,
              key = event.key,
              title = event.title,
              text = event.text,
              route = event.route,
          )
        }
      }

      val needsHomeworkCheck =
          lastHomeworkCheck?.let { nowInstant - it >= HOMEWORK_INTERVAL } ?: true
      if (needsHomeworkCheck) {
        pollHomework(userKey)
        lastHomeworkCheck = nowInstant
      }

      val nextFireAt =
          events.mapNotNull { it.fireAt.toInstant(zone) }.filter { it > nowInstant }.minOrNull()
      val untilNext = nextFireAt?.let { it - nowInstant } ?: MAX_SLEEP
      val sleep = untilNext.coerceAtMost(MAX_SLEEP).coerceAtLeast(1.seconds)
      delay(sleep)
    }
  }

  private suspend fun pollHomework(userKey: String) {
    runCatching {
          engine.collectHomework(userKey).forEach { candidate ->
            if (ReminderSeenStore.markIfNew(userKey, candidate.key)) {
              ReminderNotifications.notifyHomework(
                  context = this,
                  key = candidate.key,
                  title = candidate.title,
                  text = candidate.text,
                  route = candidate.route,
              )
            }
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
