package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.api.recovery.SchoolWriteCoordinator
import cn.edu.ubaa.plus.model.dto.LibBookAreaDetailDto
import cn.edu.ubaa.plus.model.dto.LibBookAreaDto
import cn.edu.ubaa.plus.model.dto.LibBookBookingsResponse
import cn.edu.ubaa.plus.model.dto.LibBookCancelResponse
import cn.edu.ubaa.plus.model.dto.LibBookGeoPoint
import cn.edu.ubaa.plus.model.dto.LibBookLibraryDto
import cn.edu.ubaa.plus.model.dto.LibBookQrScanResultDto
import cn.edu.ubaa.plus.model.dto.LibBookReserveRequest
import cn.edu.ubaa.plus.model.dto.LibBookReserveResponse
import cn.edu.ubaa.plus.model.dto.LibBookSeatActionRequest
import cn.edu.ubaa.plus.model.dto.LibBookSeatActionResponse
import cn.edu.ubaa.plus.model.dto.LibBookSeatDto
import cn.edu.ubaa.plus.model.dto.canReturnSignIn
import cn.edu.ubaa.plus.model.dto.isInUseBooking
import cn.edu.ubaa.plus.model.dto.isTerminalBooking

interface LibBookApiBackend {
  suspend fun getLibraries(day: String): Result<List<LibBookLibraryDto>>

  suspend fun getAreas(
      premisesId: String,
      storeyId: String?,
      day: String,
  ): Result<List<LibBookAreaDto>>

  suspend fun getAreaDetail(areaId: String): Result<LibBookAreaDetailDto>

  suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): Result<List<LibBookSeatDto>>

  suspend fun reserve(request: LibBookReserveRequest): Result<LibBookReserveResponse>

  suspend fun getBookings(page: Int = 1, limit: Int = 20): Result<LibBookBookingsResponse>

  suspend fun cancelBooking(bookingId: String): Result<LibBookCancelResponse>

  suspend fun qrScan(
      params: Map<String, String>,
      points: LibBookGeoPoint? = null,
  ): Result<LibBookQrScanResultDto>

  suspend fun signIn(request: LibBookSeatActionRequest): Result<LibBookSeatActionResponse>

  suspend fun leave(request: LibBookSeatActionRequest): Result<LibBookSeatActionResponse>

  suspend fun checkout(request: LibBookSeatActionRequest): Result<LibBookSeatActionResponse>
}

open class LibBookApi(
    private val backendProvider: () -> LibBookApiBackend = {
      ConnectionRuntime.apiFactory().libBookApi()
    }
) {
  internal constructor(backend: LibBookApiBackend) : this({ backend })

  private fun currentBackend(): LibBookApiBackend = backendProvider()

  open suspend fun getLibraries(day: String): Result<List<LibBookLibraryDto>> =
      currentBackend().getLibraries(day)

  open suspend fun getAreas(
      premisesId: String,
      storeyId: String? = null,
      day: String,
  ): Result<List<LibBookAreaDto>> = currentBackend().getAreas(premisesId, storeyId, day)

  open suspend fun getAreaDetail(areaId: String): Result<LibBookAreaDetailDto> =
      currentBackend().getAreaDetail(areaId)

  open suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): Result<List<LibBookSeatDto>> = currentBackend().getSeats(areaId, day, startTime, endTime)

  open suspend fun reserve(request: LibBookReserveRequest): Result<LibBookReserveResponse> =
      SchoolWriteCoordinator.perform(
          "libbook",
          "reserve:${request.areaId}:${request.seatId}:${request.day}:${request.segment}",
          accepted = { it.success },
          submit = { currentBackend().reserve(request) },
          verify = {
            currentBackend()
                .getBookings(1, 100)
                .getOrNull()
                ?.bookings
                ?.singleOrNull {
                  it.seatId == request.seatId &&
                      it.areaId == request.areaId &&
                      it.day == request.day &&
                      it.beginTime == request.startTime &&
                      it.endTime == request.endTime &&
                      !it.isTerminalBooking()
                }
                ?.let { cn.edu.ubaa.plus.model.dto.LibBookReserveResponse(true, "已核实预约成功", it) }
          },
      )

  open suspend fun getBookings(page: Int = 1, limit: Int = 20): Result<LibBookBookingsResponse> =
      SchoolReadCoordinator.read("libbook", "bookings:$page:$limit", cache = false) {
        currentBackend().getBookings(page, limit)
      }

  open suspend fun cancelBooking(bookingId: String): Result<LibBookCancelResponse> =
      SchoolWriteCoordinator.perform(
          "libbook",
          "cancel:$bookingId",
          accepted = { it.success },
          submit = { currentBackend().cancelBooking(bookingId) },
          verify = {
            currentBackend()
                .getBookings(1, 100)
                .getOrNull()
                ?.bookings
                ?.firstOrNull { it.id == bookingId && it.isTerminalBooking() }
                ?.let { LibBookCancelResponse(true, "已核实预约结束") }
          },
      )

  open suspend fun qrScan(
      params: Map<String, String>,
      points: LibBookGeoPoint? = null,
  ): Result<LibBookQrScanResultDto> = currentBackend().qrScan(params, points)

  open suspend fun signIn(request: LibBookSeatActionRequest): Result<LibBookSeatActionResponse> =
      SchoolWriteCoordinator.perform(
          "libbook",
          "signIn:${request.id}",
          accepted = { it.success },
          submit = { currentBackend().signIn(request) },
          verify = {
            currentBackend()
                .getBookings(1, 100)
                .getOrNull()
                ?.bookings
                ?.firstOrNull {
                  it.id == request.id && (it.isInUseBooking() && !it.canReturnSignIn())
                }
                ?.let { LibBookSeatActionResponse(success = true, message = "已核实座位状态") }
          },
      )

  open suspend fun leave(request: LibBookSeatActionRequest): Result<LibBookSeatActionResponse> =
      SchoolWriteCoordinator.perform(
          "libbook",
          "leave:${request.id}",
          accepted = { it.success },
          submit = { currentBackend().leave(request) },
          verify = {
            currentBackend()
                .getBookings(1, 100)
                .getOrNull()
                ?.bookings
                ?.firstOrNull { it.id == request.id && (it.canReturnSignIn()) }
                ?.let { LibBookSeatActionResponse(success = true, message = "已核实座位状态") }
          },
      )

  open suspend fun checkout(request: LibBookSeatActionRequest): Result<LibBookSeatActionResponse> =
      SchoolWriteCoordinator.perform(
          "libbook",
          "checkout:${request.id}",
          accepted = { it.success },
          submit = { currentBackend().checkout(request) },
          verify = {
            currentBackend()
                .getBookings(1, 100)
                .getOrNull()
                ?.bookings
                ?.firstOrNull { it.id == request.id && (it.isTerminalBooking()) }
                ?.let { LibBookSeatActionResponse(success = true, message = "已核实座位状态") }
          },
      )
}
