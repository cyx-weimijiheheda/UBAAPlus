package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.auth.AuthService
import cn.edu.ubaa.plus.api.core.DefaultApiFactory
import cn.edu.ubaa.plus.api.local.LocalAuthSession
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.storage.AuthTokensStore
import cn.edu.ubaa.plus.api.storage.CredentialStore
import cn.edu.ubaa.plus.api.storage.StoredAuthTokens
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.testing.TestSettings
import com.russhwolf.settings.MapSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionRuntimeTest {

  @BeforeTest
  fun setup() {
    // Settings() 在 Android 单元测试里拿不到 Application context，注入内存实现
    TestSettings.installInMemorySettings()
    ConnectionModeStore.settings = MapSettings()
    AuthTokensStore.settings = MapSettings()
    CredentialStore.settings = MapSettings()
    LocalAuthSessionStore.settings = MapSettings()
    LocalCookieStore.settings = MapSettings()
    ConnectionRuntime.clearSelectedMode()
    AuthTokensStore.clearAllScopes()
    CredentialStore.clear()
    LocalAuthSessionStore.clearAllScopes()
    LocalCookieStore.clearAllScopes()
    ConnectionRuntime.apiFactoryProvider = { DefaultApiFactory }
  }

  @Test
  fun `switching connection mode clears all scoped sessions and disables auto login`() {
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    AuthTokensStore.save(
        StoredAuthTokens(
            accessToken = "direct-access",
            refreshToken = "direct-refresh",
        )
    )
    LocalAuthSessionStore.save(
        LocalAuthSession(
            username = "22373333",
            user = UserData(name = "Test User", schoolid = "22373333"),
            authenticatedAt = "2026-04-20T08:00:00Z",
            lastActivity = "2026-04-20T08:30:00Z",
        )
    )

    CredentialStore.saveCredentials("22373333", "secret")
    CredentialStore.setRememberPassword(true)
    CredentialStore.setAutoLogin(true)

    ConnectionRuntime.switchMode(ConnectionMode.WEBVPN)

    assertEquals(ConnectionMode.WEBVPN, ConnectionRuntime.currentMode())
    assertNull(AuthTokensStore.get())
    assertNull(LocalAuthSessionStore.get())
    assertFalse(CredentialStore.isAutoLogin())
    assertTrue(CredentialStore.isRememberPassword())
    assertEquals("22373333", CredentialStore.getUsername())
    assertEquals("secret", CredentialStore.getPassword())

    ConnectionModeStore.save(ConnectionMode.DIRECT)
    assertNull(AuthTokensStore.get())
  }

  @Test
  fun `direct mode auth service uses local persisted session contract by default`() {
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    LocalAuthSessionStore.save(
        LocalAuthSession(
            username = "22373333",
            user = UserData(name = "Test User", schoolid = "22373333"),
            authenticatedAt = "2026-04-20T08:00:00Z",
            lastActivity = "2026-04-20T08:30:00Z",
        )
    )

    assertTrue(AuthService().hasPersistedSession())

    ConnectionModeStore.save(ConnectionMode.WEBVPN)
    assertFalse(AuthService().hasPersistedSession())
  }
}
