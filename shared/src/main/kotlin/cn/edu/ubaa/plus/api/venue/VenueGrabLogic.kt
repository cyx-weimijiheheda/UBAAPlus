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

/** 一次抢场地任务的全部参数。 */
data class VenueGrabTask(
    val siteId: Int,
    val date: String,
    val startBeginTime: String,
    val durationHours: Int,
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

/** 由开始时刻和时长推导真实时段，最多 2 个时段、2 小时。 */
internal fun resolveVenueTargetSlots(
    template: List<VenueTimeSlot>,
    startBegin: String,
    hours: Int,
): List<VenueTimeSlot> {
  val startIndex = template.indexOfFirst { it.beginTime == startBegin }
  if (startIndex < 0) return emptyList()

  val targetMinutes = hours.coerceIn(1, 2) * 60
  val result = mutableListOf<VenueTimeSlot>()
  var accumulated = 0
  for (index in startIndex until template.size) {
    val slot = template[index]
    result += slot
    accumulated += slotDurationMinutes(slot)
    if (accumulated >= targetMinutes || result.size >= 2) break
  }
  return result
}

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

private fun slotDurationMinutes(slot: VenueTimeSlot): Int {
  val begin = parseMinutes(slot.beginTime) ?: return 60
  val end = parseMinutes(slot.endTime) ?: return 60
  return if (end > begin) end - begin else 60
}

private fun parseMinutes(value: String): Int? {
  val parts = value.split(":")
  if (parts.size < 2) return null
  val hour = parts[0].toIntOrNull() ?: return null
  val minute = parts[1].toIntOrNull() ?: return null
  return hour * 60 + minute
}
