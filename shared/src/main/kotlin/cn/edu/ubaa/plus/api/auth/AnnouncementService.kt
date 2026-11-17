package cn.edu.ubaa.plus.api.auth

import cn.edu.ubaa.plus.api.core.ApiClient
import cn.edu.ubaa.plus.api.core.PublicApiClientProvider
import cn.edu.ubaa.plus.api.storage.PublicNoticeStore
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/** 公告检测服务。通过服务端读取当前公告配置。 */
class AnnouncementService(
    private val apiClientProvider: () -> ApiClient = { PublicApiClientProvider.shared }
) {
  constructor(apiClient: ApiClient) : this({ apiClient })

  suspend fun checkAnnouncement(): AppAnnouncement? {
    return try {
      val response = apiClientProvider().getClient().get("api/v1/app/announcement")
      if (
          response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.NotFound
      ) {
        PublicNoticeStore.saveAnnouncement(null)
        return null
      }
      if (response.status != HttpStatusCode.OK) return PublicNoticeStore.announcement()
      response.body<AppAnnouncement>().also(PublicNoticeStore::saveAnnouncement)
    } catch (e: Throwable) {
      if (e is CancellationException) throw e
      PublicNoticeStore.announcement()
    }
  }
}
