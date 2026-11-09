package cn.edu.ubaa.plus.reminder

import android.content.Context
import cn.edu.ubaa.plus.api.feature.BykcApi
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.repository.ScheduleStore
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * 一次性的提醒检查。
 *
 * 由 [ReminderAlarmReceiver] 触发，跑完即止，不常驻、不占用前台服务，因此应用不会为了提醒而挂一条通知在状态栏。
 */
object ReminderCheckWorker {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val engine by lazy { ReminderPollEngine() }
  private val verifier by lazy { ReminderSigninVerifier() }
  private val bykcApi by lazy { BykcApi() }

  /** 触发一次检查；不阻塞调用方。 */
  fun runOnce(context: Context) {
    val appContext = context.applicationContext
    scope.launch { runCatching { check(appContext) } }
  }

  private suspend fun check(context: Context) =
      withContext(Dispatchers.IO) {
        val userKey = LocalAuthSessionStore.get()?.username
        if (userKey.isNullOrBlank()) {
          ReminderScheduler.cancel(context)
          return@withContext
        }

        val nowInstant = Clock.System.now()
        val now = nowInstant.toLocalDateTime(ReminderScheduleBuilder.zone)

        val bykcCache =
            runCatching { bykcApi.getChosenCourses() }.getOrNull()?.getOrNull().orEmpty()

        val semesters =
            runCatching { ScheduleStore.account()?.let { ScheduleStore.read(it) } }
                .getOrNull()
                .orEmpty()
        val events =
            ReminderScheduleBuilder.merge(
                ReminderScheduleBuilder.timetableEvents(semesters, now),
                ReminderScheduleBuilder.bykcEvents(bykcCache, now),
            )
        events.forEach { event -> fireIfDue(context, userKey, event, nowInstant) }

        // 作业只能在轮询里发现，每次醒来顺手查一遍。
        runCatching {
              engine.collectHomework(userKey, now).forEach { candidate ->
                fireIfDue(context, userKey, candidate.toEvent(now), nowInstant)
              }
            }
            .onFailure { /* 作业接口失败不影响课程提醒 */ }
      }

  /** 到点且落在补发窗口内才发；已签到或不可签到的课程直接记为已处理。 */
  private suspend fun fireIfDue(
      context: Context,
      userKey: String,
      event: ReminderEvent,
      nowInstant: Instant,
  ) {
    val elapsed = nowInstant - event.fireAt.toInstant(ReminderScheduleBuilder.zone)
    if (elapsed < Duration.ZERO || elapsed > event.kind.catchUpWindow()) return
    val decision =
        runCatching { verifier.decide(event) }.getOrDefault(ReminderSigninDecision.NOTIFY)
    if (decision != ReminderSigninDecision.NOTIFY) {
      ReminderSeenStore.markIfNew(userKey, event.key)
      return
    }
    if (!ReminderSeenStore.markIfNew(userKey, event.key)) return
    when (event.kind) {
      ReminderEventKind.NEW_ASSIGNMENT,
      ReminderEventKind.ASSIGNMENT_DUE_SOON ->
          ReminderNotifications.notifyHomework(
              context = context,
              key = event.key,
              title = event.title,
              text = event.text,
              route = event.route,
          )
      else ->
          ReminderNotifications.notifyCourse(
              context = context,
              key = event.key,
              title = event.title,
              text = event.text,
              route = event.route,
          )
    }
  }
}
