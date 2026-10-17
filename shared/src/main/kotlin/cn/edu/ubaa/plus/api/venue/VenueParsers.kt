package cn.edu.ubaa.plus.api.venue

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 运动场地响应解析。
 *
 * 这些逻辑与 Python 版 BUAAVenueSelector 一一对应；上游字段缺失时尽量降级而不是整体失败。
 */
internal fun parseVenueSiteList(data: JsonObject): VenueSiteList {
  val advanceDays = data.intOrNull("advanceReservationDays") ?: 3
  val groups =
      data["venueSiteInfo"] as? JsonObject ?: return VenueSiteList(emptyList(), advanceDays)

  val sites = mutableListOf<VenueSite>()
  for ((venueKey, value) in groups) {
    val items = value as? JsonArray ?: continue
    for (element in items) {
      val item = element as? JsonObject ?: continue
      if (item.boolOrNull("isSupportReservation") != true) continue
      val id = item.intOrNull("id") ?: continue
      sites +=
          VenueSite(
              id = id,
              siteName = item.stringOrNull("siteName").orEmpty(),
              venueId = item.intOrNull("venueId") ?: venueKey.toIntOrNull() ?: 0,
              venueName = item.stringOrNull("venueName").orEmpty(),
              campusName = item.stringOrNull("campusName").orEmpty(),
              sportName = item.stringOrNull("sportName").orEmpty(),
              openStart = item.stringOrNull("openStartDate").orEmpty(),
              openEnd = item.stringOrNull("openEndDate").orEmpty(),
              isFee = (item.intOrNull("isFee") ?: 0) != 0,
          )
    }
  }

  return VenueSiteList(
      sites = sites.sortedWith(compareBy({ it.campusName }, { it.venueName }, { it.siteName })),
      advanceReservationDays = advanceDays,
  )
}

/** 尚未放票、上游没有该日期的场地数据时返回 null。 */
internal fun parseVenueDayInfo(data: JsonObject, siteId: Int, date: String): VenueDayInfo? {
  val slots =
      (data["spaceTimeInfo"] as? JsonArray).orEmpty().mapNotNull { element ->
        val slot = element as? JsonObject ?: return@mapNotNull null
        val timeId = slot.intOrNull("id") ?: return@mapNotNull null
        VenueTimeSlot(
            timeId = timeId,
            beginTime = slot.stringOrNull("beginTime").orEmpty().take(5),
            endTime = slot.stringOrNull("endTime").orEmpty().take(5),
        )
      }
  if (slots.isEmpty()) return null

  val spaceByDate = data["reservationDateSpaceInfo"] as? JsonObject
  val rawSpaces =
      (spaceByDate?.get(date) as? JsonArray)
          ?: (spaceByDate?.values?.firstOrNull() as? JsonArray)
          ?: return null

  val spaces =
      rawSpaces.mapNotNull { element ->
        val space = element as? JsonObject ?: return@mapNotNull null
        val spaceId = space.intOrNull("id") ?: return@mapNotNull null
        val cells = mutableMapOf<Int, VenueSpaceSlot>()
        for ((timeKey, cellElement) in space) {
          val timeId = timeKey.toIntOrNull() ?: continue
          val cell = cellElement as? JsonObject ?: continue
          cells[timeId] =
              VenueSpaceSlot(
                  status = cell.intOrNull("reservationStatus") ?: 0,
                  fee = cell.doubleOrNull("orderFee"),
                  remark = cell.stringOrNull("adminRemark"),
              )
        }
        VenueSpace(
            spaceId = spaceId,
            spaceName = space.stringOrNull("spaceName").orEmpty(),
            groupId = space.intOrNull("venueSpaceGroupId"),
            slots = cells,
        )
      }
  if (spaces.isEmpty()) return null

  val orderParam = data["orderParamView"] as? JsonObject
  val siteParam = data["siteParam"] as? JsonObject

  return VenueDayInfo(
      siteId = siteId,
      date = date,
      orderToken = data.stringOrNull("token").orEmpty(),
      isFee = (data.intOrNull("isFee") ?: 0) != 0,
      slots = slots,
      spaces = spaces,
      phone = orderParam?.stringOrNull("phone"),
      isCaptchaCheck = (orderParam?.intOrNull("isCaptchaCheck") ?: 0) != 0,
      buddies = parseVenueBuddies(orderParam?.get("buddyList")),
      buddyNumMin = siteParam?.intOrNull("buddyNumMin") ?: 0,
      buddyNumMax = siteParam?.intOrNull("buddyNumMax") ?: 3,
      reservationHour = siteParam?.intOrNull("reservationHour") ?: 1,
  )
}

/** 解析 `/api/buddies` 的 `data.content`，或 day/info 里的 `buddyList` 数组。 */
internal fun parseVenueBuddies(element: JsonElement?): List<VenueBuddy> {
  val array =
      when (element) {
        is JsonArray -> element
        is JsonObject -> element["content"] as? JsonArray ?: return emptyList()
        else -> return emptyList()
      }
  return array.mapNotNull { item ->
    val buddy = item as? JsonObject ?: return@mapNotNull null
    val id = buddy.intOrNull("id") ?: return@mapNotNull null
    VenueBuddy(
        id = id,
        name = buddy.stringOrNull("name").orEmpty(),
        userUid = buddy.stringOrNull("userUid").orEmpty(),
        userId = buddy.intOrNull("userId"),
        roleId = buddy.intOrNull("userRoleId") ?: buddy.intOrNull("roleId"),
    )
  }
}

internal fun parseVenueCaptcha(data: JsonObject): VenueCaptchaChallenge? {
  if (data.stringOrNull("repCode") != "0000") return null
  val payload = data["repData"] as? JsonObject ?: return null
  val token = payload.stringOrNull("token") ?: return null
  val secretKey = payload.stringOrNull("secretKey") ?: return null
  val image = payload.stringOrNull("originalImageBase64") ?: return null
  val words =
      (payload["wordList"] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull
      }
  return VenueCaptchaChallenge(
      token = token,
      secretKey = secretKey,
      wordList = words,
      imageBase64 = image,
  )
}

internal fun parseVenueOrderResult(envelope: JsonObject): VenueOrderResult {
  val data = envelope["data"] as? JsonObject
  return VenueOrderResult(
      orderId = data?.longOrNull("id"),
      tradeNo = data?.stringOrNull("tradeNo"),
      startAt = data?.stringOrNull("reservationStartDate"),
      endAt = data?.stringOrNull("reservationEndDate"),
      message = envelope.stringOrNull("message") ?: "预约成功",
  )
}

internal fun parseVenuePayResult(envelope: JsonObject): VenuePayResult {
  val data = envelope["data"] as? JsonObject
  return VenuePayResult(
      payUrl = data?.stringOrNull("schoolPayUrl"),
      payFee = data?.doubleOrNull("payFee"),
      payCode = data?.stringOrNull("payCode"),
      message = envelope.stringOrNull("message") ?: "已生成支付订单",
  )
}

/** 下单表单字段，顺序与含义与 Python 客户端保持一致。 */
internal fun buildVenueSubmitForm(request: VenueOrderRequest): Map<String, Any?> = buildMap {
  put("venueSiteId", request.siteId)
  put("reservationDate", request.date)
  put("reservationOrderJson", VenueCrypto.selectionJson(request.selections))
  request.phone?.takeIf { it.isNotBlank() }?.let { put("phone", it) }
  if (request.buddyIds.isNotEmpty()) put("buddyIds", request.buddyIds.joinToString(","))
  put("buddyUids", "")
  put("weekStartDate", request.date)
  put("reservationType", -1)
  put("orderPin", VenueCrypto.orderPin())
  request.orderPrice?.let { put("orderPrice", it) }
  request.captchaVerification?.let { put("captchaVerification", it) }
  request.captchaToken?.let { put("captchaToken", it) }
  request.orderToken?.takeIf { it.isNotBlank() }?.let { put("token", it) }
}

internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.intOrNull(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

internal fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.boolOrNull(key: String): Boolean? {
  val primitive = this[key] as? JsonPrimitive ?: return null
  return when (primitive.content) {
    "true",
    "1" -> true
    "false",
    "0" -> false
    else -> null
  }
}
