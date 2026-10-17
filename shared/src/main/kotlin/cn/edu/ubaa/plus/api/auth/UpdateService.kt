package cn.edu.ubaa.plus.api.auth

import cn.edu.ubaa.plus.BuildKonfig
import cn.edu.ubaa.plus.api.core.ApiClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

/** 更新检测服务。 通过服务端检查客户端是否存在新版本。 */
class UpdateService(private val apiClientProvider: () -> ApiClient = { ApiClientProvider.shared }) {
  constructor(apiClient: ApiClient) : this({ apiClient })

  /** 检查当前客户端是否需要更新。 */
  suspend fun checkUpdate(clientVersion: String = BuildKonfig.VERSION): AppVersionCheckResponse? {
    return try {
      val apiClient = apiClientProvider()
      val response =
          apiClient.getClient().get("api/v1/app/version") {
            parameter("clientVersion", clientVersion)
          }
      if (response.status != HttpStatusCode.OK) {
        return null
      }
      response.body<AppVersionCheckResponse>().takeIf {
        it.status == AppUpdateStatus.UPDATE_AVAILABLE
      }
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      null
    }
  }
}
