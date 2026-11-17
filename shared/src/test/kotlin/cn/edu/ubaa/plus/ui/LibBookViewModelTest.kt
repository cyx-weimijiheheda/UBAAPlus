package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.feature.LibBookApi
import cn.edu.ubaa.plus.model.dto.LibBookAreaDetailDto
import cn.edu.ubaa.plus.model.dto.LibBookAreaDto
import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.LibBookBookingsResponse
import cn.edu.ubaa.plus.model.dto.LibBookCancelResponse
import cn.edu.ubaa.plus.model.dto.LibBookLibraryDto
import cn.edu.ubaa.plus.model.dto.LibBookReserveRequest
import cn.edu.ubaa.plus.model.dto.LibBookReserveResponse
import cn.edu.ubaa.plus.model.dto.LibBookSeatAction
import cn.edu.ubaa.plus.model.dto.LibBookSeatActionRequest
import cn.edu.ubaa.plus.model.dto.LibBookSeatActionResponse
import cn.edu.ubaa.plus.model.dto.LibBookSeatDto
import cn.edu.ubaa.plus.model.dto.LibBookStoreyDto
import cn.edu.ubaa.plus.model.dto.LibBookTimeSlotDto
import cn.edu.ubaa.plus.model.dto.canCancelBooking
import cn.edu.ubaa.plus.model.dto.canTemporaryLeave
import cn.edu.ubaa.plus.reminder.LibBookLeaveStore
import cn.edu.ubaa.plus.ui.screens.libbook.*
import com.russhwolf.settings.MapSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class LibBookViewModelTest {
  @AfterTest
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `leave return and leave again refresh buttons and use a new deadline`() = runTest {
    setMainDispatcher(testScheduler)
    LibBookLeaveStore.settings = MapSettings()
    var now = LocalDateTime(2026, 11, 17, 9, 0)
    val using =
        LibBookBookingDto(
            "b1",
            day = "2026-11-17",
            endTime = "23:00",
            status = "3",
            statusName = "使用中",
            flagLeave = "1",
            flagOut = "0",
        )
    val api = FakeLibBookApi(listOf(using))
    val notices = mutableListOf<LibBookNotice>()
    val model =
        LibBookViewModel(
            api,
            notifyResult = { notices += it },
            nowProvider = { now },
            onBookingsLoaded = { bookings, date ->
              LibBookLeaveStore.reconcile("alice", bookings, date)
            },
            onLeaveSucceeded = { LibBookLeaveStore.record("alice", it) },
        )
    model.loadBookings()
    advanceUntilIdle()
    model.handleCurrentCardAction(LibBookCardAction.LEAVE, "b1")
    advanceUntilIdle()
    assertEquals(
        LocalDateTime(2026, 11, 17, 9, 30),
        libBookReturnDeadline(notices.last().booking!!),
    )
    now = LocalDateTime(2026, 11, 17, 9, 1)
    api.bookings = listOf(using.copy(status = "7", statusName = "临时离开"))
    model.loadBookings()
    advanceUntilIdle()
    assertFalse(model.uiState.value.bookings.bookings.single().canTemporaryLeave())
    now = LocalDateTime(2026, 11, 17, 9, 10)
    api.bookings = listOf(using)
    model.loadBookings()
    advanceUntilIdle()
    assertTrue(model.uiState.value.bookings.bookings.single().canTemporaryLeave())
    assertFalse(model.uiState.value.bookings.bookings.single().canCancelBooking())
    assertTrue(LibBookLeaveStore.records("alice").isEmpty())
    now = LocalDateTime(2026, 11, 17, 9, 11)
    model.handleCurrentCardAction(LibBookCardAction.LEAVE, "b1")
    advanceUntilIdle()
    assertEquals(
        LocalDateTime(2026, 11, 17, 9, 41),
        libBookReturnDeadline(notices.last().booking!!),
    )
    assertTrue(libBookReturnHint(notices.last().booking!!, now)!!.contains("09:41"))
    assertEquals(2, api.actionRequests.size)
  }

  @Test
  fun `initial load selects first library area slot and loads seats`() = runTest {
    setMainDispatcher(testScheduler)
    val api = FakeLibBookApi()
    val viewModel = LibBookViewModel(api, currentDateProvider = { "2026-05-08" })

    viewModel.ensureInitialLoaded()
    advanceUntilIdle()

    assertEquals("9", viewModel.uiState.value.selectedLibraryId)
    assertEquals("10", viewModel.uiState.value.selectedStoreyId)
    assertEquals("8", viewModel.uiState.value.selectedAreaId)
    assertEquals("seg-1", viewModel.uiState.value.selectedSlotId)
    assertEquals(2, viewModel.uiState.value.seats.size)
    assertEquals(1, api.librariesCalls)
    assertEquals(1, api.areasCalls)
    assertEquals(1, api.seatsCalls)
  }

  @Test
  fun `submit reservation refreshes seats and bookings`() = runTest {
    setMainDispatcher(testScheduler)
    val api = FakeLibBookApi()
    val viewModel = LibBookViewModel(api, currentDateProvider = { "2026-05-08" })

    viewModel.ensureInitialLoaded()
    advanceUntilIdle()
    viewModel.selectSeat("101")
    viewModel.submitReservation()
    advanceUntilIdle()

    assertEquals(1, api.reserveCalls)
    assertEquals(2, api.seatsCalls)
    assertEquals(1, api.bookingsCalls)
    assertEquals("预约成功", viewModel.uiState.value.actionMessage)
    assertEquals(null, viewModel.uiState.value.selectedSeatId)
  }

  @Test
  fun `cancel booking refreshes bookings`() = runTest {
    setMainDispatcher(testScheduler)
    val api = FakeLibBookApi()
    val viewModel = LibBookViewModel(api, currentDateProvider = { "2026-05-08" })

    viewModel.loadBookings()
    advanceUntilIdle()
    viewModel.cancelBooking("b1")
    advanceUntilIdle()

    assertEquals(1, api.cancelCalls)
    assertEquals(2, api.bookingsCalls)
    assertTrue(viewModel.uiState.value.actionMessage?.contains("取消") == true)
  }

  @Test
  fun `cancel ended booking is blocked locally without api call`() = runTest {
    setMainDispatcher(testScheduler)
    val api =
        FakeLibBookApi(
            bookings =
                listOf(
                    LibBookBookingDto(
                        id = "b-ended",
                        nameMerge = "一层 / 002",
                        status = "8",
                        statusName = "已结束",
                    )
                )
        )
    val viewModel = LibBookViewModel(api, currentDateProvider = { "2026-05-08" })

    viewModel.loadBookings()
    advanceUntilIdle()
    viewModel.cancelBooking("b-ended")
    advanceUntilIdle()

    assertEquals(0, api.cancelCalls)
    assertEquals(1, api.bookingsCalls)
    assertEquals("该预约已结束或已取消，无需取消", viewModel.uiState.value.actionMessage)
  }

  @Test
  fun `leave submits booking id without query`() = runTest {
    setMainDispatcher(testScheduler)
    val api = FakeLibBookApi()
    val viewModel = LibBookViewModel(api, currentDateProvider = { "2026-05-08" })

    viewModel.loadBookings()
    advanceUntilIdle()
    val booking = viewModel.uiState.value.bookings.bookings.first()
    viewModel.requestSeatActionForBooking(booking, LibBookSeatAction.LEAVE)
    assertEquals(LibBookSeatAction.LEAVE, viewModel.uiState.value.pendingSeatAction)

    viewModel.submitSeatAction(LibBookSeatAction.LEAVE)
    advanceUntilIdle()

    assertEquals(1, api.actionRequests.size)
    assertEquals("b1", api.actionRequests.single().id)
    assertEquals("", api.actionRequests.single().query)
    assertEquals(null, viewModel.uiState.value.pendingSeatAction)
    assertEquals("临时离开成功", viewModel.uiState.value.actionMessage)
  }

  @Test
  fun `return sign in submits signin without query`() = runTest {
    setMainDispatcher(testScheduler)
    val api =
        FakeLibBookApi(
            bookings =
                listOf(
                    LibBookBookingDto(
                        id = "b1",
                        nameMerge = "一层 / 101",
                        status = "3",
                        statusName = "临时离开",
                        flagIn = "1",
                        flagLeave = "1",
                        flagOut = "1",
                    )
                )
        )
    val viewModel = LibBookViewModel(api, currentDateProvider = { "2026-05-08" })

    viewModel.loadBookings()
    advanceUntilIdle()
    val booking = viewModel.uiState.value.bookings.bookings.first()
    assertTrue(viewModel.prepareReturnSignIn(booking))

    viewModel.submitSeatAction(LibBookSeatAction.RETURN_SIGN_IN)
    advanceUntilIdle()

    assertEquals(1, api.actionRequests.size)
    assertEquals("b1", api.actionRequests.single().id)
    assertEquals("", api.actionRequests.single().query)
    assertEquals("签到成功", viewModel.uiState.value.actionMessage)
  }

  @Test
  fun `prepare return sign in rejects normal booking`() = runTest {
    setMainDispatcher(testScheduler)
    val api = FakeLibBookApi()
    val viewModel = LibBookViewModel(api, currentDateProvider = { "2026-05-08" })

    viewModel.loadBookings()
    advanceUntilIdle()
    val booking = viewModel.uiState.value.bookings.bookings.first()

    assertFalse(viewModel.prepareReturnSignIn(booking))
  }

  private fun setMainDispatcher(scheduler: TestCoroutineScheduler) {
    Dispatchers.setMain(StandardTestDispatcher(scheduler))
  }
}

private class FakeLibBookApi(
    var bookings: List<LibBookBookingDto> =
        listOf(LibBookBookingDto(id = "b1", nameMerge = "一层 / 101", statusName = "已预约"))
) : LibBookApi() {
  var librariesCalls = 0
  var areasCalls = 0
  var seatsCalls = 0
  var reserveCalls = 0
  var bookingsCalls = 0
  var cancelCalls = 0
  val actionRequests = mutableListOf<LibBookSeatActionRequest>()

  override suspend fun getLibraries(day: String): Result<List<LibBookLibraryDto>> {
    librariesCalls++
    return Result.success(
        listOf(
            LibBookLibraryDto(
                id = "9",
                name = "学院路校区图书馆",
                freeNum = 12,
                totalNum = 100,
                storeys = listOf(LibBookStoreyDto("10", "一层", 5, 30)),
            )
        )
    )
  }

  override suspend fun getAreas(
      premisesId: String,
      storeyId: String?,
      day: String,
  ): Result<List<LibBookAreaDto>> {
    areasCalls++
    return Result.success(
        listOf(
            LibBookAreaDto(
                id = "8",
                name = "一层西阅学空间",
                areaName = "学院路",
                freeNum = 2,
                totalNum = 10,
            )
        )
    )
  }

  override suspend fun getAreaDetail(areaId: String): Result<LibBookAreaDetailDto> =
      Result.success(
          LibBookAreaDetailDto(
              id = areaId,
              name = "一层西阅学空间",
              availableDates = listOf("2026-05-08"),
              days =
                  listOf(
                      cn.edu.ubaa.plus.model.dto.LibBookDayScheduleDto(
                          "2026-05-08",
                          listOf(LibBookTimeSlotDto("seg-1", "08:00", "23:00")),
                          "00:00",
                          "23:59",
                          "23:59",
                      )
                  ),
              timeSlots = listOf(LibBookTimeSlotDto("seg-1", "08:00", "23:00")),
          )
      )

  override suspend fun getSeats(
      areaId: String,
      day: String,
      startTime: String,
      endTime: String,
  ): Result<List<LibBookSeatDto>> {
    seatsCalls++
    return Result.success(
        listOf(
            LibBookSeatDto(id = "101", no = "101", status = "1", statusName = "空闲"),
            LibBookSeatDto(id = "102", no = "102", status = "2", statusName = "已占用"),
        )
    )
  }

  override suspend fun reserve(request: LibBookReserveRequest): Result<LibBookReserveResponse> {
    reserveCalls++
    return Result.success(LibBookReserveResponse(true, "预约成功"))
  }

  override suspend fun getBookings(page: Int, limit: Int): Result<LibBookBookingsResponse> {
    bookingsCalls++
    return Result.success(
        LibBookBookingsResponse(
            bookings = bookings,
            page = page,
            limit = limit,
            total = bookings.size,
        )
    )
  }

  override suspend fun cancelBooking(bookingId: String): Result<LibBookCancelResponse> {
    cancelCalls++
    return Result.success(LibBookCancelResponse(success = true, message = "取消成功"))
  }

  override suspend fun signIn(
      request: LibBookSeatActionRequest
  ): Result<LibBookSeatActionResponse> {
    actionRequests += request
    return Result.success(LibBookSeatActionResponse(success = true, message = "签到成功"))
  }

  override suspend fun leave(request: LibBookSeatActionRequest): Result<LibBookSeatActionResponse> {
    actionRequests += request
    return Result.success(LibBookSeatActionResponse(success = true, message = "临时离开成功"))
  }

  override suspend fun checkout(
      request: LibBookSeatActionRequest
  ): Result<LibBookSeatActionResponse> {
    actionRequests += request
    return Result.success(LibBookSeatActionResponse(success = true, message = "已完全离开"))
  }
}
