package cn.edu.ubaa.plus.reminder

import android.content.Context
import cn.edu.ubaa.plus.ui.screens.signin.SigninNotice

/**
 * 课堂签到的结果通知。
 *
 * 签到是用户在当前页面主动点的，界面里已经有 Toast/对话框；这里额外落一条通知，方便切出应用后回看结果， 尤其是扫码签到失败时能明确看到原因。
 */
object SigninNotifications {
  fun notifyResult(context: Context, notice: SigninNotice) {
    ReminderNotifications.postNow(
        context = context,
        key = "signin:${notice.success}:${notice.courseName}",
        channel = ReminderNotifications.ATTENDANCE_CHANNEL,
        title = notice.title,
        text = notice.text,
        route = AppNotificationRoute.Home,
        succeeded = notice.success,
    )
  }
}
