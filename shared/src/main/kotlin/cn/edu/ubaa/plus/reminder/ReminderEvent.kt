package cn.edu.ubaa.plus.reminder

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDateTime

/** 一天里需要提醒的事件类型。 */
enum class ReminderEventKind {
  /** 课程即将开课。 */
  COURSE_OPEN,
  /** 普通课程进入签到窗口。 */
  ICLASS_SIGN_IN,
  /** 博雅课程进入签到窗口。 */
  BYKC_SIGN_IN,
  /** 博雅课程进入签退窗口。 */
  BYKC_SIGN_OUT,
}

/** 按课表生成的一条定时提醒。 */
data class ReminderEvent(
    val key: String,
    val kind: ReminderEventKind,
    val fireAt: LocalDateTime,
    val title: String,
    val text: String,
    val route: AppNotificationRoute,
    val courseName: String? = null,
)

/** 服务晚醒时仍然补发的时间窗口，超过就不再打扰。 */
fun ReminderEventKind.catchUpWindow(): Duration =
    when (this) {
      ReminderEventKind.COURSE_OPEN -> 5.minutes
      ReminderEventKind.ICLASS_SIGN_IN,
      ReminderEventKind.BYKC_SIGN_IN,
      ReminderEventKind.BYKC_SIGN_OUT -> 20.minutes
    }
