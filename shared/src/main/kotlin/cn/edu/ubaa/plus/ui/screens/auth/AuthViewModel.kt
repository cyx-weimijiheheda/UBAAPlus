package cn.edu.ubaa.plus.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.auth.AuthService
import cn.edu.ubaa.plus.api.auth.CaptchaRequiredClientException
import cn.edu.ubaa.plus.api.auth.UserService
import cn.edu.ubaa.plus.api.storage.AuthTokensStore
import cn.edu.ubaa.plus.api.storage.CachedUserStore
import cn.edu.ubaa.plus.api.storage.CredentialStore
import cn.edu.ubaa.plus.model.dto.CaptchaInfo
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.model.dto.UserInfo
import cn.edu.ubaa.plus.repository.ScheduleStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 身份认证与用户信息管理的 ViewModel。 负责处理登录流程（含预加载和验证码）、自动登录逻辑、用户信息加载以及会话状态同步。 */
class AuthViewModel(
    private val authService: AuthService = AuthService(),
    private val userService: UserService = UserService(),
) : ViewModel() {
  private var initializationAttempted = false
  private var authEpoch = 0
  private var preloadJob: Job? = null
  private var captchaJob: Job? = null
  private var loginJob: Job? = null
  private var initializeJob: Job? = null
  private var validationJob: Job? = null
  private var userInfoJob: Job? = null

  private fun cancelAuthentication() {
    cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator.allowOnline(false)
    cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator.invalidate()
    authEpoch++
    listOf(preloadJob, captchaJob, loginJob, initializeJob, validationJob, userInfoJob).forEach {
      it?.cancel()
    }
  }

  private var userInfoLoadedOnce = false
  private var userInfoLoading = false

  private val _uiState = MutableStateFlow(AuthUiState())
  /** 整体认证状态流。 */
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  private val _loginForm = MutableStateFlow(LoginFormState())
  /** 登录表单输入状态流。 */
  val loginForm: StateFlow<LoginFormState> = _loginForm.asStateFlow()

  init {
    loadSavedCredentials()
  }

  /** 从本地持久化存储加载保存的凭据。 */
  private fun loadSavedCredentials() {
    val remember = CredentialStore.isRememberPassword()
    if (remember) {
      val username = CredentialStore.getUsername() ?: ""
      val password = CredentialStore.getPassword() ?: ""
      _loginForm.value =
          _loginForm.value.copy(
              username = username,
              password = password,
              rememberPassword = true,
          )
    } else {
      _loginForm.value = _loginForm.value.copy(rememberPassword = false)
    }
  }

  /** 更新表单中的用户名。 */
  fun updateUsername(username: String) {
    _loginForm.value = _loginForm.value.copy(username = username)
  }

  /** 更新表单中的密码。 */
  fun updatePassword(password: String) {
    _loginForm.value = _loginForm.value.copy(password = password)
  }

  /** 更新表单中的验证码。 */
  fun updateCaptcha(captcha: String) {
    _loginForm.value = _loginForm.value.copy(captcha = captcha)
  }

  /** 更新“记住密码”勾选状态。 */
  fun updateRememberPassword(enabled: Boolean) {
    _loginForm.value = _loginForm.value.copy(rememberPassword = enabled)
  }

  /** 预加载登录状态。 访问服务端探测当前 SSO 是否已登录，并获取登录所需的执行标识（execution）或验证码。 */
  fun preloadLoginState() {
    if (preloadJob?.isActive == true) return
    if (CredentialStore.requiresManualLogin()) return
    val epoch = authEpoch
    preloadJob =
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isPreloading = true, error = null)
          authService
              .preloadLoginState()
              .onSuccess { response ->
                if (epoch != authEpoch) return@onSuccess
                if (response.userData != null) {
                  response.userData?.let(CachedUserStore::remember)
                  // SSO 已登录，执行自动登录逻辑
                  _uiState.value =
                      _uiState.value.copy(
                          isPreloading = false,
                          isLoggedIn = true,
                          userData = response.userData,
                          accessToken = response.accessToken?.takeIf { it.isNotBlank() },
                      )
                  resetUserInfoState()
                  _loginForm.value = LoginFormState()
                } else {
                  _uiState.value =
                      _uiState.value.copy(
                          isPreloading = false,
                          captchaRequired = response.captchaRequired,
                          captchaInfo = response.captcha,
                          execution = response.execution,
                      )
                  _loginForm.value = _loginForm.value.copy(captcha = "")
                }
              }
              .onFailure {
                if (epoch != authEpoch) return@onFailure
                _uiState.value =
                    _uiState.value.copy(isPreloading = false, error = "加载登录状态失败: ${it.message}")
              }
        }
  }

  /** 仅刷新验证码图片，不重置其他表单状态。 */
  fun refreshCaptcha(clearInput: Boolean = false) {
    if (captchaJob?.isActive == true) return
    val epoch = authEpoch
    captchaJob =
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isRefreshingCaptcha = true, error = null)
          if (clearInput) _loginForm.value = _loginForm.value.copy(captcha = "")
          authService
              .preloadLoginState()
              .onSuccess { response ->
                if (epoch != authEpoch) return@onSuccess
                _uiState.value =
                    _uiState.value.copy(
                        isRefreshingCaptcha = false,
                        captchaRequired = response.captchaRequired,
                        captchaInfo = response.captcha,
                        execution = response.execution,
                    )
              }
              .onFailure {
                if (epoch != authEpoch) return@onFailure
                _uiState.value =
                    _uiState.value.copy(
                        isRefreshingCaptcha = false,
                        error = "刷新验证码失败: ${it.message}",
                    )
              }
        }
  }

  /** 执行登录提交。 */
  fun login(automatic: Boolean = false) {
    if (!automatic) CredentialStore.allowLogin()
    val form = _loginForm.value
    val state = _uiState.value
    if (form.username.isBlank() || form.password.isBlank()) {
      _uiState.value = _uiState.value.copy(error = "用户名和密码不能为空")
      return
    }
    if (state.captchaRequired && form.captcha.isBlank()) {
      _uiState.value = _uiState.value.copy(error = "请输入验证码")
      return
    }

    if (loginJob?.isActive == true) return
    val epoch = authEpoch
    loginJob =
        viewModelScope.launch {
          _uiState.value = _uiState.value.copy(isLoading = true, error = null)
          val captcha = if (state.captchaRequired) form.captcha else null
          authService
              .login(form.username, form.password, captcha, state.execution)
              .onSuccess { loginResponse ->
                if (epoch != authEpoch) return@onSuccess
                CachedUserStore.remember(loginResponse.user)
                _uiState.value =
                    _uiState.value.copy(
                        isLoggedIn = true,
                        isLoading = false,
                        userData = loginResponse.user,
                        accessToken = loginResponse.accessToken.takeIf { it.isNotBlank() },
                        error = null,
                        captchaRequired = false,
                    )
                resetUserInfoState()
                CredentialStore.setRememberPassword(form.rememberPassword)
                CredentialStore.setAutoLogin(form.rememberPassword)
                if (form.rememberPassword)
                    CredentialStore.saveCredentials(form.username, form.password)
                if (!form.rememberPassword) _loginForm.value = LoginFormState()
              }
              .onFailure { exception ->
                if (epoch != authEpoch) return@onFailure
                if (exception !is CaptchaRequiredClientException)
                    cn.edu.ubaa.plus.api.storage.LocalDiagnostics.record("auth", "login", exception)
                if (exception is CaptchaRequiredClientException) {
                  _uiState.value =
                      _uiState.value.copy(
                          isLoading = false,
                          captchaRequired = true,
                          captchaInfo = exception.captcha,
                          execution = exception.execution,
                          error = "需要补全验证码后登录",
                      )
                } else {
                  val message = exception.message ?: "登录失败"
                  _uiState.value = _uiState.value.copy(isLoading = false, error = message)
                  // 输错验证码时上游只回一句 "Invalid parameter" 或"登录失败"。验证码是一次性的，失败后必须换一张，
                  // 否则用户对着旧图重试只会一直失败。这里只要本次提交带了验证码就刷新，不依赖 captchaRequired
                  // 是否仍为 true——WebVPN 失败后登录页会被重绘，那个标志并不可靠。
                  if (!captcha.isNullOrBlank() || isCaptchaFailure(message)) {
                    refreshCaptcha(clearInput = true)
                  }
                }
              }
        }
  }

  /** 应用全局初始化入口。 用于检查本地 Token 是否有效，若失效则根据设置决定跳转登录页或尝试自动登录。 */
  fun retryAuthentication() {
    initializationAttempted = false
    initializeApp()
  }

  fun initializeApp() {
    if (CredentialStore.requiresManualLogin() || initializationAttempted) return
    if (ConnectionRuntime.currentMode() == null) return
    if (
        initializeJob?.isActive == true ||
            loginJob?.isActive == true ||
            validationJob?.isActive == true
    )
        return
    initializationAttempted = true
    val epoch = authEpoch
    initializeJob =
        viewModelScope.launch {
          val restoredAccessToken =
              runCatching { AuthTokensStore.get()?.accessToken?.takeIf { it.isNotBlank() } }
                  .getOrNull()
          if (!runCatching { authService.hasPersistedSession() }.getOrDefault(false)) {
            if (CredentialStore.canAutomaticallyAuthenticate()) login(automatic = true)
            else preloadLoginState()
            return@launch
          }

          authService.applyStoredTokens()
          _uiState.value = _uiState.value.copy(isLoading = true)
          authService
              .getAuthStatus()
              .onSuccess { status ->
                if (epoch != authEpoch) return@onSuccess
                CachedUserStore.remember(status.user)
                _uiState.value =
                    _uiState.value.copy(
                        isLoggedIn = true,
                        isLoading = false,
                        userData = status.user,
                        accessToken = restoredAccessToken,
                        error = null,
                    )
                resetUserInfoState()
              }
              .onFailure { error ->
                if (epoch != authEpoch) return@onFailure
                cn.edu.ubaa.plus.api.storage.LocalDiagnostics.record("auth", "restore", error)
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (!isExpiredSession(error)) {
                  _uiState.value = _uiState.value.copy(error = error.message)
                  return@onFailure
                }
                authService.clearStoredSession()
                if (CredentialStore.canAutomaticallyAuthenticate()) login(automatic = true)
                else preloadLoginState()
              }
        }
  }

  fun switchConnectionMode(mode: ConnectionMode) {
    cancelAuthentication()
    initializationAttempted = false
    _uiState.value = AuthUiState(isLoading = true)
    viewModelScope.launch {
      ConnectionRuntime.switchModeAsync(mode)
      resetUserInfoState()
      _uiState.value = AuthUiState()
      loadSavedCredentials()
      if (!CredentialStore.requiresManualLogin()) retryAuthentication()
    }
  }

  /** Ordinary logout retains local data; destructive cleanup is an explicit second choice. */
  fun logout(clearLocalData: Boolean = false) {
    val owner = _uiState.value.userData?.schoolid ?: ScheduleStore.account()
    CredentialStore.requireManualLogin()
    cancelAuthentication()
    initializationAttempted = false
    _uiState.value = AuthUiState(isLoading = true)
    viewModelScope.launch {
      val cleanup = runCatching {
        if (clearLocalData && owner != null)
            cn.edu.ubaa.plus.api.storage.CacheMaintenance.clearAccount(owner)
      }
      runCatching { authService.logout() }
      AuthTokensStore.clearAllScopes()
      cn.edu.ubaa.plus.api.local.LocalAuthSessionStore.clearAllScopes()
      cn.edu.ubaa.plus.api.local.LocalCookieStore.clearAllScopes()
      cn.edu.ubaa.plus.api.local.LocalUpstreamClientProvider.reset()
      if (clearLocalData) {
        CachedUserStore.clear()
        ScheduleStore.forgetAccount()
        CredentialStore.clear()
      }
      resetUserInfoState()
      _loginForm.value = LoginFormState()
      loadSavedCredentials()
      _uiState.value = AuthUiState(error = if (cleanup.isFailure) "已退出登录，但本地数据清理失败，请重试清理" else null)
    }
  }

  /** 按需加载用户的详细档案信息。 */
  fun ensureUserInfoLoaded(forceRefresh: Boolean = false) {
    if (userInfoLoading) return
    if (!forceRefresh && (userInfoLoadedOnce || _uiState.value.userInfo != null)) {
      return
    }
    userInfoLoading = true
    if (userInfoJob?.isActive == true) return
    val epoch = authEpoch
    userInfoJob =
        viewModelScope.launch {
          userService
              .getUserInfo()
              .onSuccess { info ->
                if (epoch != authEpoch) return@onSuccess
                userInfoLoadedOnce = true
                _uiState.value = _uiState.value.copy(userInfo = info)
              }
              .onFailure { if (epoch == authEpoch) userInfoLoadedOnce = false }
          userInfoLoading = false
        }
  }

  /** 前台恢复时验证当前会话是否仍然有效。 如果会话过期或网络暂时不可用，根据错误类型决定保留还是清除会话。 */
  fun validateSession() {
    if (!_uiState.value.isLoggedIn) return
    if (validationJob?.isActive == true) return
    val epoch = authEpoch
    validationJob =
        viewModelScope.launch {
          authService
              .getAuthStatus()
              .onSuccess { status ->
                if (epoch != authEpoch) return@onSuccess
                CachedUserStore.remember(status.user)
                _uiState.value =
                    _uiState.value.copy(
                        isLoggedIn = true,
                        userData = status.user,
                    )
              }
              .onFailure { error ->
                if (epoch != authEpoch) return@onFailure
                cn.edu.ubaa.plus.api.storage.LocalDiagnostics.record("auth", "restore", error)
                if (!isExpiredSession(error)) {
                  // 网络暂时不可用，保留会话状态
                  return@onFailure
                }
                // 会话确实过期了
                authService.clearStoredSession()
                resetUserInfoState()
                _uiState.value = AuthUiState()
                if (CredentialStore.canAutomaticallyAuthenticate()) login(automatic = true)
                else preloadLoginState()
              }
        }
  }

  /** 清除当前的错误提示。 */
  fun clearError() {
    _uiState.value = _uiState.value.copy(error = null)
  }

  private fun resetUserInfoState() {
    userInfoLoadedOnce = false
    userInfoLoading = false
    _uiState.value = _uiState.value.copy(userInfo = null)
  }
}

/** 认证模块 UI 状态模型。 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isPreloading: Boolean = false,
    val isRefreshingCaptcha: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userData: UserData? = null,
    val userInfo: UserInfo? = null,
    val accessToken: String? = null,
    val error: String? = null,
    val captchaRequired: Boolean = false,
    val captchaInfo: CaptchaInfo? = null,
    val execution: String? = null,
)

/** 登录表单本地交互状态模型。 */
data class LoginFormState(
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val rememberPassword: Boolean = false,
)

/**
 * 判断失败信息是否与验证码有关。
 *
 * WebVPN 模式下输错验证码时，上游不会给出明确文案，只会回 "Invalid parameter" / "登录失败" 之类， 或者干脆 401。
 * 只要当前登录确实需要验证码，这些情况都视为需要换一张验证码。
 */
internal fun isCaptchaFailure(message: String?): Boolean {
  val text = message?.trim()?.lowercase().orEmpty()
  if (text.isEmpty()) return true
  return captchaFailureKeywords.any { text.contains(it) }
}

private val captchaFailureKeywords =
    listOf(
        "invalid parameter",
        "invalid_parameter",
        "验证码",
        "captcha",
        "登录失败",
        "账号或密码错误",
        "unauthorized",
        "参数不正确",
        "参数错误",
    )

internal fun isExpiredSession(error: Throwable): Boolean =
    error is ApiCallException &&
        (error.status?.value == 401 ||
            error.code in
                setOf(
                    "unauthorized",
                    "unauthenticated",
                    "invalid_token",
                    "invalid_refresh_token",
                    "auth_required",
                    "session_expired",
                    "not_logged_in",
                ))
