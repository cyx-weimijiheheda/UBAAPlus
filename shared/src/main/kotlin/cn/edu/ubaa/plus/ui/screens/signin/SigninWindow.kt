package cn.edu.ubaa.plus.ui.screens.signin

import cn.edu.ubaa.plus.model.format.parseUbaaTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 课堂签到的统一时间窗。
 *
 * 学校不同入口（直接签到、扫码签到、博雅签到）原先各有时段，实际可签窗口不一致。 这里统一成**上课前 10 分钟到本堂课结束**，所有签到入口都按它判断是否允许点击。
 */
internal const val SIGNIN_LEAD_MINUTES = 10

/** 签到时间窗的判定结果。 */
enum class SigninWindowState {
  /** 还没到开放时间。 */
  UPCOMING,

  /** 可以签到。 */
  OPEN,

  /** 本节课已结束。 */
  ENDED,

  /** 缺少必要时间字段，无法判断，交给服务端裁决。 */
  UNKNOWN,
}

/** 一次签到的时间边界。 */
data class SigninWindow(
    val opensAt: LocalTime?,
    val closesAt: LocalTime?,
    val state: SigninWindowState,
) {
  /** 是否允许发起签到；时间无法判断时不拦，避免误伤。 */
  val allowsSubmit: Boolean
    get() = state == SigninWindowState.OPEN || state == SigninWindowState.UNKNOWN
}

/**
 * 按上课与下课时间算出签到窗口。
 *
 * @param beginRaw 上课时间，支持 `08:00`、`08:00:00` 或带日期的时间串。
 * @param endRaw 下课时间，格式同上。
 * @param now 当前校区时间，默认取系统时区当前时刻。
 */
@OptIn(ExperimentalTime::class)
internal fun signinWindow(
    beginRaw: String,
    endRaw: String,
    now: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
): SigninWindow {
  val begin = parseUbaaTime(beginRaw)
  val end = parseUbaaTime(endRaw)
  if (begin == null || end == null) {
    return SigninWindow(opensAt = null, closesAt = null, state = SigninWindowState.UNKNOWN)
  }
  val opensAt = begin.minusMinutes(SIGNIN_LEAD_MINUTES)
  val nowMinutes = now.hour * 60 + now.minute
  val opensMinutes = opensAt.hour * 60 + opensAt.minute
  val closesMinutes = end.hour * 60 + end.minute
  val state =
      when {
        nowMinutes < opensMinutes -> SigninWindowState.UPCOMING
        nowMinutes < closesMinutes -> SigninWindowState.OPEN
        else -> SigninWindowState.ENDED
      }
  return SigninWindow(opensAt = opensAt, closesAt = end, state = state)
}

/** 时间窗文案：未开始提示几点可签，已结束说明已关闭。 */
internal fun SigninWindow.describe(): String? =
    when (state) {
      SigninWindowState.OPEN -> "可以签到"
      SigninWindowState.UPCOMING -> opensAt?.let { "${padClock(it)} 起可签到" } ?: "尚未到签到时间"
      SigninWindowState.ENDED -> "本堂课签到已结束"
      SigninWindowState.UNKNOWN -> null
    }

internal fun LocalTime.minusMinutes(minutes: Int): LocalTime {
  val total = (hour * 60 + minute - minutes).coerceIn(0, 24 * 60 - 1)
  return LocalTime(total / 60, total % 60)
}

internal fun padClock(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
