package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.*
import cn.edu.ubaa.plus.api.auth.*
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.model.dto.*
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.testing.TestSettings
import cn.edu.ubaa.plus.ui.screens.auth.AuthViewModel
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRecoveryTest {
  @BeforeTest
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    TestSettings.installInMemorySettings()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    CredentialStore.setRememberPassword(true)
    CredentialStore.saveCredentials("alice", "synthetic")
    CredentialStore.setAutoLogin(false) // legacy preference no longer blocks saved credentials
  }

  @AfterTest
  fun cleanup() {
    Dispatchers.resetMain()
  }

  private class FakeAuth(var persisted: Boolean = false) : AuthService() {
    var logins = 0
    var statuses = 0
    var clears = 0
    var status: Result<SessionStatusResponse> =
        Result.failure(ApiCallException("expired", code = "unauthenticated"))

    override fun hasPersistedSession() = persisted

    override fun applyStoredTokens() {}

    override fun clearStoredSession() {
      clears++
    }

    override suspend fun getAuthStatus(): Result<SessionStatusResponse> {
      statuses++
      return status
    }

    override suspend fun login(
        username: String,
        password: String,
        captcha: String?,
        execution: String?,
    ): Result<LoginResponse> {
      logins++
      return Result.success(LoginResponse(UserData("Alice", username), "token", "refresh", "", ""))
    }

    override suspend fun logout(): Result<Unit> = Result.failure(IllegalStateException("offline"))
  }

  @Test
  fun savedCredentialsAutomaticallyLoginOnceEvenWithLegacyAutoOff() = runTest {
    val auth = FakeAuth()
    val model = AuthViewModel(auth)
    model.initializeApp()
    model.initializeApp()
    advanceUntilIdle()
    assertEquals(1, auth.logins)
    assertTrue(model.uiState.value.isLoggedIn)
  }

  @Test
  fun expiredSessionFallsBackToPassword() = runTest {
    val auth = FakeAuth(true)
    val model = AuthViewModel(auth)
    model.initializeApp()
    advanceUntilIdle()
    assertEquals(1, auth.statuses)
    assertEquals(1, auth.clears)
    assertEquals(1, auth.logins)
  }

  @Test
  fun timeoutPreservesSessionAndCachedIdentityUntilExplicitRetry() = runTest {
    CachedUserStore.remember(UserData("Alice", "alice"))
    val auth =
        FakeAuth(true).apply {
          status = Result.failure(ApiCallException("timeout", code = "auth_upstream_timeout"))
        }
    val model = AuthViewModel(auth)
    model.initializeApp()
    model.initializeApp()
    advanceUntilIdle()
    assertEquals(0, auth.logins)
    assertEquals(0, auth.clears)
    assertNotNull(CachedUserStore.current())
    assertEquals("timeout", model.uiState.value.error)
    model.retryAuthentication()
    advanceUntilIdle()
    assertEquals(2, auth.statuses)
  }

  @Test
  fun ordinaryLogoutKeepsDataAndPersistentlyBlocksAutomaticRecovery() = runTest {
    CachedUserStore.remember(UserData("Alice", "alice"))
    val auth = FakeAuth()
    val model = AuthViewModel(auth)
    model.logout()
    advanceUntilIdle()
    assertEquals("alice", ScheduleStore.account())
    assertNotNull(CachedUserStore.current())
    assertEquals("synthetic", CredentialStore.getPassword())
    assertTrue(CredentialStore.requiresManualLogin())
    assertFalse(CredentialStore.canAutomaticallyAuthenticate())
    AuthViewModel(auth).initializeApp()
    advanceUntilIdle()
    assertEquals(0, auth.logins)
    model.login()
    advanceUntilIdle()
    assertFalse(CredentialStore.requiresManualLogin())
    assertEquals(1, auth.logins)
  }
}
