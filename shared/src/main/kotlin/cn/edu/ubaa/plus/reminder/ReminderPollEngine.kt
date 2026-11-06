package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.api.feature.JudgeApi
import cn.edu.ubaa.plus.api.feature.SpocApi
import cn.edu.ubaa.plus.model.dto.JudgeSubmissionStatus
import cn.edu.ubaa.plus.model.dto.SpocSubmissionStatus
import cn.edu.ubaa.plus.model.format.formatUbaaDateTime
import cn.edu.ubaa.plus.model.format.parseUbaaDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class ReminderCandidate(
    val key: String,
    val title: String,
    val text: String,
    val route: AppNotificationRoute,
)

/** 作业提醒：新作业发布 + 截止前 24 小时；课程提醒由 [ReminderScheduleBuilder] 按课表生成。 */
@OptIn(ExperimentalTime::class)
class ReminderPollEngine(
    private val spocApi: SpocApi = SpocApi(),
    private val judgeApi: JudgeApi = JudgeApi(),
    private val nowProvider: () -> LocalDateTime = {
      Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    },
) {
  /** 作业轮询；首次运行只建立基线，不把历史作业全部推一遍。 */
  suspend fun collectHomework(
      userKey: String,
      now: LocalDateTime = nowProvider(),
  ): List<ReminderCandidate> {
    val out = mutableListOf<ReminderCandidate>()
    collectSpoc(userKey, now, out)
    collectJudge(userKey, now, out)
    return out
  }

  private suspend fun collectSpoc(
      userKey: String,
      now: LocalDateTime,
      out: MutableList<ReminderCandidate>,
  ) {
    val response = runCatching { spocApi.getAssignments() }.getOrNull()?.getOrNull() ?: return
    val assignments = response.assignments
    val seeded = ReminderSeenStore.isSourceSeeded(userKey, "spoc")
    if (!seeded) {
      ReminderSeenStore.seedSource(
          userKey,
          "spoc",
          assignments.map { "spoc-new:${it.assignmentId}" },
      )
    }
    assignments.forEach { assignment ->
      if (assignment.submissionStatus != SpocSubmissionStatus.UNSUBMITTED) return@forEach
      val due = parseUbaaDateTime(assignment.dueTime) ?: return@forEach
      val newKey = "spoc-new:${assignment.assignmentId}"
      if (seeded && !ReminderSeenStore.isSeen(userKey, newKey)) {
        out +=
            ReminderCandidate(
                key = newKey,
                title = "新作业：${assignment.courseName}",
                text = assignment.title,
                route = AppNotificationRoute.SpocAssignment(assignment.assignmentId),
            )
      }
      if (
          due > now &&
              due.toInstant(TimeZone.currentSystemDefault()) -
                  now.toInstant(TimeZone.currentSystemDefault()) <= 24.hours
      ) {
        out +=
            ReminderCandidate(
                key = "spoc-due24:${assignment.assignmentId}",
                title = "作业即将截止：${assignment.title}",
                text = "${assignment.courseName} · 截止 ${formatUbaaDateTime(assignment.dueTime)}",
                route = AppNotificationRoute.SpocAssignment(assignment.assignmentId),
            )
      }
    }
  }

  private suspend fun collectJudge(
      userKey: String,
      now: LocalDateTime,
      out: MutableList<ReminderCandidate>,
  ) {
    val response =
        runCatching { judgeApi.getAssignments(includeExpired = false) }.getOrNull()?.getOrNull()
            ?: return
    val assignments = response.assignments
    val seeded = ReminderSeenStore.isSourceSeeded(userKey, "judge")
    if (!seeded) {
      ReminderSeenStore.seedSource(
          userKey,
          "judge",
          assignments.map { "judge-new:${it.courseId}:${it.assignmentId}" },
      )
    }
    assignments.forEach { assignment ->
      if (
          assignment.submissionStatus != JudgeSubmissionStatus.UNSUBMITTED &&
              assignment.submissionStatus != JudgeSubmissionStatus.PARTIAL
      ) {
        return@forEach
      }
      val due = parseUbaaDateTime(assignment.dueTime) ?: return@forEach
      val newKey = "judge-new:${assignment.courseId}:${assignment.assignmentId}"
      if (seeded && !ReminderSeenStore.isSeen(userKey, newKey)) {
        out +=
            ReminderCandidate(
                key = newKey,
                title = "新作业：${assignment.courseName}",
                text = assignment.title,
                route =
                    AppNotificationRoute.JudgeAssignment(
                        assignment.courseId,
                        assignment.assignmentId,
                    ),
            )
      }
      if (
          due > now &&
              due.toInstant(TimeZone.currentSystemDefault()) -
                  now.toInstant(TimeZone.currentSystemDefault()) <= 24.hours
      ) {
        out +=
            ReminderCandidate(
                key = "judge-due24:${assignment.courseId}:${assignment.assignmentId}",
                title = "作业即将截止：${assignment.title}",
                text = "${assignment.courseName} · 截止 ${formatUbaaDateTime(assignment.dueTime)}",
                route =
                    AppNotificationRoute.JudgeAssignment(
                        assignment.courseId,
                        assignment.assignmentId,
                    ),
            )
      }
    }
  }
}
