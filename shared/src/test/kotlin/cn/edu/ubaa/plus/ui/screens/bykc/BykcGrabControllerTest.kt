package cn.edu.ubaa.plus.ui.screens.bykc

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.feature.BykcApi
import cn.edu.ubaa.plus.api.feature.BykcApiBackend
import cn.edu.ubaa.plus.model.dto.BykcChosenCourseDto
import cn.edu.ubaa.plus.model.dto.BykcCourseDetailDto
import cn.edu.ubaa.plus.model.dto.BykcCoursesResponse
import cn.edu.ubaa.plus.model.dto.BykcStatisticsDto
import cn.edu.ubaa.plus.model.dto.BykcSuccessResponse
import cn.edu.ubaa.plus.model.dto.BykcUserProfileDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class BykcGrabControllerTest {

  @Test
  fun `满员后继续重试并抢课成功`() = runTest {
    val backend =
        FakeBykcBackend(
            mutableListOf(
                Result.failure(ApiCallException("人数已满", code = "course_full")),
                Result.success(BykcSuccessResponse("报名成功")),
            )
        )
    val controller = controller(backend)
    var succeeded = 0

    controller.start(1L, "博雅课", null, 1.milliseconds, null) { succeeded++ }
    advanceUntilIdle()

    assertEquals(BykcGrabPhase.SUCCESS, controller.state.value.phase)
    assertEquals(2, backend.selectCalls)
    assertEquals(1, succeeded)
  }

  @Test
  fun `服务端返回已选视为成功`() = runTest {
    val backend =
        FakeBykcBackend(
            mutableListOf(Result.failure(ApiCallException("您已报名过该课程", code = "already_selected")))
        )
    val controller = controller(backend)
    var succeeded = 0

    controller.start(1L, "博雅课", null, 1.milliseconds, null) { succeeded++ }
    advanceUntilIdle()

    assertEquals(BykcGrabPhase.SUCCESS, controller.state.value.phase)
    assertEquals(1, succeeded)
  }

  @Test
  fun `登录失效时停止`() = runTest {
    val backend =
        FakeBykcBackend(
            mutableListOf(Result.failure(ApiCallException("登录状态已失效", code = "unauthenticated")))
        )
    val controller = controller(backend)

    controller.start(1L, "博雅课", null, 1.milliseconds, null) {}
    advanceUntilIdle()

    assertEquals(BykcGrabPhase.FAILED, controller.state.value.phase)
    assertEquals(1, backend.selectCalls)
  }

  @Test
  fun `未知错误重试三次后停止`() = runTest {
    val backend = FakeBykcBackend(mutableListOf(Result.failure(ApiCallException("网络异常"))))
    val controller = controller(backend)

    controller.start(1L, "博雅课", null, 1.milliseconds, null) {}
    advanceUntilIdle()

    assertEquals(BykcGrabPhase.FAILED, controller.state.value.phase)
    assertEquals(3, backend.selectCalls)
  }

  @Test
  fun `应用进入后台时停止`() = runTest {
    val foreground = MutableStateFlow(true)
    val backend =
        FakeBykcBackend(
            mutableListOf(Result.failure(ApiCallException("人数已满", code = "course_full")))
        )
    val controller = controller(backend, foreground)

    controller.start(1L, "博雅课", null, 1.milliseconds, null) {}
    runCurrent()
    foreground.value = false
    advanceUntilIdle()

    assertEquals(BykcGrabPhase.STOPPED, controller.state.value.phase)
    assertEquals(BykcGrabStopReason.BACKGROUND, controller.state.value.stopReason)
  }

  @Test
  fun `定时开抢会等到开始时间再请求`() = runTest {
    val backend = FakeBykcBackend(mutableListOf(Result.success(BykcSuccessResponse("报名成功"))))
    val base = Instant.fromEpochMilliseconds(0)
    val zone = TimeZone.currentSystemDefault()
    val start = (base + 60.seconds).toLocalDateTime(zone)
    val controller = controller(backend, now = { base + testScheduler.currentTime.milliseconds })

    controller.start(1L, "博雅课", start, 1.milliseconds, null) {}
    advanceUntilIdle()

    assertEquals(BykcGrabPhase.SUCCESS, controller.state.value.phase)
    assertEquals(1, backend.selectCalls)
  }

  private fun TestScope.controller(
      backend: FakeBykcBackend,
      foreground: MutableStateFlow<Boolean> = MutableStateFlow(true),
      now: () -> Instant = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
  ): BykcGrabController =
      BykcGrabController(
          api = BykcApi(backend),
          scope = this,
          foreground = foreground,
          now = now,
      )

  private class FakeBykcBackend(
      private val selectResults: MutableList<Result<BykcSuccessResponse>>,
  ) : BykcApiBackend {
    var selectCalls = 0

    override suspend fun getProfile(): Result<BykcUserProfileDto> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getCourses(
        page: Int,
        size: Int,
        all: Boolean,
    ): Result<BykcCoursesResponse> = Result.failure(UnsupportedOperationException())

    override suspend fun getCourseDetail(courseId: Long): Result<BykcCourseDetailDto> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getChosenCourses(): Result<List<BykcChosenCourseDto>> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getStatistics(): Result<BykcStatisticsDto> =
        Result.failure(UnsupportedOperationException())

    override suspend fun selectCourse(courseId: Long): Result<BykcSuccessResponse> {
      selectCalls++
      return if (selectResults.size > 1) selectResults.removeAt(0) else selectResults.first()
    }

    override suspend fun deselectCourse(courseId: Long): Result<BykcSuccessResponse> =
        Result.failure(UnsupportedOperationException())

    override suspend fun signCourse(
        courseId: Long,
        lat: Double?,
        lng: Double?,
        signType: Int,
    ): Result<BykcSuccessResponse> = Result.failure(UnsupportedOperationException())
  }
}
