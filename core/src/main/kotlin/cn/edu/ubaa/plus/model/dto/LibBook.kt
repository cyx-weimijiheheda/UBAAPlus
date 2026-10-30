package cn.edu.ubaa.plus.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibBookLibraryDto(
    val id: String,
    val name: String,
    val freeNum: Int = 0,
    val totalNum: Int = 0,
    val storeys: List<LibBookStoreyDto> = emptyList(),
)

@Serializable
data class LibBookStoreyDto(
    val id: String,
    val name: String,
    val freeNum: Int = 0,
    val totalNum: Int = 0,
)

@Serializable
data class LibBookAreaDto(
    val id: String,
    val name: String,
    val areaName: String = "",
    val premisesId: String = "",
    val storeyId: String = "",
    val freeNum: Int = 0,
    val totalNum: Int = 0,
)

@Serializable
data class LibBookTimeSlotDto(
    val id: String,
    val start: String,
    val end: String,
    val label: String = "$start-$end",
)

@Serializable
data class LibBookAreaDetailDto(
    val id: String,
    val name: String = "",
    val availableDates: List<String> = emptyList(),
    val timeSlots: List<LibBookTimeSlotDto> = emptyList(),
)

@Serializable
data class LibBookSeatDto(
    val id: String,
    val name: String = "",
    val no: String = "",
    val status: String = "",
    val statusName: String = "",
    val isAvailable: Boolean = status == "1",
)

@Serializable
data class LibBookReserveRequest(
    val areaId: String,
    val seatId: String,
    val day: String,
    val segment: String,
    val startTime: String = "",
    val endTime: String = "",
)

@Serializable
data class LibBookReserveResponse(
    val success: Boolean,
    val message: String,
    val booking: LibBookBookingDto? = null,
)

@Serializable
data class LibBookCancelResponse(
    val success: Boolean,
    val message: String,
)

@Serializable
data class LibBookBookingsResponse(
    val bookings: List<LibBookBookingDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = bookings.size,
)

@Serializable
data class LibBookBookingDto(
    val id: String,
    val nameMerge: String = "",
    val areaName: String = "",
    val seatNo: String = "",
    val day: String = "",
    val beginTime: String = "",
    val endTime: String = "",
    val status: String = "",
    val statusName: String = "",
    /** 座位二维码/操作接口需要的一次性 query，部分列表响应会返回。 */
    val query: String = "",
    val seatId: String = "",
    val areaId: String = "",
    val signStatus: String = "",
    /** 预约类型：1=图书馆座位，3/4=研修间。 */
    val type: String = "",
    /** 官方列表返回的操作标志，缺失时回退到 status 判断。 */
    val flagIn: String = "",
    val flagOut: String = "",
    val flagLeave: String = "",
    val oksign: String = "",
    /** 预约创建时间，用于计算 30 分钟签到截止；部分接口返回。 */
    val createTime: String = "",
)

fun LibBookBookingDto.canCancelBooking(): Boolean = cancelBlockedMessage() == null

/** 已结束/已取消等终态，任何座位操作都不应再显示。 */
fun LibBookBookingDto.isTerminalBooking(): Boolean =
    status.trim() in nonCancelableLibBookBookingStatusCodes ||
        nonCancelableLibBookBookingStatusKeywords.any { statusName.contains(it) }

/** 已签到、正在使用或临时离开中。 */
fun LibBookBookingDto.isInUseBooking(): Boolean =
    !isTerminalBooking() &&
        (status.trim() in inUseLibBookBookingStatusCodes ||
            listOf("使用中", "临时离开", "暂离", "已签到").any { statusName.contains(it) })

fun LibBookBookingDto.canSignIn(): Boolean =
    !isTerminalBooking() && (flagIn == "1" || (flagIn.isBlank() && !isInUseBooking()))

fun LibBookBookingDto.canTemporaryLeave(): Boolean =
    !isTerminalBooking() && (flagOut == "1" || (flagOut.isBlank() && isInUseBooking()))

fun LibBookBookingDto.canCheckout(): Boolean =
    !isTerminalBooking() &&
        (flagLeave == "1" || flagOut == "1" || (flagLeave.isBlank() && isInUseBooking()))

fun LibBookBookingDto.cancelBlockedMessage(): String? {
  if (id.isBlank()) return "预约记录不存在或已失效，请刷新后重试"
  if (status.trim() in nonCancelableLibBookBookingStatusCodes) {
    return "该预约已结束或已取消，无需取消"
  }
  if (nonCancelableLibBookBookingStatusKeywords.any { statusName.contains(it) }) {
    return "该预约已结束或已取消，无需取消"
  }
  return null
}

private val nonCancelableLibBookBookingStatusCodes = setOf("6", "8")

private val inUseLibBookBookingStatusCodes = setOf("3", "11", "33")

private val nonCancelableLibBookBookingStatusKeywords = listOf("取消", "结束", "已完成", "过期", "失效")

/** 座位签到/离座动作。 */
enum class LibBookSeatAction {
  SIGN_IN,
  LEAVE,
  CHECKOUT,
}

@Serializable
data class LibBookGeoPoint(
    val lat: Double,
    val lng: Double,
    val time: Long,
)

@Serializable
data class LibBookSeatActionRequest(
    val id: String,
    /** 仅扫码签到流程使用；列表发起的离开/签退不需要。 */
    val query: String = "",
    val points: LibBookGeoPoint? = null,
)

@Serializable
data class LibBookSeatActionResponse(
    val success: Boolean,
    val message: String,
)

/** 扫描座位二维码后由 /v4/space/qr_scan 返回的状态。 */
@Serializable
data class LibBookQrScanResultDto(
    val query: String = "",
    val bookId: String = "",
    val seatId: String = "",
    val seatNo: String = "",
    val areaName: String = "",
    val day: String = "",
    val beginTime: String = "",
    val endTime: String = "",
    val status: String = "",
    val statusName: String = "",
) {
  val canSignIn: Boolean
    get() = status.trim() in setOf("4", "6", "7") || statusName.contains("未签到")

  val canTemporaryLeave: Boolean
    get() = status.trim() in inUseLibBookBookingStatusCodes || statusName.contains("使用中")

  val canCheckout: Boolean
    get() = canTemporaryLeave
}

@Serializable
data class LibBookEncryptedReserveBody(
    @SerialName("seat_id") val seatId: String,
    val segment: String,
    val day: String,
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
)
