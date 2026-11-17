package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.*
import cn.edu.ubaa.plus.api.core.ApiClient
import cn.edu.ubaa.plus.api.storage.AuthTokensStore
import cn.edu.ubaa.plus.api.storage.StoredAuthTokens
import cn.edu.ubaa.plus.testing.TestSettings
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class PublicAppServiceTest {
  @Test
  fun knownPublicNoticesSurviveNetworkFailureAndSuccessfulRemovalClearsThem() = runTest {
    TestSettings.installInMemorySettings()
    val store = cn.edu.ubaa.plus.api.storage.PublicNoticeStore
    store.saveAnnouncement(AppAnnouncement("cached", "公告", "内容"))
    store.saveUpdate(
        "1.5.4",
        AppVersionCheckResponse(
            "9.0.0",
            AppUpdateStatus.UPDATE_AVAILABLE,
            true,
            "https://example.invalid/app",
        ),
    )
    val client =
        ApiClient(
            MockEngine { respond("", HttpStatusCode.ServiceUnavailable) },
            authenticated = false,
        )
    try {
      assertEquals("cached", AnnouncementService(client).checkAnnouncement()?.id)
      assertEquals("9.0.0", UpdateService(client).checkUpdate("1.5.4")?.latestVersion)
      assertNull(UpdateService(client).checkUpdate("other-version"))
    } finally {
      client.close()
    }
    val cleared =
        ApiClient(MockEngine { respond("", HttpStatusCode.NoContent) }, authenticated = false)
    try {
      assertNull(AnnouncementService(cleared).checkAnnouncement())
      assertNull(store.announcement())
    } finally {
      cleared.close()
    }
  }

  @Test
  fun updatesAndAnnouncementsWorkWithoutSchoolTransportAndDoNotSendCredentials() = runTest {
    TestSettings.installInMemorySettings()
    ConnectionRuntime.clearSelectedMode()
    var calls = 0
    val engine = MockEngine { request ->
      calls++
      assertNull(request.headers[HttpHeaders.Authorization])
      assertNull(request.headers[HttpHeaders.Cookie])
      val body =
          if (request.url.encodedPath.endsWith("version"))
              """{"latestVersion":"9.0.0","status":"UPDATE_AVAILABLE","updateAvailable":true,"downloadUrl":"https://example.invalid/app"}"""
          else """{"id":"notice","title":"公告","content":"内容"}"""
      respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    val client = ApiClient(engine, authenticated = false)
    try {
      assertNotNull(UpdateService(client).checkUpdate())
      assertEquals("notice", AnnouncementService(client).checkAnnouncement()?.id)
      assertEquals(2, calls)
    } finally {
      client.close()
    }
  }

  @Test
  fun publicUnauthorizedNeverRefreshesOrDeletesStoredSchoolTokens() = runTest {
    TestSettings.installInMemorySettings()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    AuthTokensStore.save(StoredAuthTokens("private-token", "private-refresh"))
    var calls = 0
    val engine = MockEngine { request ->
      calls++
      assertTrue(request.url.encodedPath.endsWith("version"))
      assertNull(request.headers[HttpHeaders.Authorization])
      respond("", HttpStatusCode.Unauthorized)
    }
    val client = ApiClient(engine, authenticated = false)
    try {
      assertNull(UpdateService(client).checkUpdate())
      assertEquals(1, calls)
      assertEquals("private-token", AuthTokensStore.get()?.accessToken)
    } finally {
      client.close()
    }
  }
}
