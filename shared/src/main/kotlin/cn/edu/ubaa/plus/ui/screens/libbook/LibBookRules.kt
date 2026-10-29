package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal const val LIB_BOOK_CHECK_IN_MINUTES = 30

/** 座位使用规则；客户端只提示，最终以服务端校验为准。 */
internal val LIB_BOOK_RULES: List<String> =
    listOf(
        "预约时间：线上开放时间为每日 08:30 至闭馆；线下开放时间为开馆至闭馆。",
        "签到时间：预约成功后，须在 30 分钟内完成签到。",
        "签到方式：使用校园卡、人脸识别或北航码通过闸机入馆后自动签到；也可扫描座位墨水屏或桌面标签二维码手动签到。",
        "取消预约：签到成功前，每日可通过系统自行取消 1 次。",
        "签退与释放：中途离馆 30 分钟内返回可继续使用原座位；午餐 10:30-13:30、晚餐 16:30-19:00 离馆，2 小时内返回可继续使用；结束使用请主动签退；闭馆后系统自动签退。",
        "违约处理：预约成功后 30 分钟内未签到，系统自动取消并记违约 1 次；累计违约 3 次将暂停线上座位预约权限 7 天。",
        "图书馆将根据座位使用情况持续优化预约规则。",
    )

/** 从预约创建时间计算签到截止；接口没返回创建时间时返回 null。 */
internal fun libBookCheckInDeadline(
    booking: LibBookBookingDto,
    checkInMinutes: Int = LIB_BOOK_CHECK_IN_MINUTES,
): LocalDateTime? {
  val created = parseLibBookDateTime(booking.createTime) ?: return null
  return (created.toInstant(TimeZone.UTC) + checkInMinutes.minutes).toLocalDateTime(TimeZone.UTC)
}

internal fun libBookRemainingMinutes(deadline: LocalDateTime, now: LocalDateTime): Long {
  val seconds =
      deadline.toInstant(TimeZone.UTC).epochSeconds - now.toInstant(TimeZone.UTC).epochSeconds
  return (seconds / 60).coerceAtLeast(0L)
}

internal fun formatLibBookClock(value: LocalDateTime): String =
    "${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"

/** 兼容 "2026-05-08 08:00:00"、"2026-05-08T08:00" 等常见格式。 */
internal fun parseLibBookDateTime(raw: String): LocalDateTime? {
  val text = raw.trim().replace(' ', 'T')
  if (text.length < 16) return null
  val normalized =
      when {
        text.length == 16 -> "$text:00"
        text.count { it == ':' } == 1 -> "$text:00"
        else -> text
      }
  return runCatching { LocalDateTime.parse(normalized) }.getOrNull()
}
