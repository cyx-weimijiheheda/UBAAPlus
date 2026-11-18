package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.*
import cn.edu.ubaa.plus.api.auth.AuthService
import cn.edu.ubaa.plus.api.local.*
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.model.dto.LoginPreloadResponse
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.repository.*
import cn.edu.ubaa.plus.testing.TestSettings
import cn.edu.ubaa.plus.ui.screens.auth.AuthViewModel
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthLogoutCleanupTest {
  @Test
  fun `服务端注销失败仍删除本机账号数据和凭据`() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
    try {
      TestSettings.installInMemorySettings()
      ConnectionModeStore.save(ConnectionMode.DIRECT)
      ConnectionRuntime.resolveSelectedMode()
      ScheduleStore.useAccount("alice")
      ScheduleStore.write(
          "alice",
          listOf(SemesterSchedule(emptyList(), "term", emptyList(), emptyMap())),
      )
      CredentialStore.saveCredentials("alice", "synthetic-password")
      CachedUserStore.remember(UserData("Synthetic", "alice"))
      AuthTokensStore.save(StoredAuthTokens("synthetic-token", "synthetic-refresh"))
      LocalAuthSessionStore.save(
          LocalAuthSession("alice", UserData("Synthetic", "alice"), "now", "now")
      )
      val auth =
          object : AuthService() {
            override suspend fun logout(): Result<Unit> =
                Result.failure(IllegalStateException("synthetic offline"))

            override suspend fun preloadLoginState(): Result<LoginPreloadResponse> =
                Result.failure(IllegalStateException("synthetic offline"))
          }
      val model = AuthViewModel(authService = auth)
      model.logout()
      advanceUntilIdle()
      assertNull(ScheduleStore.account())
      assertEquals(emptyList(), ScheduleStore.read("alice"))
      assertNull(CachedUserStore.current())
      assertNull(CredentialStore.getUsername())
      assertNull(CredentialStore.getPassword())
      assertNull(AuthTokensStore.get())
      assertNull(LocalAuthSessionStore.get())
      assertFalse(model.uiState.value.isLoggedIn)
    } finally {
      Dispatchers.resetMain()
    }
  }
}
