package cn.edu.ubaa.plus.reminder

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 通知点击后的目标页面。 */
sealed interface AppNotificationRoute {
  /** 主界面（首页）。普通课程的签到提醒点击后直达这里。 */
  data object Home : AppNotificationRoute

  data object IclassHome : AppNotificationRoute

  data class BykcCourse(val courseId: Long) : AppNotificationRoute

  data class SpocAssignment(val assignmentId: String) : AppNotificationRoute

  data class JudgeAssignment(val courseId: String, val assignmentId: String) : AppNotificationRoute
}

object ReminderIntentExtras {
  const val EXTRA_ROUTE = "ubaa_reminder_route"
  const val EXTRA_COURSE_ID = "ubaa_reminder_course_id"
  const val EXTRA_ASSIGNMENT_ID = "ubaa_reminder_assignment_id"
  const val EXTRA_JUDGE_COURSE_ID = "ubaa_reminder_judge_course_id"

  const val ROUTE_HOME = "home"
  const val ROUTE_ICLASS = "iclass"
  const val ROUTE_BYKC = "bykc"
  const val ROUTE_SPOC = "spoc"
  const val ROUTE_JUDGE = "judge"

  fun putRoute(intent: Intent, route: AppNotificationRoute) {
    when (route) {
      AppNotificationRoute.Home -> intent.putExtra(EXTRA_ROUTE, ROUTE_HOME)
      AppNotificationRoute.IclassHome -> intent.putExtra(EXTRA_ROUTE, ROUTE_ICLASS)
      is AppNotificationRoute.BykcCourse -> {
        intent.putExtra(EXTRA_ROUTE, ROUTE_BYKC)
        intent.putExtra(EXTRA_COURSE_ID, route.courseId)
      }
      is AppNotificationRoute.SpocAssignment -> {
        intent.putExtra(EXTRA_ROUTE, ROUTE_SPOC)
        intent.putExtra(EXTRA_ASSIGNMENT_ID, route.assignmentId)
      }
      is AppNotificationRoute.JudgeAssignment -> {
        intent.putExtra(EXTRA_ROUTE, ROUTE_JUDGE)
        intent.putExtra(EXTRA_JUDGE_COURSE_ID, route.courseId)
        intent.putExtra(EXTRA_ASSIGNMENT_ID, route.assignmentId)
      }
    }
  }

  fun readRoute(intent: Intent?): AppNotificationRoute? {
    if (intent == null) return null
    return when (intent.getStringExtra(EXTRA_ROUTE)) {
      ROUTE_HOME -> AppNotificationRoute.Home
      ROUTE_ICLASS -> AppNotificationRoute.IclassHome
      ROUTE_BYKC ->
          intent
              .getLongExtra(EXTRA_COURSE_ID, -1L)
              .takeIf { it > 0 }
              ?.let(AppNotificationRoute::BykcCourse)
      ROUTE_SPOC ->
          intent
              .getStringExtra(EXTRA_ASSIGNMENT_ID)
              ?.takeIf { it.isNotBlank() }
              ?.let(AppNotificationRoute::SpocAssignment)
      ROUTE_JUDGE -> {
        val courseId = intent.getStringExtra(EXTRA_JUDGE_COURSE_ID)
        val assignmentId = intent.getStringExtra(EXTRA_ASSIGNMENT_ID)
        if (!courseId.isNullOrBlank() && !assignmentId.isNullOrBlank()) {
          AppNotificationRoute.JudgeAssignment(courseId, assignmentId)
        } else {
          null
        }
      }
      else -> null
    }
  }
}

object PendingAppRouteStore {
  private val _route = MutableStateFlow<AppNotificationRoute?>(null)
  val route: StateFlow<AppNotificationRoute?> = _route.asStateFlow()

  fun offer(route: AppNotificationRoute?) {
    if (route != null) _route.value = route
  }

  fun offerFromIntent(intent: Intent?) {
    offer(ReminderIntentExtras.readRoute(intent))
  }

  fun consume() {
    _route.value = null
  }
}
