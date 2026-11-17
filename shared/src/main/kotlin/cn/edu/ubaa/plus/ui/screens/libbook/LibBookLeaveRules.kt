package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.LibBookSeatActionResponse
import cn.edu.ubaa.plus.model.dto.canReturnSignIn
import cn.edu.ubaa.plus.model.format.parseUbaaDate
import cn.edu.ubaa.plus.model.format.parseUbaaDateTime
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.*

internal val LIB_BOOK_ZONE = TimeZone.of("Asia/Shanghai")

/** 按离馆时刻确定普通 30 分钟/用餐 120 分钟宽限；不延长到预约结束之后。 */
internal fun calculateLibBookReturnDeadline(
    booking: LibBookBookingDto,
    started: LocalDateTime,
): LocalDateTime {
  val minute = started.hour * 60 + started.minute
  val meal = minute in 630..810 || minute in 990..1140
  val deadline =
      (started.toInstant(LIB_BOOK_ZONE) + (if (meal) 120 else 30).minutes).toLocalDateTime(
          LIB_BOOK_ZONE
      )
  val date = parseUbaaDate(booking.day)
  val end = parseUbaaTime(booking.endTime)
  val bookingEnd = if (date != null && end != null) LocalDateTime(date, end) else null
  return bookingEnd?.takeIf { it > started && it < deadline } ?: deadline
}

internal fun libBookReturnDeadline(booking: LibBookBookingDto): LocalDateTime? =
    parseUbaaDateTime(booking.leaveDeadline)
        ?: parseUbaaDateTime(booking.leaveStartedAt)?.let {
          calculateLibBookReturnDeadline(booking, it)
        }

internal fun successfulLibBookLeave(
    booking: LibBookBookingDto,
    response: LibBookSeatActionResponse,
    started: LocalDateTime,
): LibBookBookingDto {
  val start = parseUbaaDateTime(response.leaveStartedAt) ?: started
  val serverDeadline = parseUbaaDateTime(response.leaveDeadline)
  return booking.copy(
      status = "7",
      statusName = "临时离开",
      leaveStartedAt = start.toString(),
      leaveDeadline = (serverDeadline ?: calculateLibBookReturnDeadline(booking, start)).toString(),
      leaveDeadlineEstimated = serverDeadline == null,
  )
}

internal fun libBookReturnHint(booking: LibBookBookingDto, now: LocalDateTime): String? {
  if (!booking.canReturnSignIn()) return null
  val deadline = libBookReturnDeadline(booking) ?: return "暂离中，平台未提供离馆时间；请及时通过闸机返回，以免座位被释放"
  val clock =
      "${deadline.hour.toString().padStart(2, '0')}:${deadline.minute.toString().padStart(2, '0')}"
  val prefix = if (booking.leaveDeadlineEstimated) "按暂离规则预计" else "请"
  return if (now < deadline) "${prefix}在 $clock 前通过闸机返回，逾时座位将被自动释放"
  else "已到暂离截止时间 $clock，请刷新确认座位是否已被释放"
}
