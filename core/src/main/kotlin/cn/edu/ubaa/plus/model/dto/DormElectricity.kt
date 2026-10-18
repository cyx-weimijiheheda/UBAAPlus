package cn.edu.ubaa.plus.model.dto

import kotlinx.serialization.Serializable

/** 宿舍电表目录中的一条记录。 */
@Serializable
data class DormRoomDto(
    val id: Int,
    val campus: String = "",
    val building: String = "",
    val floor: String = "",
    val room: String = "",
    val name: String = "",
    val meterNo: String = "",
    val address: String = "",
)

/** 单个电表的当前状态。 */
@Serializable
data class DormMeterInfoDto(
    val id: Int,
    val campus: String = "",
    val building: String = "",
    val room: String = "",
    val address: String = "",
    val remain: Double = 0.0,
    val price: Double = 0.0,
    val payStatus: Int = 0,
    val readingTime: String = "",
    val ct: Int = 1,
    val payUrl: String? = null,
    val serial: String? = null,
    val money: Double = 0.0,
)

/** 最近一次购电记录。 */
@Serializable
data class DormTopUpDto(
    val date: String = "",
    val power: String = "",
    val amount: String = "",
    val operator: String = "",
)
