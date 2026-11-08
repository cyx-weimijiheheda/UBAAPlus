package cn.edu.ubaa.plus.reminder

import android.content.Context
import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.format.formatUbaaDateWithTimeRange
import cn.edu.ubaa.plus.ui.screens.libbook.LibBookNotice

/**
 * 图书馆座位操作结果通知。
 *
 * 预约、签到、暂离、签退、取消都由用户在前台触发，系统已经用 Toast/对话框给出即时反馈；这里额外落一条 通知栏记录，便于离开应用后回看结果。不依赖
 * [ReminderForegroundService]，关掉后台提醒也能收到。
 */
object LibBookNotifications {
  /** 座位操作的结果，决定通知标题与点击后的目标页面。 */
  enum class Outcome(val succeeded: Boolean) {
    RESERVED(true),
    SIGNED_IN(true),
    RETURNED(true),
    LEFT(true),
    CHECKED_OUT(true),
    CANCELED(true),
    RESERVE_FAILED(false),
    SIGN_IN_FAILED(false),
    RETURN_FAILED(false),
    LEAVE_FAILED(false),
    CHECKOUT_FAILED(false),
    CANCEL_FAILED(false),
  }

  /** ViewModel 侧的入口：直接消费一次操作结果。 */
  fun notifyResult(context: Context, notice: LibBookNotice) {
    notifyBookingResult(
        context = context,
        outcome = notice.outcome,
        booking = notice.booking,
        message = notice.message,
    )
  }

  fun notifyBookingResult(
      context: Context,
      outcome: Outcome,
      booking: LibBookBookingDto? = null,
      message: String? = null,
  ) {
    val detail = booking?.let(::bookingDetail).orEmpty()
    val body =
        listOfNotNull(
                detail.takeIf { it.isNotBlank() },
                message?.trim()?.takeIf { it.isNotEmpty() },
            )
            .joinToString("\n")
            .ifBlank { "无更多信息" }
    post(
        context = context,
        title = titleOf(outcome),
        text = body,
        route = AppNotificationRoute.LibBookBookings,
        tag = "libbook:${outcome.name}:${booking?.id.orEmpty()}",
        succeeded = outcome.succeeded,
    )
  }

  internal fun titleOf(outcome: Outcome): String =
      when (outcome) {
        Outcome.RESERVED -> "图书馆座位预约成功"
        Outcome.SIGNED_IN -> "图书馆座位签到成功"
        Outcome.RETURNED -> "图书馆座位返回签到成功"
        Outcome.LEFT -> "图书馆座位已临时离开"
        Outcome.CHECKED_OUT -> "图书馆座位已签退"
        Outcome.CANCELED -> "图书馆预约已取消"
        Outcome.RESERVE_FAILED -> "图书馆座位预约失败"
        Outcome.SIGN_IN_FAILED -> "图书馆座位签到失败"
        Outcome.RETURN_FAILED -> "图书馆返回签到失败"
        Outcome.LEAVE_FAILED -> "图书馆临时离开失败"
        Outcome.CHECKOUT_FAILED -> "图书馆签退失败"
        Outcome.CANCEL_FAILED -> "取消图书馆预约失败"
      }

  /** 预约详情：场馆/座位 + 时间 + 状态，尽量把接口给的字段都用上。 */
  internal fun bookingDetail(booking: LibBookBookingDto): String {
    val place =
        booking.nameMerge.ifBlank {
          listOf(booking.areaName, booking.seatNo).filter { it.isNotBlank() }.joinToString(" / ")
        }
    val time = formatUbaaDateWithTimeRange(booking.day, booking.beginTime, booking.endTime)
    val status = booking.statusName.ifBlank { booking.status }
    return listOf(place, time, status).filter { it.isNotBlank() }.joinToString(" · ")
  }

  private fun post(
      context: Context,
      title: String,
      text: String,
      route: AppNotificationRoute,
      tag: String,
      succeeded: Boolean,
  ) {
    ReminderNotifications.postNow(
        context = context,
        key = tag,
        channel = ReminderNotifications.LIBBOOK_CHANNEL,
        title = title,
        text = text,
        route = route,
        succeeded = succeeded,
    )
  }
}
