package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.model.dto.*

interface SigninApiBackend {
  suspend fun getTodayClasses(): Result<SigninStatusResponse>

  /** 后台提醒使用：网络/解析失败返回 failure，而不是空列表。 */
  suspend fun getTodayClassesStrict(): Result<SigninStatusResponse> = getTodayClasses()

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

  /**
   * 当前课时的排课信息，用于拼课堂签到链接。
   *
   * 不在课时时 `courseSchedId` 为 `0`，其余字段为空。
   */
  suspend fun getCurrentSocketInfo(): Result<IclassSocketInfoDto> =
      Result.failure(UnsupportedOperationException("当前连接方式不支持读取课时信息"))

  /**
   * 读取服务端毫秒时间戳，用于拼签到链接。
   *
   * 必须是整数：上游把字符串时间戳当成无效参数并静默丢弃。
   */
  suspend fun currentTimestamp(): Long? = null
}

/**
 * 当前课时信息。
 *
 * @property courseSchedId 排课 ID，`0` 表示当前没有正在进行的课。
 * @property courseName 课程名，可能为空。
 * @property classBeginTime 本堂课开始时间。
 * @property classEndTime 本堂课结束时间。
 */
data class IclassSocketInfoDto(
    val courseSchedId: String = "0",
    val courseName: String = "",
    val classBeginTime: String = "",
    val classEndTime: String = "",
) {
  /** 是否处于某节课内；只有此时签到链接才有意义。 */
  val hasCurrentCourse: Boolean
    get() = IclassSigninLink.isUsableCourseSchedId(courseSchedId)
}

/** 课堂签到 API 服务。 用于查询今日可签到的课堂及执行签到动作。 */
class SigninApi(
    private val backendProvider: () -> SigninApiBackend = {
      ConnectionRuntime.apiFactory().signinApi()
    }
) {
  internal constructor(backend: SigninApiBackend) : this({ backend })

  private fun currentBackend(): SigninApiBackend = backendProvider()

  /** 当前课时的排课信息，用于拼课堂签到链接。 */
  suspend fun getCurrentSocketInfo(): Result<IclassSocketInfoDto> =
      currentBackend().getCurrentSocketInfo()

  /** 服务端毫秒时间戳，用于拼课堂签到链接。 */
  suspend fun currentTimestamp(): Long? = currentBackend().currentTimestamp()

  /**
   * 获取今日所有有签到任务的课堂列表。
   *
   * @return 签到状态响应，包含课堂列表。
   */
  suspend fun getTodayClasses(): Result<SigninStatusResponse> {
    return currentBackend().getTodayClasses()
  }

  suspend fun getTodayClassesStrict(): Result<SigninStatusResponse> {
    return currentBackend().getTodayClassesStrict()
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
    return cn.edu.ubaa.plus.api.recovery.SchoolWriteCoordinator.perform(
        "signin",
        "signin:$courseId",
        accepted = { it.success },
        submit = { currentBackend().performSignin(courseId, timestamp) },
        verify = {
          currentBackend()
              .getTodayClassesStrict()
              .getOrNull()
              ?.data
              ?.firstOrNull { it.courseId == courseId && it.signStatus == 1 }
              ?.let { SigninActionResponse(0, true, "已核实当前课程为已签到") }
        },
    )
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
