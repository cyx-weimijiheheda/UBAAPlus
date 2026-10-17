package cn.edu.ubaa.plus.api.feature

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.ApiClientProvider
import cn.edu.ubaa.plus.api.auth.safeApiCall
import cn.edu.ubaa.plus.api.core.ApiClient
import cn.edu.ubaa.plus.api.local.LocalSigninApiBackend
import cn.edu.ubaa.plus.model.dto.*
import io.ktor.client.request.*

interface SigninApiBackend {
  suspend fun getTodayClasses(): Result<SigninStatusResponse>

  suspend fun performSignin(
      courseId: String,
      timestamp: String? = null,
  ): Result<SigninActionResponse>

  /** 本学期考勤明细。 */
  suspend fun getAttendance(): Result<SigninAttendanceResponse>
}

/** 课堂签到 API 服务。 用于查询今日可签到的课堂及执行签到动作。 */
class SigninApi(
    private val backendProvider: () -> SigninApiBackend = {
      ConnectionRuntime.apiFactory().signinApi()
    }
) {
  internal constructor(backend: SigninApiBackend) : this({ backend })

  constructor(apiClient: ApiClient) : this({ RelaySigninApiBackend(apiClient) })

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

  /** 查询本学期考勤明细。 */
  suspend fun getAttendance(): Result<SigninAttendanceResponse> {
    return currentBackend().getAttendance()
  }
}

internal class RelaySigninApiBackend(private val apiClient: ApiClient = ApiClientProvider.shared) :
    SigninApiBackend {
  // 中转服务端没有考勤接口；设备能直连 iClass 时用本地实现兜底。
  private val localAttendanceBackend by lazy { LocalSigninApiBackend() }

  override suspend fun getTodayClasses(): Result<SigninStatusResponse> {
    return safeApiCall { apiClient.getClient().get("api/v1/signin/today") }
  }

  override suspend fun performSignin(
      courseId: String,
      timestamp: String?,
  ): Result<SigninActionResponse> {
    return safeApiCall {
      apiClient.getClient().post("api/v1/signin/do") { parameter("courseId", courseId) }
    }
  }

  override suspend fun getAttendance(): Result<SigninAttendanceResponse> =
      localAttendanceBackend.getAttendance()
}
