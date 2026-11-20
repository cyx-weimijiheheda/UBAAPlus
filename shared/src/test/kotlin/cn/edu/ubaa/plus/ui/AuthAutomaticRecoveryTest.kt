package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.*
import cn.edu.ubaa.plus.api.auth.*
import cn.edu.ubaa.plus.api.storage.CredentialStore
import cn.edu.ubaa.plus.model.dto.*
import cn.edu.ubaa.plus.testing.TestSettings
import cn.edu.ubaa.plus.ui.screens.auth.AuthViewModel
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthAutomaticRecoveryTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
    CredentialStore.allowLogin()
    CredentialStore.setRememberPassword(true)
    CredentialStore.saveCredentials("synthetic", "synthetic-password")
    ConnectionRuntime.clearSelectedMode()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
  }

  @AfterTest
  fun cleanup() {
    Dispatchers.resetMain()
    CredentialStore.clear()
    ConnectionRuntime.clearSelectedMode()
    TestSettings.installInMemorySettings()
  }

  private val success = LoginResponse(UserData("Test", "synthetic"), "", "", "", "")
  private val timeout = ApiCallException("请求超时，请稍后重试", status = HttpStatusCode.ServiceUnavailable)

  private fun service(attempt: suspend (Int) -> Result<LoginResponse>, persisted: Boolean = false) =
      object : AuthService() {
        var calls = 0

        override fun hasPersistedSession() = persisted

        override fun applyStoredTokens() {}

        override suspend fun logout(): Result<Unit> = Result.success(Unit)

        override suspend fun getAuthStatus(): Result<SessionStatusResponse> =
            Result.failure(timeout)

        override suspend fun login(
            username: String,
            password: String,
            captcha: String?,
            execution: String?,
        ): Result<LoginResponse> {
          calls++
          return attempt(calls)
        }
      }

  @Test
  fun `主页认证超时立即尝试登录并合并重复恢复请求`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val api = service({ Result.success(success) }, persisted = true)
    val model = AuthViewModel(api, UserService())
    model.initializeApp()
    model.retryAuthentication()
    model.validateSession()
    advanceUntilIdle()
    assertEquals(1, api.calls)
    assertTrue(model.uiState.value.isLoggedIn)
    assertFalse(model.uiState.value.isRecovering)
  }

  @Test
  fun `网络失败自动退避并最终成功而不需要点击重试`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val api = service({ if (it < 3) Result.failure(timeout) else Result.success(success) })
    val model = AuthViewModel(api, UserService())
    model.initializeApp()
    runCurrent()
    assertEquals(1, api.calls)
    assertTrue(model.uiState.value.isRecovering)
    advanceTimeBy(1999)
    runCurrent()
    assertEquals(1, api.calls)
    advanceTimeBy(1)
    runCurrent()
    assertEquals(2, api.calls)
    advanceTimeBy(5000)
    runCurrent()
    assertEquals(3, api.calls)
    assertTrue(model.uiState.value.isLoggedIn)
  }

  @Test
  fun `错误密码和验证码停止重试并转交登录界面`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    for (error in
        listOf(
            ApiCallException("账号或密码错误", code = "invalid_credentials"),
            CaptchaRequiredClientException(
                CaptchaInfo("fixture", imageUrl = "https://example.test/captcha"),
                "fixture",
                "需要验证码",
            ),
        )) {
      val api = service({ Result.failure(error) })
      val model = AuthViewModel(api, UserService())
      model.initializeApp()
      advanceUntilIdle()
      assertEquals(1, api.calls)
      assertTrue(model.uiState.value.needsUserLogin)
      assertFalse(model.uiState.value.isRecovering)
    }
  }

  @Test
  fun `进入后台暂停重试回到前台立即继续且退出后不会自动登录`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val api = service({ Result.failure(timeout) })
    val model = AuthViewModel(api, UserService())
    model.initializeApp()
    runCurrent()
    assertEquals(1, api.calls)
    model.setForeground(false)
    advanceTimeBy(120_000)
    runCurrent()
    assertEquals(1, api.calls)
    model.setForeground(true)
    runCurrent()
    assertEquals(2, api.calls)
    model.requestManualLogin()
    advanceTimeBy(120_000)
    runCurrent()
    assertEquals(2, api.calls)
    assertTrue(model.uiState.value.needsUserLogin)
    model.logout()
    runCurrent()
    assertTrue(CredentialStore.requiresManualLogin())
    model.retryAuthentication()
    runCurrent()
    assertEquals(2, api.calls)
    CredentialStore.allowLogin()
  }

  @Test
  fun `切换路线后丢弃旧登录响应且新路线可以正常恢复`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val replies = mutableListOf<CompletableDeferred<Result<LoginResponse>>>()
    val api =
        object : AuthService() {
          override fun hasPersistedSession() = false

          override suspend fun login(
              username: String,
              password: String,
              captcha: String?,
              execution: String?,
          ): Result<LoginResponse> {
            val reply = CompletableDeferred<Result<LoginResponse>>()
            replies += reply
            return withContext(NonCancellable) { reply.await() }
          }
        }
    val model = AuthViewModel(api, UserService())
    model.initializeApp()
    runCurrent()
    assertEquals(1, replies.size)
    model.switchConnectionMode(ConnectionMode.WEBVPN)
    runCurrent()
    assertEquals(2, replies.size)
    replies[0].complete(Result.success(success.copy(user = UserData("Old", "old-account"))))
    runCurrent()
    assertFalse(model.uiState.value.isLoggedIn)
    replies[1].complete(Result.success(success))
    advanceUntilIdle()
    assertEquals("synthetic", model.uiState.value.userData?.schoolid)
    assertEquals(ConnectionMode.WEBVPN, ConnectionRuntime.currentMode())
  }
}
