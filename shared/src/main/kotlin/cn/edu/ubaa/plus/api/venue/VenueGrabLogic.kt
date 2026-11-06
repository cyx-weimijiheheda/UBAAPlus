package cn.edu.ubaa.plus.api.venue

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.datetime.LocalTime

enum class VenueSpaceMode {
  /** 指定场地：优先使用用户在表格里点选的场地。 */
  FIXED,
  /** 自动选场地：只要求时段。 */
  AUTO,
}

/** 学校限制：一次预约最多连续 2 小时。 */
internal const val MAX_VENUE_SPAN_MINUTES = 120

/** 一次抢场地任务的全部参数。 */
data class VenueGrabTask(
    val siteId: Int,
    val date: String,
    val startBeginTime: String,
    val durationMinutes: Int,
    val spaceMode: VenueSpaceMode,
    val preferredSpaceByBegin: Map<String, Int> = emptyMap(),
    val fallbackAnySpace: Boolean = true,
    val randomSpace: Boolean = false,
    val buddyIds: List<Int> = emptyList(),
    val phone: String? = null,
    val pollInterval: Duration = 350.milliseconds,
    val grabAt: LocalTime? = null,
)

/** 抢场地失败的处理方式。 */
enum class VenueFailureKind {
  RETRY,
  NOT_RELEASED,
  CAPTCHA_INVALID,
  CAPTCHA_RATE_LIMITED,
  AUTH_EXPIRED,
  STOP,
}

/** 目标时段的解析结果。 */
data class VenueSlotResolution(
    val slots: List<VenueTimeSlot>,
    val requestedMinutes: Int,
    val resolvedMinutes: Int,
    val spanMinutes: Int,
) {
  val isComplete: Boolean
    get() = slots.isNotEmpty() && resolvedMinutes >= requestedMinutes

  val exceedsTwoHours: Boolean
    get() = spanMinutes > MAX_VENUE_SPAN_MINUTES

  /** 实际起止区间，例如 `19:00-20:30`。 */
  val rangeLabel: String
    get() = if (slots.isEmpty()) "" else "${slots.first().beginTime}-${slots.last().endTime}"
}

/**
 * 由开始时刻和时长推导真实时段。
 *
 * 官方时段表不一定是整小时（射击 30 分钟一场，沙河游泳馆 11:45-13:45）， 所以按真实分钟数累计到目标时长为止，不再按“最多 2 个时段”截断。
 */
internal fun resolveVenueTargetSlots(
    template: List<VenueTimeSlot>,
    startBegin: String,
    minutes: Int,
): VenueSlotResolution {
  val requested = minutes.coerceIn(1, MAX_VENUE_SPAN_MINUTES)
  val startIndex = template.indexOfFirst { it.beginTime == startBegin }
  if (startIndex < 0) return VenueSlotResolution(emptyList(), requested, 0, 0)

  val result = mutableListOf<VenueTimeSlot>()
  var accumulated = 0
  for (index in startIndex until template.size) {
    val slot = template[index]
    result += slot
    accumulated += venueSlotMinutes(slot)
    if (accumulated >= requested) break
  }
  val span = slotSpanMinutes(result) ?: accumulated
  return VenueSlotResolution(result, requested, accumulated, span)
}

/** 可选时长：以场馆最小时段为单位，最多 2 小时。 */
internal fun venueDurationOptions(slots: List<VenueTimeSlot>): List<Int> {
  val unit =
      slots
          .mapNotNull { slot -> slotMinutesOrNull(slot)?.takeIf { it > 0 } }
          .minOrNull()
          ?.coerceAtLeast(1) ?: 60
  val options = (1..4).map { unit * it }.filter { it <= MAX_VENUE_SPAN_MINUTES }
  return options.ifEmpty { listOf(MAX_VENUE_SPAN_MINUTES) }
}

internal fun coerceVenueDuration(minutes: Int, slots: List<VenueTimeSlot>): Int {
  val options = venueDurationOptions(slots)
  return when {
    minutes in options -> minutes
    minutes < options.first() -> options.first()
    else -> options.last()
  }
}

/** 时长文案：30 分钟 / 1 小时 / 1.5 小时 / 2 小时。 */
internal fun venueDurationLabel(minutes: Int): String {
  val hours = minutes / 60
  val rest = minutes % 60
  return when {
    hours == 0 -> "$minutes 分钟"
    rest == 0 -> "$hours 小时"
    rest == 30 -> "$hours.5 小时"
    else -> "$hours 小时 $rest 分钟"
  }
}

internal fun venueSlotMinutes(slot: VenueTimeSlot): Int = slotMinutesOrNull(slot) ?: 60

/** 为每个目标时段挑一个空闲场地；任意一段无空位则返回 null。 */
internal fun pickVenueSelection(
    dayInfo: VenueDayInfo,
    targetSlots: List<VenueTimeSlot>,
    preferredSpaceByBegin: Map<String, Int>,
    fallbackAnySpace: Boolean,
    randomSpace: Boolean,
    random: Random = Random.Default,
): List<VenueSelection>? {
  if (targetSlots.isEmpty()) return null

  val usedSpaces = mutableSetOf<Int>()
  val selections = mutableListOf<VenueSelection>()

  for (slot in targetSlots) {
    val freeSpaces = dayInfo.spaces.filter { it.slot(slot.timeId)?.isFree == true }
    if (freeSpaces.isEmpty()) return null

    val preferredId = preferredSpaceByBegin[slot.beginTime]
    var chosen: VenueSpace? = null
    if (preferredId != null) {
      chosen = freeSpaces.firstOrNull { it.spaceId == preferredId }
      if (chosen == null && !fallbackAnySpace) return null
    }
    if (chosen == null) {
      chosen = freeSpaces.firstOrNull { it.spaceId in usedSpaces }
    }
    if (chosen == null) {
      chosen = if (randomSpace) freeSpaces[random.nextInt(freeSpaces.size)] else freeSpaces.first()
    }

    selections +=
        VenueSelection(spaceId = chosen.spaceId, timeId = slot.timeId, groupId = chosen.groupId)
    usedSpaces += chosen.spaceId
  }
  return selections
}

/** 上游明确表示「可以重试」的文案。 */
internal fun isRetryableVenueMessage(message: String?): Boolean {
  val text = message.orEmpty()
  return RETRYABLE_KEYWORDS.any { text.contains(it) }
}

internal fun classifyVenueFailure(message: String?, code: String?): VenueFailureKind {
  val text = message.orEmpty()
  return when {
    code == "venue_unauthenticated" -> VenueFailureKind.AUTH_EXPIRED
    text.contains("次数超出") -> VenueFailureKind.CAPTCHA_RATE_LIMITED
    text.contains("验证") -> VenueFailureKind.CAPTCHA_INVALID
    text.contains("最多只能查看") || text.contains("尚未") -> VenueFailureKind.NOT_RELEASED
    isRetryableVenueMessage(text) -> VenueFailureKind.RETRY
    else -> VenueFailureKind.STOP
  }
}

private val RETRYABLE_KEYWORDS =
    listOf(
        "已被预约",
        "已被占用",
        "已满",
        "已被抢",
        "请重新选择",
        "稍后再试",
        "系统繁忙",
        "当前预约人数过多",
    )

private fun slotMinutesOrNull(slot: VenueTimeSlot): Int? {
  val begin = parseMinutes(slot.beginTime) ?: return null
  val end = parseMinutes(slot.endTime) ?: return null
  return if (end > begin) end - begin else null
}

private fun slotSpanMinutes(slots: List<VenueTimeSlot>): Int? {
  val begin = slots.firstOrNull()?.let { parseMinutes(it.beginTime) } ?: return null
  val end = slots.lastOrNull()?.let { parseMinutes(it.endTime) } ?: return null
  return if (end > begin) end - begin else null
}

private fun parseMinutes(value: String): Int? {
  val parts = value.split(":")
  if (parts.size < 2) return null
  val hour = parts[0].toIntOrNull() ?: return null
  val minute = parts[1].toIntOrNull() ?: return null
  return hour * 60 + minute
}
