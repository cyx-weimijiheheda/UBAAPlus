package cn.edu.ubaa.plus.api.venue

import cn.edu.ubaa.plus.api.auth.ApiCallException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class VenueGrabControllerTest {

  @Test
  fun `空闲场地立即提交并成功`() = runTest {
    val backend = FakeVenueBackend(dayInfo(free = true))
    val controller = controller(backend)

    controller.start(task())
    advanceUntilIdle()

    assertEquals(VenueGrabPhase.SUCCESS, controller.state.value.phase)
    assertEquals(1, backend.submitCalls)
  }

  @Test
  fun `可重试错误后继续尝试并成功`() = runTest {
    val backend =
        FakeVenueBackend(
            day = dayInfo(free = true),
            submitResults =
                mutableListOf(
                    Result.failure(ApiCallException("该场地已被预约")),
                    Result.success(order()),
                ),
        )
    val controller = controller(backend)

    controller.start(task())
    advanceUntilIdle()

    assertEquals(VenueGrabPhase.SUCCESS, controller.state.value.phase)
    assertEquals(2, backend.submitCalls)
  }

  @Test
  fun `不可重试错误立即停止`() = runTest {
    val backend =
        FakeVenueBackend(
            day = dayInfo(free = true),
            submitResults = mutableListOf(Result.failure(ApiCallException("每天只能预约2次"))),
        )
    val controller = controller(backend)

    controller.start(task())
    advanceUntilIdle()

    assertEquals(VenueGrabPhase.FAILED, controller.state.value.phase)
    assertEquals(1, backend.submitCalls)
  }

  @Test
  fun `应用进入后台时停止任务`() = runTest {
    val foreground = MutableStateFlow(true)
    val backend = FakeVenueBackend(dayInfo(free = false))
    val controller = controller(backend, foreground)

    controller.start(task())
    runCurrent()
    foreground.value = false
    advanceUntilIdle()

    assertEquals(VenueGrabPhase.STOPPED, controller.state.value.phase)
    assertEquals(VenueGrabStopReason.BACKGROUND, controller.state.value.stopReason)
  }

  @Test
  fun `验证码提交后继续下单`() = runTest {
    val backend = FakeVenueBackend(dayInfo(free = true, captcha = true))
    val controller = controller(backend)

    controller.start(task())
    runCurrent()
    assertEquals(VenueGrabPhase.NEEDS_CAPTCHA, controller.state.value.phase)

    controller.submitCaptcha(
        VenueCaptchaAnswer(points = listOf(VenuePoint(1f, 2f)), imageWidth = 100, imageHeight = 50)
    )
    advanceUntilIdle()

    assertEquals(VenueGrabPhase.SUCCESS, controller.state.value.phase)
    assertEquals(1, backend.verifyCalls)
    assertEquals(1, backend.submitCalls)
  }

  @Test
  fun `取消验证码会停止任务`() = runTest {
    val backend = FakeVenueBackend(dayInfo(free = true, captcha = true))
    val controller = controller(backend)

    controller.start(task())
    runCurrent()
    controller.cancelCaptcha()
    advanceUntilIdle()

    assertEquals(VenueGrabPhase.STOPPED, controller.state.value.phase)
    assertEquals(VenueGrabStopReason.CAPTCHA_CANCELLED, controller.state.value.stopReason)
    assertEquals(0, backend.submitCalls)
  }

  private fun kotlinx.coroutines.test.TestScope.controller(
      backend: FakeVenueBackend,
      foreground: MutableStateFlow<Boolean> = MutableStateFlow(true),
  ): VenueGrabController =
      VenueGrabController(
          api = VenueApi(backend),
          scope = this,
          foreground = foreground,
          now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
      )

  private fun task(): VenueGrabTask =
      VenueGrabTask(
          siteId = 1,
          date = "2026-09-26",
          startBeginTime = "19:00",
          durationHours = 1,
          spaceMode = VenueSpaceMode.AUTO,
          pollInterval = 1.milliseconds,
          grabAt = null,
      )

  private fun order(): VenueOrderResult =
      VenueOrderResult(
          orderId = 1,
          tradeNo = "D1",
          startAt = "2026-09-26 19:00",
          endAt = "2026-09-26 20:00",
          message = "预约成功",
      )

  private fun dayInfo(free: Boolean, captcha: Boolean = false): VenueDayInfo {
    val slot = VenueTimeSlot(7700, "19:00", "20:00")
    val space =
        VenueSpace(
            spaceId = 236,
            spaceName = "3号",
            groupId = null,
            slots =
                mapOf(
                    7700 to
                        VenueSpaceSlot(
                            status =
                                if (free) VenueSpaceSlot.STATUS_FREE
                                else VenueSpaceSlot.STATUS_RESERVED,
                            fee = null,
                            remark = null,
                        )
                ),
        )
    return VenueDayInfo(
        siteId = 1,
        date = "2026-09-26",
        orderToken = "order-token",
        isFee = false,
        slots = listOf(slot),
        spaces = listOf(space),
        phone = null,
        isCaptchaCheck = captcha,
        buddies = emptyList(),
        buddyNumMin = 0,
        buddyNumMax = 3,
        reservationHour = 1,
    )
  }

  private class FakeVenueBackend(
      var day: VenueDayInfo?,
      val submitResults: MutableList<Result<VenueOrderResult>> = mutableListOf(),
  ) : VenueApiBackend {
    var submitCalls = 0
    var verifyCalls = 0

    override suspend fun resumeSession(): VenueAuthOutcome = VenueAuthOutcome.Ready

    override suspend fun login(
        username: String,
        password: String,
        captcha: String?,
        execution: String?,
    ): VenueAuthOutcome = VenueAuthOutcome.Ready

    override fun logout() {}

    override suspend fun getSites(): Result<VenueSiteList> =
        Result.success(VenueSiteList(emptyList(), 3))

    override suspend fun getDayInfo(siteId: Int, date: String): Result<VenueDayInfo?> =
        Result.success(day)

    override suspend fun getBuddies(): Result<List<VenueBuddy>> = Result.success(emptyList())

    override suspend fun addBuddy(userUid: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteBuddy(buddyId: Int): Result<Unit> = Result.success(Unit)

    override suspend fun getCaptcha(): Result<VenueCaptchaChallenge> =
        Result.success(
            VenueCaptchaChallenge(
                token = "captcha-token",
                secretKey = "abcdefghijklmnop",
                wordList = listOf("一"),
                imageBase64 = "",
            )
        )

    override suspend fun verifyCaptcha(
        challenge: VenueCaptchaChallenge,
        points: List<VenuePoint>,
        imageWidth: Int,
        imageHeight: Int,
    ): Result<VenueCaptchaResult> {
      verifyCalls++
      return Result.success(VenueCaptchaResult("verification", challenge.token))
    }

    override suspend fun submitOrder(request: VenueOrderRequest): Result<VenueOrderResult> {
      submitCalls++
      return if (submitResults.isNotEmpty()) submitResults.removeAt(0) else Result.success(order())
    }

    override suspend fun pay(tradeNo: String, payType: Int): Result<VenuePayResult> =
        Result.success(VenuePayResult(null, null, null, "ok"))

    private fun order(): VenueOrderResult =
        VenueOrderResult(1, "D1", "2026-09-26 19:00", "2026-09-26 20:00", "预约成功")
  }
}
