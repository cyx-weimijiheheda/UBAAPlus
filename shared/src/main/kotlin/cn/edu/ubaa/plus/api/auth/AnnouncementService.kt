package cn.edu.ubaa.plus.api.auth

import cn.edu.ubaa.plus.api.core.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/** 公告检测服务。固定通过 relay 服务端读取当前公告配置。 */
class AnnouncementService(
    private val apiClientProvider: () -> ApiClient = { ApiClientProvider.shared }
) {
  constructor(apiClient: ApiClient) : this({ apiClient })

  suspend fun checkAnnouncement(): AppAnnouncement? {
    return try {
      val response = apiClientProvider().getClient().get("api/v1/app/announcement")
      if (response.status != HttpStatusCode.OK) {
        return null
      }
      response.body<AppAnnouncement>()
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      null
    }
  }
}
