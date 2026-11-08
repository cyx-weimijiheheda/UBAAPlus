package cn.edu.ubaa.plus.reminder

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDateTime

/**
 * 一天里需要提醒的事件类型。
 *
 * 只有“确实有事要做”的时刻才发通知：课程开课、签到/签退窗口开启、新作业发布、作业即将截止。 启动服务本身不发通知，也不用“后台检测中”之类的提示代替。
 */
enum class ReminderEventKind {
  /** 课程即将开课。 */
  COURSE_OPEN,
  /** 普通课程进入签到窗口。 */
  ICLASS_SIGN_IN,
  /** 博雅课程进入签到窗口。 */
  BYKC_SIGN_IN,
  /** 博雅课程进入签退窗口。 */
  BYKC_SIGN_OUT,
  /** 新作业发布。 */
  NEW_ASSIGNMENT,
  /** 作业截止前 24 小时。 */
  ASSIGNMENT_DUE_SOON,
  /** 图书馆预约即将超过签到时限。 */
  LIBBOOK_CHECK_IN,
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
      // 作业类事件按轮询是否“首次见到”判定，不走补发窗口。
      ReminderEventKind.NEW_ASSIGNMENT,
      ReminderEventKind.ASSIGNMENT_DUE_SOON,
      ReminderEventKind.LIBBOOK_CHECK_IN -> Duration.ZERO
    }
