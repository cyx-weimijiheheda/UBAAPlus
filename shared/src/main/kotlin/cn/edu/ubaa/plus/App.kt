package cn.edu.ubaa.plus

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.AnnouncementService
import cn.edu.ubaa.plus.api.auth.AppAnnouncement
import cn.edu.ubaa.plus.api.auth.AppVersionCheckResponse
import cn.edu.ubaa.plus.api.auth.UpdateService
import cn.edu.ubaa.plus.api.storage.AnnouncementReadStore
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.api.storage.CachedUserStore
import cn.edu.ubaa.plus.api.storage.DisclaimerStore
import cn.edu.ubaa.plus.api.storage.ThemeMode
import cn.edu.ubaa.plus.reminder.ReminderServiceController
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.common.components.FirstRunDisclaimerDialog
import cn.edu.ubaa.plus.ui.common.components.ReleaseNotesText
import cn.edu.ubaa.plus.ui.navigation.MainAppScreen
import cn.edu.ubaa.plus.ui.screens.auth.AuthViewModel
import cn.edu.ubaa.plus.ui.screens.auth.ConnectionModeSelectionScreen
import cn.edu.ubaa.plus.ui.screens.auth.LoginScreen
import cn.edu.ubaa.plus.ui.screens.schedule.OfflineScheduleScreen
import cn.edu.ubaa.plus.ui.screens.splash.SplashScreen
import cn.edu.ubaa.plus.ui.theme.PreloadFonts
import cn.edu.ubaa.plus.ui.theme.UBAATheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 应用程序顶层入口 Composable。 负责全局状态管理，包括：
 * 1. 字体预加载。
 * 2. 整体主题应用。
 * 3. 认证状态监听与自动登录逻辑。
 * 4. 启动界面 (Splash) 与主界面/登录界面的切换。
 * 5. 软件更新检测与弹窗提示。
 */
@Composable
@Preview
fun App() {
  // 预加载应用所需的中文字体
  PreloadFonts()
  val preferences by AppPreferencesStore.state.collectAsState()
  val darkTheme =
      when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

  UBAATheme(
      palette = preferences.themePalette,
      followWallpaper = preferences.followWallpaper,
      darkTheme = darkTheme,
      customBrandArgb = preferences.customBrandArgb,
      glassEnabled = preferences.glassEnabled,
  ) {
    val authViewModel: AuthViewModel = viewModel { AuthViewModel() }
    val uiState by authViewModel.uiState.collectAsState()
    androidx.compose.runtime.SideEffect {
      cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator.allowOnline(uiState.isLoggedIn)
    }
    val cachedUserRevision by CachedUserStore.changes.collectAsState()
    val cachedUser = remember(cachedUserRevision) { CachedUserStore.current() }
    var showLogin by rememberSaveable {
      mutableStateOf(cn.edu.ubaa.plus.api.storage.CredentialStore.requiresManualLogin())
    }
    LaunchedEffect(uiState.isLoggedIn) { if (uiState.isLoggedIn) showLogin = false }
    BackHandler(enabled = showLogin && cachedUser != null) { showLogin = false }

    val loginForm by authViewModel.loginForm.collectAsState()
    val appScope = rememberCoroutineScope()
    val appContext = LocalContext.current
    var notificationPermissionGranted by remember {
      mutableStateOf(ReminderServiceController.notificationsAllowed(appContext))
    }
    var notificationPermissionRequested by remember { mutableStateOf(false) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
          notificationPermissionGranted = granted
        }
    LaunchedEffect(uiState.isLoggedIn) {
      if (
          uiState.isLoggedIn &&
              Build.VERSION.SDK_INT >= 33 &&
              !notificationPermissionGranted &&
              !notificationPermissionRequested
      ) {
        notificationPermissionRequested = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    LaunchedEffect(
        uiState.isLoggedIn,
        preferences.backgroundRemindersEnabled,
        notificationPermissionGranted,
    ) {
      ReminderServiceController.sync(
          context = appContext,
          loggedIn = uiState.isLoggedIn,
          enabled = preferences.backgroundRemindersEnabled,
      )
    }
    val availableConnectionModes = remember { ConnectionRuntime.availableModes() }
    var selectedConnectionMode by remember { mutableStateOf<ConnectionMode?>(null) }
    val effectiveConnectionMode by ConnectionRuntime.currentModeFlow.collectAsState()
    var modeResolved by remember { mutableStateOf(false) }
    var showOfflineSchedule by remember { mutableStateOf(false) }

    // 启动流程控制状态
    var isSplashFinished by remember { mutableStateOf(false) }

    // 更新检测逻辑
    val updateService = remember { UpdateService() }
    val announcementService = remember { AnnouncementService() }
    var updateInfo by remember { mutableStateOf<AppVersionCheckResponse?>(null) }
    var announcementInfo by remember { mutableStateOf<AppAnnouncement?>(null) }
    var disclaimerAccepted by remember {
      mutableStateOf(runCatching { DisclaimerStore.isAccepted() }.getOrDefault(false))
    }
    val uriHandler = LocalUriHandler.current

    suspend fun checkStartupPrompts() = coroutineScope {
      val update = async { updateService.checkUpdate() }
      val announcement = async { announcementService.checkAnnouncement() }
      updateInfo = update.await()
      announcementInfo = announcement.await()?.takeUnless { AnnouncementReadStore.isRead(it.id) }
    }

    suspend fun bootstrapForMode(mode: ConnectionMode) {
      authViewModel.initializeApp()
    }

    // Public app notices never wait for a school session or a chosen school transport.
    LaunchedEffect(disclaimerAccepted) { if (disclaimerAccepted) checkStartupPrompts() }

    LaunchedEffect(Unit) {
      CachedUserStore.migrateVerifiedAccount()
      cn.edu.ubaa.plus.api.storage.CachedHomeStore.migrateLegacy()
      val preferred = ConnectionRuntime.preferredMode()
      selectedConnectionMode = preferred
      modeResolved = true
      if (
          CachedUserStore.current() != null ||
              ScheduleStore.hasSavedSchedule() ||
              cn.edu.ubaa.plus.api.storage.CredentialStore.canAutomaticallyAuthenticate()
      )
          isSplashFinished = true
      if (preferred != null) {
        ConnectionRuntime.resolveStartupMode()?.let { bootstrapForMode(it) }
      }
    }

    // 前台恢复时验证会话有效性
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, uiState.isLoggedIn) {
      var wasInBackground = false
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_STOP -> wasInBackground = true
          Lifecycle.Event.ON_RESUME -> {
            if (wasInBackground) {
              wasInBackground = false
              if (uiState.isLoggedIn) authViewModel.validateSession()
              else if (cachedUser != null && !uiState.isLoading && !uiState.isPreloading)
                  authViewModel.initializeApp()
              appScope.launch { checkStartupPrompts() }
            }
          }
          else -> {}
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      try {
        awaitCancellation()
      } finally {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }

    // 根据认证状态和加载进度决定何时隐藏 Splash 界面
    LaunchedEffect(
        uiState.isLoggedIn,
        uiState.error,
        uiState.isLoading,
        uiState.isPreloading,
        uiState.isRefreshingCaptcha,
    ) {
      val shouldEndSplash =
          (uiState.isLoggedIn && uiState.userData != null) ||
              (uiState.error != null &&
                  !uiState.isLoading &&
                  !uiState.isPreloading &&
                  !uiState.isRefreshingCaptcha) ||
              (!uiState.isLoading &&
                  !uiState.isPreloading &&
                  !uiState.isRefreshingCaptcha &&
                  !uiState.isLoggedIn &&
                  uiState.error == null)

      if (shouldEndSplash) isSplashFinished = true
    }

    // 首次使用的免责声明：必须同意后才能继续。
    if (!disclaimerAccepted) {
      FirstRunDisclaimerDialog(
          onAccept = {
            DisclaimerStore.accept()
            disclaimerAccepted = true
          }
      )
    }

    // 版本更新对话框
    if (disclaimerAccepted && updateInfo != null) {
      val release = updateInfo!!
      val releaseNotes = release.releaseNotes?.takeIf { it.isNotBlank() } ?: "点击下方按钮下载最新客户端。"
      val updateMessage = buildString {
        append("当前客户端版本：")
        append(AppInfo.version)
        append('\n')
        append("最新客户端版本：")
        append(release.latestVersion)
        append("\n\n")
        append(releaseNotes)
      }

      AlertDialog(
          onDismissRequest = { updateInfo = null },
          title = { Text("发现新版本") },
          text = {
            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
              ReleaseNotesText(updateMessage)
            }
          },
          confirmButton = {
            TextButton(
                onClick = {
                  uriHandler.openUri(release.downloadUrl)
                  updateInfo = null
                }
            ) {
              Text("前往下载")
            }
          },
          dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("稍后再说") } },
      )
    }

    if (disclaimerAccepted && shouldShowAnnouncementDialog(updateInfo, announcementInfo)) {
      val announcement = announcementInfo!!
      AlertDialog(
          onDismissRequest = {},
          title = { Text(announcement.title) },
          text = {
            Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
              Text(announcement.content)
            }
          },
          confirmButton = {
            TextButton(
                onClick = {
                  confirmAnnouncement(
                      announcement = announcement,
                      openUri = uriHandler::openUri,
                      markRead = AnnouncementReadStore::markRead,
                  )
                  announcementInfo = null
                }
            ) {
              Text(announcement.confirmText?.takeIf { it.isNotBlank() } ?: "我知道了")
            }
          },
      )
    }

    // 主界面以外的页面（开屏、连接方式、登录）没有蓝色标题栏，按主题选择系统栏图标颜色。
    val mainAppVisible =
        (uiState.isLoggedIn && uiState.userData != null || cachedUser != null) &&
            isSplashFinished &&
            !showOfflineSchedule &&
            !showLogin
    if (!mainAppVisible) {
      val useDarkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
      SystemBarsAppearance(
          darkStatusBarIcons = useDarkIcons,
          darkNavigationBarIcons = useDarkIcons,
      )
    }

    // 视图切换状态机
    when {
      !modeResolved -> SplashScreen(modifier = Modifier.fillMaxSize())
      selectedConnectionMode == null ->
          ConnectionModeSelectionScreen(
              availableModes = availableConnectionModes,
              onConfirm = { mode ->
                appScope.launch { bootstrapForMode(mode.also(ConnectionRuntime::switchMode)) }
              },
              modifier = Modifier.fillMaxSize(),
          )
      !isSplashFinished -> SplashScreen(modifier = Modifier.fillMaxSize())
      showOfflineSchedule -> OfflineScheduleScreen(onBack = { showOfflineSchedule = false })
      (uiState.isLoggedIn && uiState.userData != null || cachedUser != null) && !showLogin -> {
        val userData = uiState.userData ?: requireNotNull(cachedUser)
        key(userData.schoolid) {
          MainAppScreen(
              isAuthenticated = uiState.isLoggedIn,
              onLoginRequired = { showLogin = true },
              userData = userData,
              userInfo = uiState.userInfo,
              connectionMode = selectedConnectionMode ?: ConnectionMode.DIRECT,
              effectiveConnectionMode =
                  effectiveConnectionMode
                      ?: selectedConnectionMode?.takeIf { it != ConnectionMode.AUTO }
                      ?: ConnectionMode.DIRECT,
              availableConnectionModes = availableConnectionModes,
              onEnsureUserInfo = { if (uiState.isLoggedIn) authViewModel.ensureUserInfoLoaded() },
              onConnectionModeSelected = { mode ->
                selectedConnectionMode = mode
                authViewModel.switchConnectionMode(mode)
                appScope.launch { checkStartupPrompts() }
              },
              onLogoutClick = { clear ->
                showLogin = true
                authViewModel.logout(clear)
              },
              authMessage = if (uiState.captchaRequired) "需要补全验证码后登录" else uiState.error,
              authAction =
                  if (uiState.captchaRequired) "补全登录"
                  else if (uiState.error?.contains("密码") == true) "重新登录" else "重试",
              onAuthRetry = {
                if (uiState.captchaRequired || uiState.error?.contains("密码") == true)
                    showLogin = true
                else authViewModel.retryAuthentication()
              },
              modifier = Modifier.fillMaxSize(),
          )
        }
      }
      !showLogin && cn.edu.ubaa.plus.api.storage.CredentialStore.canAutomaticallyAuthenticate() -> {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
          Text("首页", style = MaterialTheme.typography.headlineMedium)
          Text(
              if (uiState.isLoading || uiState.isPreloading) "正在恢复登录…"
              else uiState.error ?: "登录后加载课程与待办"
          )
          if (!uiState.isLoading && !uiState.isPreloading) {
            cn.edu.ubaa.plus.ui.common.components.UbaaButton(
                onClick = {
                  if (uiState.captchaRequired || uiState.error?.contains("密码") == true)
                      showLogin = true
                  else authViewModel.retryAuthentication()
                }
            ) {
              Text(if (uiState.captchaRequired) "补全登录" else "重试")
            }
            cn.edu.ubaa.plus.ui.common.components.UbaaTextButton(onClick = { showLogin = true }) {
              Text("修改登录信息")
            }
          }
        }
      }
      else -> {
        LoginScreen(
            loginFormState = loginForm,
            currentConnectionMode = selectedConnectionMode ?: ConnectionMode.DIRECT,
            availableConnectionModes = availableConnectionModes,
            onUsernameChange = { authViewModel.updateUsername(it) },
            onPasswordChange = { authViewModel.updatePassword(it) },
            onCaptchaChange = { authViewModel.updateCaptcha(it) },
            onRememberPasswordChange = { authViewModel.updateRememberPassword(it) },
            onConnectionModeSelected = { mode ->
              selectedConnectionMode = mode
              authViewModel.switchConnectionMode(mode)
              appScope.launch { checkStartupPrompts() }
            },
            onLoginClick = { authViewModel.login() },
            onRefreshCaptcha = { authViewModel.refreshCaptcha() },
            isLoading = uiState.isLoading,
            isRefreshingCaptcha = uiState.isRefreshingCaptcha,
            captchaRequired = uiState.captchaRequired,
            captchaInfo = uiState.captchaInfo,
            error = uiState.error,
            onOfflineSchedule =
                if (ScheduleStore.hasSavedSchedule()) ({ showOfflineSchedule = true }) else null,
            modifier = Modifier.background(MaterialTheme.colorScheme.background).fillMaxSize(),
        )
      }
    }

    // 错误消息自动淡出
    LaunchedEffect(uiState.error) {
      if (uiState.error != null) {
        delay(5000)
        authViewModel.clearError()
      }
    }
  }
}

internal fun launchStartupTasks(
    scope: CoroutineScope,
    initializeAuthentication: () -> Unit,
    checkStartupPrompts: suspend () -> Unit,
) {
  scope.launch { initializeAuthentication() }
  scope.launch { checkStartupPrompts() }
}

internal fun shouldShowAnnouncementDialog(
    updateInfo: AppVersionCheckResponse?,
    announcement: AppAnnouncement?,
): Boolean = updateInfo == null && announcement != null

internal fun confirmAnnouncement(
    announcement: AppAnnouncement,
    openUri: (String) -> Unit,
    markRead: (String) -> Unit,
) {
  markRead(announcement.id)
  announcement.linkUrl?.takeIf { it.isNotBlank() }?.let(openUri)
}

/** 设置状态栏与系统导航栏图标明暗，适配浅色/深色背景。 */
@Composable
internal fun SystemBarsAppearance(
    darkStatusBarIcons: Boolean,
    darkNavigationBarIcons: Boolean,
) {
  val view = LocalView.current
  val context = LocalContext.current
  SideEffect {
    val window = context.findActivity()?.window ?: return@SideEffect
    WindowCompat.getInsetsController(window, view).apply {
      isAppearanceLightStatusBars = darkStatusBarIcons
      isAppearanceLightNavigationBars = darkNavigationBarIcons
    }
  }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
      is Activity -> this
      is ContextWrapper -> baseContext.findActivity()
      else -> null
    }
