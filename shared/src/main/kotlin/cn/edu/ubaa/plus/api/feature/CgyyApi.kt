package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.model.dto.CgyyDayInfoResponse
import cn.edu.ubaa.plus.model.dto.CgyyLockCodeResponse
import cn.edu.ubaa.plus.model.dto.CgyyOrderDto
import cn.edu.ubaa.plus.model.dto.CgyyOrdersPageResponse
import cn.edu.ubaa.plus.model.dto.CgyyPurposeTypeDto
import cn.edu.ubaa.plus.model.dto.CgyyReservationSubmitRequest
import cn.edu.ubaa.plus.model.dto.CgyyReservationSubmitResponse
import cn.edu.ubaa.plus.model.dto.CgyyVenueSiteDto

interface CgyyApiBackend {
  suspend fun getVenueSites(): Result<List<CgyyVenueSiteDto>>

  suspend fun getPurposeTypes(): Result<List<CgyyPurposeTypeDto>>

  suspend fun getDayInfo(venueSiteId: Int, date: String): Result<CgyyDayInfoResponse>

  suspend fun submitReservation(
      request: CgyyReservationSubmitRequest
  ): Result<CgyyReservationSubmitResponse>

  suspend fun getMyOrders(page: Int, size: Int): Result<CgyyOrdersPageResponse>

  suspend fun getOrderDetail(orderId: Int): Result<CgyyOrderDto>

  suspend fun cancelOrder(orderId: Int): Result<CgyyReservationSubmitResponse>

  suspend fun getLockCode(): Result<CgyyLockCodeResponse>
}

open class CgyyApi(
    private val backendProvider: () -> CgyyApiBackend = { ConnectionRuntime.apiFactory().cgyyApi() }
) {
  internal constructor(backend: CgyyApiBackend) : this({ backend })

  private fun currentBackend(): CgyyApiBackend = backendProvider()

  open suspend fun getVenueSites(): Result<List<CgyyVenueSiteDto>> {
    return currentBackend().getVenueSites()
  }

  open suspend fun getPurposeTypes(): Result<List<CgyyPurposeTypeDto>> {
    return currentBackend().getPurposeTypes()
  }

  open suspend fun getDayInfo(venueSiteId: Int, date: String): Result<CgyyDayInfoResponse> {
    return currentBackend().getDayInfo(venueSiteId, date)
  }

  open suspend fun submitReservation(
      request: CgyyReservationSubmitRequest
  ): Result<CgyyReservationSubmitResponse> {
    return cn.edu.ubaa.plus.api.recovery.SchoolWriteCoordinator.perform(
        "cgyy",
        "reserve:${request.venueSiteId}:${request.reservationDate}:${request.selections.joinToString { "${it.spaceId}:${it.timeId}" }}",
        accepted = { it.success },
        submit = { currentBackend().submitReservation(request) },
        verify = {
          // 丢失订单 ID 时，列表中的相似事项不能证明是这次提交；只刷新，不推定成功。
          currentBackend().getMyOrders(0, 100)
          null
        },
    )
  }

  open suspend fun getMyOrders(page: Int = 0, size: Int = 20): Result<CgyyOrdersPageResponse> {
    return currentBackend().getMyOrders(page, size)
  }

  open suspend fun getOrderDetail(orderId: Int): Result<CgyyOrderDto> {
    return currentBackend().getOrderDetail(orderId)
  }

  open suspend fun cancelOrder(orderId: Int): Result<CgyyReservationSubmitResponse> {
    return cn.edu.ubaa.plus.api.recovery.SchoolWriteCoordinator.perform(
        "cgyy",
        "cancel:$orderId",
        accepted = { it.success },
        submit = { currentBackend().cancelOrder(orderId) },
        verify = {
          currentBackend()
              .getOrderDetail(orderId)
              .getOrNull()
              ?.takeIf { it.orderStatus == 2 }
              ?.let { CgyyReservationSubmitResponse(true, "已核实取消成功", it) }
        },
    )
  }

  open suspend fun getLockCode(): Result<CgyyLockCodeResponse> {
    return currentBackend().getLockCode()
  }
}
