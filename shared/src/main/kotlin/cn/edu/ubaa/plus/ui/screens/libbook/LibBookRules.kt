package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.LibBookSeatDto
import cn.edu.ubaa.plus.model.dto.canCancelBooking
import cn.edu.ubaa.plus.model.dto.canCheckout
import cn.edu.ubaa.plus.model.dto.canReturnSignIn
import cn.edu.ubaa.plus.model.dto.canSignIn
import cn.edu.ubaa.plus.model.dto.canTemporaryLeave
import cn.edu.ubaa.plus.model.dto.isInUseBooking
import cn.edu.ubaa.plus.model.dto.isTerminalBooking
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal const val LIB_BOOK_CHECK_IN_MINUTES = 30

/** 图书馆座位卡片上的操作，与「我的预约」保持一致。 */
enum class LibBookCardAction {
  CANCEL,
  SIGN_IN,
  RETURN_SIGN_IN,
  LEAVE,
  CHECKOUT,
}

fun LibBookBookingDto.cardActions(): List<LibBookCardAction> =
    if (isTerminalBooking()) {
      emptyList()
    } else {
      buildList {
        if (canCancelBooking()) add(LibBookCardAction.CANCEL)
        if (canReturnSignIn()) {
          add(LibBookCardAction.RETURN_SIGN_IN)
        } else if (canTemporaryLeave()) {
          add(LibBookCardAction.LEAVE)
        }
        if (canCheckout()) add(LibBookCardAction.CHECKOUT)
        if (!canReturnSignIn()) add(LibBookCardAction.SIGN_IN)
      }
    }

/**
 * 首页只显示今天的、未结束的预约。
 *
 * 优先选正在使用的（含临时离开），其次选待签到的，再按开始时间最早的；这样同一天有多段预约时， 首页显示的是当下真正要处理的那一张。
 */
fun selectHomeLibBookBooking(
    bookings: List<LibBookBookingDto>,
    today: String,
): LibBookBookingDto? {
  val candidates =
      bookings.filter {
        !it.isTerminalBooking() && it.day.normalizeBookingDay() == today.normalizeBookingDay()
      }
  if (candidates.isEmpty()) return null
  return candidates.minWithOrNull(
      compareBy(
          { if (it.isInUseBooking()) 0 else if (it.canSignIn()) 1 else 2 },
          { it.beginTime.ifBlank { "99:99" } },
      )
  )
}

/** 接口返回的 day 可能带时间或用斜杠分隔，统一成 `yyyy-MM-dd` 再比较。 */
internal fun String.normalizeBookingDay(): String {
  val text = trim()
  val dashed = text.take(10)
  if (dashed.length == 10 && dashed[4] == '-' && dashed[7] == '-') return dashed
  val parts = text.split('/', '-', '.', ' ')
  val year = parts.getOrNull(0)?.toIntOrNull() ?: return text
  val month = parts.getOrNull(1)?.toIntOrNull() ?: return text
  val day = parts.getOrNull(2)?.toIntOrNull() ?: return text
  return "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

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

/** 座位网格每行个数与每页行数：一行 8 个、每页 4 行，避免长列表翻不到确认按钮。 */
internal const val LIB_BOOK_SEATS_PER_ROW = 8
internal const val LIB_BOOK_SEAT_ROWS_PER_PAGE = 4

/** 按 8×4 分页；最后一页不足也保留为独立的一页。 */
internal fun libBookSeatPages(seats: List<LibBookSeatDto>): List<List<LibBookSeatDto>> {
  val pageSize = LIB_BOOK_SEATS_PER_ROW * LIB_BOOK_SEAT_ROWS_PER_PAGE
  if (seats.isEmpty()) return listOf(emptyList())
  return seats.chunked(pageSize)
}

/**
 * 主界面顶部的「当前预约」：优先正在使用的，其次未签到的，再按开始时间最早。
 *
 * 不限定必须是今天——预约可能跨天，只要还没结束就该在顶部提示。
 */
fun selectCurrentLibBookBooking(bookings: List<LibBookBookingDto>): LibBookBookingDto? =
    bookings
        .filter { !it.isTerminalBooking() }
        .minWithOrNull(
            compareBy(
                { if (it.isInUseBooking()) 0 else if (it.canSignIn()) 1 else 2 },
                { it.day.ifBlank { "9999-99-99" } },
                { it.beginTime.ifBlank { "99:99" } },
            )
        )

/** 确认预约对话框里的一句话摘要。 */
internal fun libBookReserveSummary(state: LibBookUiState): String {
  val place =
      listOfNotNull(
              state.selectedArea?.name?.takeIf { it.isNotBlank() },
              state.selectedSeat?.no?.takeIf { it.isNotBlank() },
          )
          .joinToString(" / ")
          .ifBlank { "未选择座位" }
  val slot = state.selectedSlot
  val time = slot?.let { "${it.start}-${it.end}" } ?: "未选择时段"
  return listOf(place, state.selectedDay, time).filter { it.isNotBlank() }.joinToString(" · ")
}
