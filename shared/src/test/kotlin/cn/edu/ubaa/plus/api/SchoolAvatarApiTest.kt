package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.api.local.*
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.testing.TestSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class SchoolAvatarApiTest {
  private val png =
      java.util.Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
          )
  private var wrongOwner = false
  private var badImage = false
  private var images = 0

  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    runTest { localConnectionTestMutex.lock() }
    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    ConnectionRuntime.resolveSelectedMode()
    ScheduleStore.useAccount("synthetic")
    LocalUpstreamClientProvider.reset()
    LocalUpstreamClientProvider.clientFactory = { redirects ->
      HttpClient(
          MockEngine { request ->
            val path = Url(LocalWebVpnSupport.fromWebVpnUrl(request.url.toString())).encodedPath
            if (path == "/personal/frontend/data/info")
                respond(
                    """{"e":0,"d":{"info":{"xgh":"${if(wrongOwner)"other" else "synthetic"}","avatar":"/uploads/synthetic.png"}}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            else {
              assertEquals("/uploads/synthetic.png", path)
              assertTrue(request.attributes.getOrNull(LocalPrivateRead) == true)
              images++
              if (badImage)
                  respond(
                      "<html>login</html>",
                      HttpStatusCode.OK,
                      headersOf(HttpHeaders.ContentType, "text/html"),
                  )
              else respond(png, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/png"))
            }
          }
      ) {
        followRedirects = redirects
      }
    }
  }

  @AfterTest
  fun cleanup() {
    LocalUpstreamClientProvider.reset()
    localConnectionTestMutex.unlock()
  }

  @Test
  fun verifiedPhotoUsesPrivateDownloadAndEncryptedAccountCacheClearedByMaintenance() = runTest {
    val avatar = ItPortalApi().getAvatar().getOrThrow()
    assertEquals("synthetic", avatar.owner)
    assertContentEquals(png, java.util.Base64.getDecoder().decode(avatar.encodedImage))
    assertNotNull(FeatureReadCache.get<SchoolAvatarDto>("profile", "school-avatar"))
    ScheduleStore.useAccount("other")
    assertNull(FeatureReadCache.get<SchoolAvatarDto>("profile", "school-avatar"))
    ScheduleStore.useAccount("synthetic")
    CacheMaintenance.clear()
    assertNull(FeatureReadCache.get<SchoolAvatarDto>("profile", "school-avatar"))
  }

  @Test
  fun mismatchedAccountIsRejectedBeforeDownloadingAnyPhoto() = runTest {
    wrongOwner = true
    assertTrue(ItPortalApi().getAvatar().isFailure)
    assertEquals(0, images)
  }

  @Test
  fun htmlMasqueradingAsAnImageIsNotCached() = runTest {
    badImage = true
    assertTrue(ItPortalApi().getAvatar().isFailure)
    assertNull(FeatureReadCache.get<SchoolAvatarDto>("profile", "school-avatar"))
  }

  @Test
  fun externalAndCredentialBearingImageUrlsAreRejected() {
    for (url in
        listOf(
            "https://evil.example/image.png",
            "https://it.buaa.edu.cn.evil.example/x",
            "https://user@it.buaa.edu.cn/x",
            "http://it.buaa.edu.cn/x",
            "file:///tmp/image.png",
        )) assertFails { schoolAvatarUrl(url) }
    assertEquals("https://it.buaa.edu.cn/uploads/image.png", schoolAvatarUrl("/uploads/image.png"))
  }
}
