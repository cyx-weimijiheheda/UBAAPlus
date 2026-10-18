package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.model.dto.*

interface SigninApiBackend {
  suspend fun getTodayClasses(): Result<SigninStatusResponse>

  suspend fun performSignin(
      courseId: String,
      timestamp: String? = null,
  ): Result<SigninActionResponse>

  /** 可用于考勤查询的学年学期。 */
  suspend fun getAttendanceTerms(): Result<List<IclassAttendanceTermDto>>

  /** 指定学年学期的课程列表。 */
  suspend fun getAttendanceCourses(termCode: String): Result<List<IclassAttendanceCourseDto>>

  /** 指定学年学期、指定课程的考勤明细。 */
  suspend fun getAttendance(
      termCode: String,
      courseId: String,
  ): Result<SigninAttendanceResponse>
}

/** 课堂签到 API 服务。 用于查询今日可签到的课堂及执行签到动作。 */
class SigninApi(
    private val backendProvider: () -> SigninApiBackend = {
      ConnectionRuntime.apiFactory().signinApi()
    }
) {
  internal constructor(backend: SigninApiBackend) : this({ backend })

  private fun currentBackend(): SigninApiBackend = backendProvider()

  /**
   * 获取今日所有有签到任务的课堂列表。
   *
   * @return 签到状态响应，包含课堂列表。
   */
  suspend fun getTodayClasses(): Result<SigninStatusResponse> {
    return currentBackend().getTodayClasses()
  }

  /**
   * 执行课堂签到。
   *
   * @param courseId 课程安排 ID。
   * @param timestamp 扫码二维码中携带的服务器时间戳；为空时由接口重新获取。
   * @return 签到操作执行结果。
   */
  suspend fun performSignin(
      courseId: String,
      timestamp: String? = null,
  ): Result<SigninActionResponse> {
    return currentBackend().performSignin(courseId, timestamp)
  }

  /** 查询可选学年学期。 */
  suspend fun getAttendanceTerms(): Result<List<IclassAttendanceTermDto>> {
    return currentBackend().getAttendanceTerms()
  }

  /** 查询指定学年学期的课程。 */
  suspend fun getAttendanceCourses(termCode: String): Result<List<IclassAttendanceCourseDto>> {
    return currentBackend().getAttendanceCourses(termCode)
  }

  /** 查询指定学年学期、指定课程的考勤明细。 */
  suspend fun getAttendance(
      termCode: String,
      courseId: String,
  ): Result<SigninAttendanceResponse> {
    return currentBackend().getAttendance(termCode, courseId)
  }
}
