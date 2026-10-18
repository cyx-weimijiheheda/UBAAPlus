package cn.edu.ubaa.plus.ui.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.ubaa.plus.SystemBarsAppearance
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.local.SsoTicketHandoff
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.api.storage.BykcCourseFilterStore
import cn.edu.ubaa.plus.model.dto.BykcCourseDto
import cn.edu.ubaa.plus.model.dto.CourseClass
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.model.dto.UserInfo
import cn.edu.ubaa.plus.ui.common.components.AppTopBar
import cn.edu.ubaa.plus.ui.common.components.BottomNavTab
import cn.edu.ubaa.plus.ui.common.components.QrScanResultDialog
import cn.edu.ubaa.plus.ui.common.util.BackHandlerCompat
import cn.edu.ubaa.plus.ui.screens.bykc.*
import cn.edu.ubaa.plus.ui.screens.campus.CampusCardViewModel
import cn.edu.ubaa.plus.ui.screens.campus.CampusNetworkViewModel
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyUiState
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyViewModel
import cn.edu.ubaa.plus.ui.screens.classroom.ClassroomViewModel
import cn.edu.ubaa.plus.ui.screens.dorm.DormElectricityViewModel
import cn.edu.ubaa.plus.ui.screens.evaluation.EvaluationViewModel
import cn.edu.ubaa.plus.ui.screens.exam.ExamUiState
import cn.edu.ubaa.plus.ui.screens.exam.ExamViewModel
import cn.edu.ubaa.plus.ui.screens.grade.GradeScoreWatchViewModel
import cn.edu.ubaa.plus.ui.screens.grade.GradeUiState
import cn.edu.ubaa.plus.ui.screens.grade.GradeViewModel
import cn.edu.ubaa.plus.ui.screens.judge.JudgeViewModel
import cn.edu.ubaa.plus.ui.screens.libbook.LibBookViewModel
import cn.edu.ubaa.plus.ui.screens.library.LibraryBorrowViewModel
import cn.edu.ubaa.plus.ui.screens.menu.*
import cn.edu.ubaa.plus.ui.screens.schedule.ScheduleViewModel
import cn.edu.ubaa.plus.ui.screens.signin.SigninViewModel
import cn.edu.ubaa.plus.ui.screens.spoc.SpocViewModel
import cn.edu.ubaa.plus.ui.screens.venue.VenueMembershipCodeDialog
import cn.edu.ubaa.plus.ui.screens.venue.VenueViewModel
import cn.edu.ubaa.plus.ui.screens.ygdk.YgdkUiState
import cn.edu.ubaa.plus.ui.screens.ygdk.YgdkViewModel
import cn.edu.ubaa.plus.ui.theme.UbaaBrand
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** 应用程序所有的屏幕页面定义。 */
enum class AppScreen {
  HOME,
  REGULAR,
  PROFILE,
  SETTINGS,
  ABOUT,
  FEATURE_ORDER,
  SCHEDULE,
  EXAM,
  GRADE,
  COURSE_DETAIL,
  BYKC_COURSES,
  BYKC_DETAIL,
  BYKC_CHOSEN,
  BYKC_STATISTICS,
  ICLASS_HOME,
  CGYY_RESERVE_PICKER,
  CGYY_RESERVE_FORM,
  CGYY_ORDERS,
  CGYY_LOCK_CODE,
  CLASSROOM_QUERY,
  EVALUATION,
  SPOC_ASSIGNMENTS,
  SPOC_ASSIGNMENT_DETAIL,
  JUDGE_ASSIGNMENTS,
  JUDGE_ASSIGNMENT_DETAIL,
  LIBBOOK_RESERVE,
  LIBBOOK_BOOKINGS,
  YGDK_HOME,
  YGDK_FORM,
  VENUE_HOME,
  DORM_ELECTRICITY,
  CAMPUS_NETWORK,
  CAMPUS_CARD,
  LIBRARY_BORROW,
}

/**
 * 主界面支架组件。 整合了侧边栏、顶部栏、底部导航栏以及各业务模块的屏幕切换。 负责协调 ViewModel 的初始化和导航状态的分发。
 *
 * @param userData 登录用户的基础数据。
 * @param userInfo 登录用户的详细信息。
 * @param onLogoutClick 注销回调。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun MainAppScreen(
    userData: UserData,
    userInfo: UserInfo?,
    connectionMode: ConnectionMode,
    availableConnectionModes: List<ConnectionMode>,
    onEnsureUserInfo: () -> Unit,
    onConnectionModeSelected: (ConnectionMode) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  val uriHandler = LocalUriHandler.current
  val navController = rememberNavigationController()
  val currentScreen = navController.currentScreen
  val cgyyScreens = remember {
    setOf(
        AppScreen.CGYY_RESERVE_PICKER,
        AppScreen.CGYY_RESERVE_FORM,
        AppScreen.CGYY_ORDERS,
        AppScreen.CGYY_LOCK_CODE,
    )
  }
  val ygdkScreens = remember { setOf(AppScreen.YGDK_HOME, AppScreen.YGDK_FORM) }
  val libBookScreens = remember { setOf(AppScreen.LIBBOOK_RESERVE, AppScreen.LIBBOOK_BOOKINGS) }

  var selectedBottomTab by remember { mutableStateOf(BottomNavTab.HOME) }
  var homeManualRefreshPending by remember { mutableStateOf(false) }
  var homeManualRefreshStarted by remember { mutableStateOf(false) }
  val homeSnackbarHostState = remember { SnackbarHostState() }
  val homeBootstrapCoordinator = remember(scope) { HomeBootstrapCoordinator(scope) }
  val homeBootstrapRunning by homeBootstrapCoordinator.isRunning.collectAsState()
  val homeNow by
      produceState(
          initialValue = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
      ) {
        while (true) {
          delay(60_000)
          value = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }
      }
  var hasVisitedCgyy by remember { mutableStateOf(false) }
  var hasVisitedLibBook by remember { mutableStateOf(false) }
  var hasVisitedVenue by remember { mutableStateOf(false) }
  val shouldKeepCgyyViewModel =
      currentScreen == AppScreen.HOME || hasVisitedCgyy || currentScreen in cgyyScreens
  val shouldKeepLibBookViewModel = hasVisitedLibBook || currentScreen in libBookScreens
  val shouldKeepVenueViewModel = hasVisitedVenue || currentScreen == AppScreen.VENUE_HOME

  // 初始化各模块 ViewModel
  val scheduleViewModel: ScheduleViewModel = viewModel { ScheduleViewModel() }
  val scheduleUiState by scheduleViewModel.uiState.collectAsState()
  val todayScheduleState by scheduleViewModel.todayScheduleState.collectAsState()
  LaunchedEffect(homeNow.date) {
    scheduleViewModel.loadTodaySchedule()
    scheduleViewModel.ensureCurrentWeekLoaded(forceRefresh = true)
    scheduleUiState.selectedTerm?.let(scheduleViewModel::loadWeeks)
  }

  val examViewModel: ExamViewModel? =
      if (currentScreen == AppScreen.EXAM) {
        viewModel(key = "exam") { ExamViewModel() }
      } else {
        null
      }
  val examUiState = examViewModel?.uiState?.collectAsState()?.value ?: ExamUiState()
  var showExamTermMenu by remember { mutableStateOf(false) }
  val gradeViewModel: GradeViewModel? =
      if (currentScreen == AppScreen.GRADE) {
        viewModel(key = "grade") { GradeViewModel() }
      } else {
        null
      }
  val gradeUiState = gradeViewModel?.uiState?.collectAsState()?.value ?: GradeUiState()
  val gradeScoreWatchViewModel: GradeScoreWatchViewModel =
      viewModel(key = "grade-score-watch-${userData.schoolid}") {
        GradeScoreWatchViewModel(userKey = userData.schoolid)
      }
  val gradeScoreWatchUiState by gradeScoreWatchViewModel.uiState.collectAsState()

  val signinViewModel: SigninViewModel =
      viewModel(key = "signin-${userData.schoolid}") { SigninViewModel() }
  val signinUiState by signinViewModel.uiState.collectAsState()
  var showVenueMembershipCodeDialog by remember { mutableStateOf(false) }
  val iclassScanOptions = remember {
    ScanOptions()
        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        .setPrompt("将签到二维码放入框内")
        .setBeepEnabled(false)
        .setOrientationLocked(true)
  }
  val iclassScanLauncher =
      rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let(signinViewModel::performScannedSignin)
      }
  val genericScanOptions = remember {
    ScanOptions()
        .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        .setPrompt("将二维码或条形码放入框内")
        .setBeepEnabled(false)
        .setOrientationLocked(true)
  }
  var scanResult by remember { mutableStateOf<String?>(null) }
  val genericScanLauncher =
      rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.takeIf { it.isNotBlank() }?.let { scanResult = it }
      }
  val evaluationViewModel: EvaluationViewModel? =
      if (currentScreen == AppScreen.EVALUATION) {
        viewModel(key = "evaluation") { EvaluationViewModel() }
      } else {
        null
      }
  val cgyyViewModel: CgyyViewModel? =
      if (shouldKeepCgyyViewModel) {
        viewModel(key = "cgyy-${userData.schoolid}") { CgyyViewModel() }
      } else {
        null
      }
  val cgyyUiState = cgyyViewModel?.uiState?.collectAsState()?.value ?: CgyyUiState()
  val libBookViewModel: LibBookViewModel? =
      if (shouldKeepLibBookViewModel) {
        viewModel(key = "libbook-${userData.schoolid}") { LibBookViewModel() }
      } else {
        null
      }
  val classroomViewModel: ClassroomViewModel? =
      if (currentScreen == AppScreen.CLASSROOM_QUERY) {
        viewModel(key = "classroom") { ClassroomViewModel() }
      } else {
        null
      }
  val dormElectricityViewModel: DormElectricityViewModel? =
      if (currentScreen == AppScreen.DORM_ELECTRICITY) {
        viewModel(key = "dorm-${userData.schoolid}") { DormElectricityViewModel() }
      } else {
        null
      }
  val campusNetworkViewModel: CampusNetworkViewModel? =
      if (currentScreen == AppScreen.CAMPUS_NETWORK) {
        viewModel(key = "network-${userData.schoolid}") { CampusNetworkViewModel() }
      } else {
        null
      }
  val campusCardViewModel: CampusCardViewModel? =
      if (currentScreen == AppScreen.CAMPUS_CARD) {
        viewModel(key = "card-${userData.schoolid}") { CampusCardViewModel() }
      } else {
        null
      }
  val libraryBorrowViewModel: LibraryBorrowViewModel? =
      if (currentScreen == AppScreen.LIBRARY_BORROW) {
        viewModel(key = "library-${userData.schoolid}") { LibraryBorrowViewModel() }
      } else {
        null
      }
  val spocViewModel: SpocViewModel =
      viewModel(key = "spoc-${userData.schoolid}") { SpocViewModel() }
  val spocUiState by spocViewModel.uiState.collectAsState()
  val judgeViewModel: JudgeViewModel =
      viewModel(key = "judge-${userData.schoolid}") { JudgeViewModel(userKey = userData.schoolid) }
  val judgeUiState by judgeViewModel.uiState.collectAsState()
  val bykcViewModel: BykcViewModel =
      viewModel(key = "bykc-${userData.schoolid}") { BykcViewModel() }
  val venueViewModel: VenueViewModel? =
      if (shouldKeepVenueViewModel) {
        viewModel(key = "venue-${userData.schoolid}") { VenueViewModel() }
      } else {
        null
      }
  val bykcCoursesState by bykcViewModel.coursesState.collectAsState()
  val bykcDetailState by bykcViewModel.courseDetailState.collectAsState()
  val bykcChosenState by bykcViewModel.chosenCoursesState.collectAsState()
  val bykcGrabState by bykcViewModel.grabState.collectAsState()
  val ygdkViewModel: YgdkViewModel? =
      if (currentScreen == AppScreen.HOME || currentScreen in ygdkScreens) {
        viewModel(key = "ygdk-${userData.schoolid}") { YgdkViewModel(userKey = userData.schoolid) }
      } else {
        null
      }
  val ygdkUiState = ygdkViewModel?.uiState?.collectAsState()?.value ?: YgdkUiState()
  val currentWeek = scheduleUiState.currentWeek
  val ygdkWeekReminderKey = currentWeek?.let { "${it.term}:${it.serialNumber}" }
  val ygdkTermReminderKey = currentWeek?.term ?: scheduleUiState.selectedTerm?.itemCode
  val ygdkWeekDone = ygdkWeekReminderKey?.let { ygdkViewModel?.isHomeReminderWeekDone(it) } ?: false
  val ygdkTermDone = ygdkTermReminderKey?.let { ygdkViewModel?.isHomeReminderTermDone(it) } ?: false
  val isYgdkReminderWeek = currentWeek?.serialNumber?.let { it in 11..14 } == true
  val shouldLoadYgdkHomeOverview =
      ygdkViewModel != null &&
          ygdkUiState.homeReminderEnabled &&
          isYgdkReminderWeek &&
          !ygdkWeekDone &&
          !ygdkTermDone

  var selectedCourse by remember { mutableStateOf<CourseClass?>(null) }
  var selectedBykcCourseId by remember { mutableStateOf<Long?>(null) }
  var selectedBykcCourseSnapshot by remember { mutableStateOf<BykcCourseDto?>(null) }
  val bykcCoursesListState = rememberLazyListState()
  val bykcFilterUserKey = userData.schoolid
  val defaultBykcFilters = remember { defaultBykcCourseFilters() }
  var bykcCourseFilters by
      remember(bykcFilterUserKey) {
        mutableStateOf(
            BykcCourseFilterStore.get(bykcFilterUserKey)?.toBykcCourseFilters()
                ?: defaultBykcFilters
        )
      }
  val appPreferences by AppPreferencesStore.state.collectAsState()
  val features =
      remember(appPreferences.featureOrder, appPreferences.hiddenFeatures) {
        resolveFeatureItems(appPreferences.featureOrder, appPreferences.hiddenFeatures)
      }
  var selectedSpocAssignmentId by remember { mutableStateOf<String?>(null) }
  var showSpocSortFilterDialog by remember { mutableStateOf(false) }
  var showBykcFiltersDialog by remember { mutableStateOf(false) }
  var selectedJudgeCourseId by remember { mutableStateOf<String?>(null) }
  var selectedJudgeAssignmentId by remember { mutableStateOf<String?>(null) }
  var showJudgeSortFilterDialog by remember { mutableStateOf(false) }
  val homeTodoItems =
      remember(
          bykcChosenState.courses,
          spocUiState.assignmentsResponse,
          judgeUiState.assignmentsResponse,
          cgyyUiState.orders.content,
          signinUiState.classes,
          ygdkUiState.overview,
          currentWeek,
          ygdkUiState.homeReminderEnabled,
          ygdkWeekDone,
          ygdkTermDone,
          homeNow,
      ) {
        buildHomeTodoItems(
            bykcCourses = bykcChosenState.courses,
            spocAssignments = spocUiState.assignmentsResponse?.assignments.orEmpty(),
            judgeAssignments = judgeUiState.assignmentsResponse?.assignments.orEmpty(),
            cgyyOrders = cgyyUiState.orders.content,
            signinClasses = signinUiState.classes,
            ygdkOverview = ygdkUiState.overview,
            currentWeek = currentWeek,
            ygdkReminderEnabled = ygdkUiState.homeReminderEnabled,
            ygdkWeekDone = ygdkWeekDone,
            ygdkTermDone = ygdkTermDone,
            now = homeNow,
        )
      }
  val homeTodoLoadingSources = buildList {
    if (bykcChosenState.isLoading) add(HomeTodoSource.BYKC)
    if (spocUiState.isLoading || spocUiState.isRefreshing) add(HomeTodoSource.SPOC)
    if (judgeUiState.isLoading || judgeUiState.isRefreshing) add(HomeTodoSource.JUDGE)
    if (cgyyUiState.isOrdersLoading) add(HomeTodoSource.CGYY)
    if (signinUiState.isLoading) add(HomeTodoSource.SIGNIN)
    if (shouldLoadYgdkHomeOverview && ygdkUiState.isLoading) add(HomeTodoSource.YGDK)
  }
  val homeTodoLoading = homeTodoLoadingSources.isNotEmpty()
  val homeContentLoading =
      todayScheduleState.isLoading || homeTodoLoading || gradeScoreWatchUiState.isLoading
  val homeIsRefreshing =
      homeManualRefreshPending &&
          (homeManualRefreshStarted || homeBootstrapRunning || homeContentLoading)
  val homeTodoFailedSources = buildList {
    if (bykcChosenState.error != null) add(HomeTodoSource.BYKC)
    if (spocUiState.error != null) add(HomeTodoSource.SPOC)
    if (judgeUiState.error != null) add(HomeTodoSource.JUDGE)
    if (cgyyUiState.ordersError != null) add(HomeTodoSource.CGYY)
    if (signinUiState.error != null) add(HomeTodoSource.SIGNIN)
    if (shouldLoadYgdkHomeOverview && ygdkUiState.loadError != null) add(HomeTodoSource.YGDK)
  }

  fun startHomeBootstrap(forceRefresh: Boolean = false) {
    val showLoading =
        forceRefresh ||
            !scheduleViewModel.hasTodayLoaded() ||
            !signinViewModel.hasTodayLoaded() ||
            !spocViewModel.hasAssignmentsLoaded() ||
            !judgeViewModel.hasAssignmentsLoaded() ||
            !bykcViewModel.hasChosenCoursesLoaded() ||
            cgyyViewModel?.hasOrdersLoaded() != true ||
            !scheduleViewModel.hasCurrentWeekLoaded() ||
            (shouldLoadYgdkHomeOverview && ygdkViewModel.hasOverviewLoaded() != true) ||
            !gradeScoreWatchViewModel.hasChecked()
    homeBootstrapCoordinator.restart(
        HomeBootstrapActions(
            loadTodaySchedule = { force ->
              scheduleViewModel.ensureTodayLoaded(forceRefresh = force)
            },
            loadSignin = { force -> signinViewModel.ensureTodayLoaded(forceRefresh = force) },
            loadSpoc = { force -> spocViewModel.ensureAssignmentsLoaded(forceRefresh = force) },
            loadJudge = { force -> judgeViewModel.ensureAssignmentsLoaded(forceRefresh = force) },
            loadBykc = { force -> bykcViewModel.ensureChosenCoursesLoaded(forceRefresh = force) },
            loadCgyy = { force -> cgyyViewModel?.ensureOrdersLoaded(forceRefresh = force) },
            loadYgdk = { force ->
              scheduleViewModel.ensureCurrentWeekLoaded(forceRefresh = force)
              if (shouldLoadYgdkHomeOverview) {
                ygdkViewModel.ensureOverviewLoaded(forceRefresh = force)
              }
            },
            checkGradeScores = { force ->
              gradeScoreWatchViewModel.checkForUpdates(forceRefresh = force)
            },
        ),
        forceRefresh = forceRefresh,
        showLoading = showLoading,
    )
  }

  fun refreshHomeData() {
    homeManualRefreshPending = true
    startHomeBootstrap(forceRefresh = true)
  }

  fun updateBykcCourseFilters(newFilters: BykcCourseFilters) {
    val shouldReloadCourses =
        bykcCourseFilters.requiresAllCourses() != newFilters.requiresAllCourses()
    bykcCourseFilters = newFilters
    if (newFilters == defaultBykcFilters) {
      BykcCourseFilterStore.clear(bykcFilterUserKey)
    } else {
      BykcCourseFilterStore.save(bykcFilterUserKey, newFilters.toStored())
    }
    if (shouldReloadCourses) {
      bykcViewModel.loadCourses(includeExpired = newFilters.requiresAllCourses())
    }
  }

  /** 重置导航栈至指定根页面。 */
  fun setRoot(screen: AppScreen, tab: BottomNavTab) {
    navController.setRoot(screen)
    selectedBottomTab = tab
  }

  /** 跳转至指定页面，并自动更新底部 Tab 激活状态。 */
  fun navigateTo(screen: AppScreen, bottomTab: BottomNavTab? = null) {
    navController.navigateTo(screen)
    val tab = bottomTab ?: bottomTabFor(screen)
    tab?.let { selectedBottomTab = it }
  }

  /** 统一的返回逻辑处理。 */
  fun navigateBack() {
    if (navController.navigateBack()) {
      bottomTabFor(navController.currentScreen)?.let { selectedBottomTab = it }
    } else {
      selectedBottomTab =
          when (navController.currentScreen) {
            AppScreen.REGULAR -> BottomNavTab.REGULAR
            AppScreen.PROFILE -> BottomNavTab.PROFILE
            else -> BottomNavTab.HOME
          }
    }
  }

  /** 尝试用 CAS 一次性 ticket 打开网页；失败时回退普通 URL。 */
  fun openUrl(url: String) {
    scope.launch {
      val target =
          runCatching { SsoTicketHandoff.authenticatedUrl(url).getOrDefault(url) }.getOrDefault(url)
      runCatching { uriHandler.openUri(target) }
    }
  }

  /** 退出登录前先停止所有前台抢单任务，避免带着已清理的会话继续请求。 */
  fun performLogout() {
    bykcViewModel.stopGrab(BykcGrabStopReason.LOGOUT)
    venueViewModel?.stopGrabForLogout()
    onLogoutClick()
  }

  fun handleHomeTodoClick(todoItem: HomeTodoItem) {
    when (val action = todoItem.action) {
      is HomeTodoAction.OpenBykcCourse -> {
        selectedBykcCourseId = action.courseId
        selectedBykcCourseSnapshot = null
        bykcViewModel.loadCourseDetail(action.courseId)
        navigateTo(AppScreen.BYKC_DETAIL)
      }
      is HomeTodoAction.OpenSpocAssignment -> {
        selectedSpocAssignmentId = action.assignmentId
        spocViewModel.loadAssignmentDetail(action.assignmentId)
        navigateTo(AppScreen.SPOC_ASSIGNMENT_DETAIL)
      }
      is HomeTodoAction.OpenJudgeAssignment -> {
        selectedJudgeCourseId = action.courseId
        selectedJudgeAssignmentId = action.assignmentId
        judgeViewModel.loadAssignmentDetail(action.courseId, action.assignmentId)
        navigateTo(AppScreen.JUDGE_ASSIGNMENT_DETAIL)
      }
      HomeTodoAction.OpenCgyyOrders -> {
        cgyyViewModel?.ensureOrdersLoaded()
        navigateTo(AppScreen.CGYY_ORDERS)
      }
      is HomeTodoAction.SigninCourse -> signinViewModel.performSignin(action.courseId)
      HomeTodoAction.OpenYgdkHome -> navigateTo(AppScreen.YGDK_HOME)
    }
  }

  fun openScoresFromHomeNotice() {
    gradeScoreWatchViewModel.consumeNotice()
    navigateTo(AppScreen.GRADE)
  }

  LaunchedEffect(
      ygdkUiState.overview,
      ygdkWeekReminderKey,
      ygdkTermReminderKey,
      ygdkUiState.homeReminderEnabled,
  ) {
    if (!ygdkUiState.homeReminderEnabled) return@LaunchedEffect
    val viewModel = ygdkViewModel ?: return@LaunchedEffect
    val summary = ygdkUiState.overview?.summary ?: return@LaunchedEffect
    ygdkWeekReminderKey?.let { key ->
      if ((summary.weekCount ?: 0) >= 4) {
        viewModel.markHomeReminderWeekDone(key)
      }
    }
    ygdkTermReminderKey?.let { key ->
      if (summary.termCount >= 16) {
        viewModel.markHomeReminderTermDone(key)
      }
    }
  }

  LaunchedEffect(currentScreen, currentWeek, shouldLoadYgdkHomeOverview) {
    if (currentScreen == AppScreen.HOME && shouldLoadYgdkHomeOverview) {
      ygdkViewModel.ensureOverviewLoaded()
    }
  }

  LaunchedEffect(currentScreen) {
    if (currentScreen in cgyyScreens) {
      hasVisitedCgyy = true
    }
    if (currentScreen in libBookScreens) {
      hasVisitedLibBook = true
    }
    if (currentScreen == AppScreen.VENUE_HOME) {
      hasVisitedVenue = true
    } else {
      showVenueMembershipCodeDialog = false
    }
    if (currentScreen != AppScreen.BYKC_COURSES) {
      showBykcFiltersDialog = false
    }
    if (currentScreen != AppScreen.HOME) {
      homeManualRefreshPending = false
      homeManualRefreshStarted = false
    }
    if (currentScreen == AppScreen.PROFILE) {
      onEnsureUserInfo()
    }
  }

  // 连接模式切换后，重置所有 ViewModel 的加载标记与缓存数据，并强制刷新当前页面
  LaunchedEffect(connectionMode, userData.schoolid) {
    // 常驻 ViewModel
    scheduleViewModel.resetLoadedState()
    signinViewModel.resetLoadedState()
    spocViewModel.resetLoadedState()
    judgeViewModel.resetLoadedState()
    bykcViewModel.resetLoadedState()
    // 按需 ViewModel（当前可能为 null，仅在存活时重置）
    cgyyViewModel?.resetLoadedState()
    ygdkViewModel?.resetLoadedState()
    examViewModel?.resetLoadedState()
    gradeViewModel?.resetLoadedState()
    gradeScoreWatchViewModel.resetLoadedState()
    evaluationViewModel?.resetLoadedState()
    libBookViewModel?.resetLoadedState()
    dormElectricityViewModel?.resetLoadedState()
    campusNetworkViewModel?.resetLoadedState()
    campusCardViewModel?.resetLoadedState()
    libraryBorrowViewModel?.resetLoadedState()
    venueViewModel?.resetLoadedState()
    // 刷新当前页面数据
    when (currentScreen) {
      AppScreen.HOME -> startHomeBootstrap(forceRefresh = true)
      AppScreen.SCHEDULE -> scheduleViewModel.ensureScheduleLoaded(forceRefresh = true)
      AppScreen.EXAM -> examViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.GRADE -> gradeViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.BYKC_COURSES -> {
        bykcViewModel.ensureProfileLoaded(forceRefresh = true)
        bykcViewModel.ensureCoursesLoaded(
            includeExpired = bykcCourseFilters.requiresAllCourses(),
            forceRefresh = true,
        )
      }
      AppScreen.BYKC_CHOSEN -> bykcViewModel.ensureChosenCoursesLoaded(forceRefresh = true)
      AppScreen.BYKC_STATISTICS -> bykcViewModel.ensureStatisticsLoaded(forceRefresh = true)
      AppScreen.ICLASS_HOME -> {
        signinViewModel.ensureTodayLoaded(forceRefresh = true)
        signinViewModel.ensureAttendanceLoaded(forceRefresh = true)
      }
      AppScreen.CGYY_RESERVE_PICKER,
      AppScreen.CGYY_RESERVE_FORM -> cgyyViewModel?.ensureInitialDataLoaded(forceRefresh = true)
      AppScreen.CGYY_ORDERS -> {
        cgyyViewModel?.ensureInitialDataLoaded(forceRefresh = true)
        cgyyViewModel?.ensureOrdersLoaded(forceRefresh = true)
      }
      AppScreen.EVALUATION -> evaluationViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.SPOC_ASSIGNMENTS -> spocViewModel.ensureAssignmentsLoaded(forceRefresh = true)
      AppScreen.JUDGE_ASSIGNMENTS -> judgeViewModel.ensureAssignmentsLoaded(forceRefresh = true)
      AppScreen.LIBBOOK_RESERVE -> libBookViewModel?.ensureInitialLoaded(forceRefresh = true)
      AppScreen.LIBBOOK_BOOKINGS -> libBookViewModel?.ensureBookingsLoaded(forceRefresh = true)
      AppScreen.YGDK_HOME,
      AppScreen.YGDK_FORM -> ygdkViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.VENUE_HOME -> venueViewModel?.ensureInitialized()
      AppScreen.DORM_ELECTRICITY -> dormElectricityViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.CAMPUS_NETWORK -> campusNetworkViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.CAMPUS_CARD -> campusCardViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.LIBRARY_BORROW -> libraryBorrowViewModel?.ensureLoaded(forceRefresh = true)
      AppScreen.FEATURE_ORDER -> Unit
      else -> Unit
    }
  }

  // Don't clear a manual refresh until we've observed real loading at least once.
  LaunchedEffect(
      homeManualRefreshPending,
      homeManualRefreshStarted,
      homeBootstrapRunning,
      homeContentLoading,
  ) {
    if (!homeManualRefreshPending) {
      homeManualRefreshStarted = false
    } else if (homeBootstrapRunning || homeContentLoading) {
      homeManualRefreshStarted = true
    } else if (homeManualRefreshStarted) {
      homeManualRefreshPending = false
      homeManualRefreshStarted = false
    }
  }

  LaunchedEffect(currentScreen, shouldKeepCgyyViewModel) {
    if (currentScreen != AppScreen.HOME) {
      homeBootstrapCoordinator.cancel()
    }
    when (currentScreen) {
      AppScreen.HOME -> startHomeBootstrap()
      AppScreen.SCHEDULE -> scheduleViewModel.ensureScheduleLoaded()
      AppScreen.EXAM -> examViewModel?.ensureLoaded()
      AppScreen.GRADE -> gradeViewModel?.ensureLoaded()
      AppScreen.BYKC_COURSES -> {
        bykcViewModel.ensureProfileLoaded()
        bykcViewModel.ensureCoursesLoaded(includeExpired = bykcCourseFilters.requiresAllCourses())
      }
      AppScreen.BYKC_CHOSEN -> bykcViewModel.ensureChosenCoursesLoaded()
      AppScreen.BYKC_STATISTICS -> bykcViewModel.ensureStatisticsLoaded()
      AppScreen.ICLASS_HOME -> {
        signinViewModel.ensureTodayLoaded()
        signinViewModel.ensureAttendanceLoaded()
      }
      AppScreen.CGYY_RESERVE_PICKER,
      AppScreen.CGYY_RESERVE_FORM -> {
        cgyyViewModel?.ensureInitialDataLoaded()
      }
      AppScreen.CGYY_ORDERS -> {
        cgyyViewModel?.ensureInitialDataLoaded()
        cgyyViewModel?.ensureOrdersLoaded()
      }
      AppScreen.CGYY_LOCK_CODE -> {
        cgyyViewModel?.ensureInitialDataLoaded()
        cgyyViewModel?.ensureLockCodeLoaded(forceRefresh = true)
      }
      AppScreen.EVALUATION -> evaluationViewModel?.ensureLoaded()
      AppScreen.SPOC_ASSIGNMENTS,
      AppScreen.SPOC_ASSIGNMENT_DETAIL -> spocViewModel.ensureAssignmentsLoaded()
      AppScreen.JUDGE_ASSIGNMENTS,
      AppScreen.JUDGE_ASSIGNMENT_DETAIL -> judgeViewModel.ensureAssignmentsLoaded()
      AppScreen.LIBBOOK_RESERVE -> libBookViewModel?.ensureInitialLoaded()
      AppScreen.LIBBOOK_BOOKINGS -> libBookViewModel?.ensureBookingsLoaded()
      AppScreen.YGDK_HOME,
      AppScreen.YGDK_FORM -> ygdkViewModel?.ensureLoaded()
      AppScreen.VENUE_HOME -> venueViewModel?.ensureInitialized()
      AppScreen.DORM_ELECTRICITY -> dormElectricityViewModel?.ensureLoaded()
      AppScreen.CAMPUS_NETWORK -> campusNetworkViewModel?.ensureLoaded()
      AppScreen.CAMPUS_CARD -> campusCardViewModel?.ensureLoaded()
      AppScreen.LIBRARY_BORROW -> libraryBorrowViewModel?.ensureLoaded()
      AppScreen.FEATURE_ORDER -> Unit
      else -> Unit
    }
  }

  LaunchedEffect(currentScreen, signinUiState.signinResult) {
    if (currentScreen == AppScreen.HOME) {
      signinUiState.signinResult?.let { message ->
        homeSnackbarHostState.showSnackbar(message)
        signinViewModel.clearSigninResult()
      }
    }
  }

  val screenTitle =
      when (currentScreen) {
        AppScreen.HOME -> "首页"
        AppScreen.REGULAR -> "功能"
        AppScreen.PROFILE -> "个人中心"
        AppScreen.SETTINGS -> "设置"
        AppScreen.ABOUT -> "关于"
        AppScreen.FEATURE_ORDER -> "功能排序与显示"
        AppScreen.SCHEDULE -> "课程表"
        AppScreen.EXAM -> "考试查询"
        AppScreen.GRADE -> gradeUiState.selectedTerm?.itemName ?: "成绩查询"
        AppScreen.COURSE_DETAIL -> "课程详情"
        AppScreen.BYKC_COURSES -> "博雅课程"
        AppScreen.BYKC_DETAIL -> "课程详情"
        AppScreen.BYKC_CHOSEN -> "我的课程"
        AppScreen.BYKC_STATISTICS -> "课程统计"
        AppScreen.ICLASS_HOME -> "智慧教室"
        AppScreen.CGYY_RESERVE_PICKER -> "研讨室预约"
        AppScreen.CGYY_RESERVE_FORM -> "填写预约信息"
        AppScreen.CGYY_ORDERS -> "我的预约"
        AppScreen.CGYY_LOCK_CODE -> "查看密码"
        AppScreen.CLASSROOM_QUERY -> "空教室查询"
        AppScreen.EVALUATION -> "自动评教"
        AppScreen.SPOC_ASSIGNMENTS -> "SPOC作业"
        AppScreen.SPOC_ASSIGNMENT_DETAIL -> "作业详情"
        AppScreen.JUDGE_ASSIGNMENTS -> "希冀作业"
        AppScreen.JUDGE_ASSIGNMENT_DETAIL -> "作业详情"
        AppScreen.LIBBOOK_RESERVE -> "图书馆座位"
        AppScreen.LIBBOOK_BOOKINGS -> "我的座位预约"
        AppScreen.YGDK_HOME -> "阳光打卡"
        AppScreen.YGDK_FORM -> "新增打卡"
        AppScreen.VENUE_HOME -> "运动场地"
        AppScreen.DORM_ELECTRICITY -> "寝室用电"
        AppScreen.CAMPUS_NETWORK -> "校园网络"
        AppScreen.CAMPUS_CARD -> "校园卡"
        AppScreen.LIBRARY_BORROW -> "图书借阅"
      }

  val isRootScreen = currentScreen in listOf(AppScreen.HOME, AppScreen.REGULAR, AppScreen.PROFILE)
  val darkBrandIcons = UbaaBrand.current.brand.luminance() > 0.5f
  SystemBarsAppearance(
      darkStatusBarIcons = darkBrandIcons,
      darkNavigationBarIcons = if (isRootScreen) darkBrandIcons else !isSystemInDarkTheme(),
  )

  Box(modifier = modifier.fillMaxSize()) {
    BackHandlerCompat(enabled = navController.navStack.size > 1) {
      if (navController.navStack.size > 1) navigateBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
      val showGlobalTopBar = currentScreen != AppScreen.SCHEDULE
      if (showGlobalTopBar) {
        AppTopBar(
            title = screenTitle,
            canNavigateBack = !isRootScreen,
            onNavigationIconClick = { navigateBack() },
            actions = {
              // 特殊页面的顶部栏动作按钮
              if (currentScreen == AppScreen.EXAM) {
                Box {
                  TextButton(
                      onClick = { showExamTermMenu = true },
                      colors =
                          ButtonDefaults.textButtonColors(contentColor = UbaaBrand.current.onBrand),
                  ) {
                    Text(examUiState.selectedTerm?.itemName ?: "选择学期")
                    Icon(Icons.Default.ArrowDropDown, null)
                  }
                  DropdownMenu(
                      expanded = showExamTermMenu,
                      onDismissRequest = { showExamTermMenu = false },
                  ) {
                    examUiState.terms.forEach {
                      DropdownMenuItem(
                          text = { Text(it.itemName) },
                          onClick = {
                            examViewModel?.selectTerm(it)
                            showExamTermMenu = false
                          },
                      )
                    }
                  }
                }
              } else if (currentScreen == AppScreen.GRADE) {
                val currentTermIndex = gradeUiState.terms.indexOf(gradeUiState.selectedTerm)
                IconButton(
                    onClick = {
                      if (currentTermIndex > 0) {
                        gradeViewModel?.selectTerm(gradeUiState.terms[currentTermIndex - 1])
                      }
                    },
                    enabled = currentTermIndex > 0,
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一学期")
                }
                IconButton(
                    onClick = {
                      if (
                          currentTermIndex != -1 && currentTermIndex < gradeUiState.terms.size - 1
                      ) {
                        gradeViewModel?.selectTerm(gradeUiState.terms[currentTermIndex + 1])
                      }
                    },
                    enabled =
                        currentTermIndex != -1 && currentTermIndex < gradeUiState.terms.size - 1,
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一学期")
                }
              } else if (currentScreen == AppScreen.SPOC_ASSIGNMENTS) {
                IconButton(onClick = { showSpocSortFilterDialog = true }) {
                  Icon(Icons.Default.Tune, contentDescription = "排序和筛选")
                }
              } else if (currentScreen == AppScreen.BYKC_COURSES) {
                IconButton(onClick = { showBykcFiltersDialog = true }) {
                  Icon(Icons.Default.Tune, contentDescription = "筛选课程")
                }
              } else if (currentScreen == AppScreen.JUDGE_ASSIGNMENTS) {
                IconButton(onClick = { showJudgeSortFilterDialog = true }) {
                  Icon(Icons.Default.Tune, contentDescription = "排序和筛选")
                }
              } else if (currentScreen == AppScreen.VENUE_HOME) {
                IconButton(onClick = { showVenueMembershipCodeDialog = true }) {
                  Icon(Icons.Default.QrCode2, contentDescription = "会员预约码")
                }
              } else if (currentScreen == AppScreen.HOME || currentScreen == AppScreen.REGULAR) {
                IconButton(onClick = { genericScanLauncher.launch(genericScanOptions) }) {
                  Icon(Icons.Default.QrCodeScanner, contentDescription = "扫一扫")
                }
              } else if (currentScreen == AppScreen.ICLASS_HOME) {
                IconButton(
                    onClick = { iclassScanLauncher.launch(iclassScanOptions) },
                    enabled = !signinUiState.isScanSigning,
                ) {
                  if (signinUiState.isScanSigning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = UbaaBrand.current.onBrand,
                    )
                  } else {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码签到")
                  }
                }
              }
            },
        )
      }

      val screenContentState =
          MainScreenContentState(
              currentScreen = currentScreen,
              userData = userData,
              userInfo = userInfo,
              connectionMode = connectionMode,
              availableConnectionModes = availableConnectionModes,
              todayScheduleState = todayScheduleState,
              homeIsRefreshing = homeIsRefreshing,
              homeTodoItems = homeTodoItems,
              homeTodoLoading = homeTodoLoading,
              homeTodoLoadingSources = homeTodoLoadingSources,
              homeTodoFailedSources = homeTodoFailedSources,
              signinUiState = signinUiState,
              gradeScoreWatchUiState = gradeScoreWatchUiState,
              scheduleUiState = scheduleUiState,
              bykcCoursesState = bykcCoursesState,
              bykcDetailState = bykcDetailState,
              bykcChosenState = bykcChosenState,
              bykcGrabState = bykcGrabState,
              bykcCourseFilters = bykcCourseFilters,
              features = features,
              featureLayout = appPreferences.featureLayout,
              ygdkUiState = ygdkUiState,
              selectedCourse = selectedCourse,
              selectedBykcCourseId = selectedBykcCourseId,
              selectedBykcCourseSnapshot = selectedBykcCourseSnapshot,
              selectedSpocAssignmentId = selectedSpocAssignmentId,
              selectedJudgeCourseId = selectedJudgeCourseId,
              selectedJudgeAssignmentId = selectedJudgeAssignmentId,
              scheduleViewModel = scheduleViewModel,
              examViewModel = examViewModel,
              gradeViewModel = gradeViewModel,
              gradeScoreWatchViewModel = gradeScoreWatchViewModel,
              signinViewModel = signinViewModel,
              evaluationViewModel = evaluationViewModel,
              cgyyViewModel = cgyyViewModel,
              venueViewModel = venueViewModel,
              libBookViewModel = libBookViewModel,
              dormElectricityViewModel = dormElectricityViewModel,
              campusNetworkViewModel = campusNetworkViewModel,
              campusCardViewModel = campusCardViewModel,
              libraryBorrowViewModel = libraryBorrowViewModel,
              classroomViewModel = classroomViewModel,
              spocViewModel = spocViewModel,
              judgeViewModel = judgeViewModel,
              bykcViewModel = bykcViewModel,
              ygdkViewModel = ygdkViewModel,
              bykcCoursesListState = bykcCoursesListState,
          )
      val screenActions =
          MainScreenActions(
              navigateTo = { navigateTo(it) },
              navigateBack = { navigateBack() },
              refreshHomeData = { refreshHomeData() },
              openScoresFromHomeNotice = { openScoresFromHomeNotice() },
              handleHomeTodoClick = { handleHomeTodoClick(it) },
              onConnectionModeSelected = onConnectionModeSelected,
              onFeatureLongPressWeb = { feature -> feature.webUrl?.let(::openUrl) },
              onOpenUrl = { openUrl(it) },
              onSelectedCourseChange = { selectedCourse = it },
              onSelectedBykcCourseChange = { id, snapshot ->
                selectedBykcCourseId = id
                selectedBykcCourseSnapshot = snapshot
              },
              onSelectedSpocAssignmentChange = { selectedSpocAssignmentId = it },
              onSelectedJudgeAssignmentChange = { courseId, assignmentId ->
                selectedJudgeCourseId = courseId
                selectedJudgeAssignmentId = assignmentId
              },
              onLogoutClick = { performLogout() },
          )

      Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        MainScreenContent(screenContentState, screenActions)
      }

      if (isRootScreen) {
        MainBottomBar(
            selectedTab = selectedBottomTab,
            onTabSelected = { tab ->
              when (tab) {
                BottomNavTab.HOME -> setRoot(AppScreen.HOME, BottomNavTab.HOME)
                BottomNavTab.REGULAR -> setRoot(AppScreen.REGULAR, BottomNavTab.REGULAR)
                BottomNavTab.PROFILE -> setRoot(AppScreen.PROFILE, BottomNavTab.PROFILE)
              }
            },
        )
      }
    }

    if (currentScreen == AppScreen.HOME) {
      SnackbarHost(
          hostState = homeSnackbarHostState,
          modifier =
              Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 88.dp),
      )
    }

    if (showVenueMembershipCodeDialog) {
      venueViewModel?.let { viewModel ->
        VenueMembershipCodeDialog(
            viewModel = viewModel,
            onDismiss = { showVenueMembershipCodeDialog = false },
        )
      }
    }

    scanResult?.let { content ->
      QrScanResultDialog(
          content = content,
          onOpenUrl = { url ->
            openUrl(url)
            scanResult = null
          },
          onDismiss = { scanResult = null },
      )
    }

    if (showSpocSortFilterDialog && currentScreen == AppScreen.SPOC_ASSIGNMENTS) {
      SpocSortFilterDialog(
          sortField = spocUiState.sortField,
          sortAscending = spocUiState.sortAscending,
          showExpired = spocUiState.showExpired,
          showOnlyUnsubmitted = spocUiState.showOnlyUnsubmitted,
          onDismiss = { showSpocSortFilterDialog = false },
          onApply = { sortField, sortAscending, showExpired, showOnlyUnsubmitted ->
            spocViewModel.setSortField(sortField)
            if (spocUiState.sortAscending != sortAscending) {
              spocViewModel.toggleSortDirection()
            }
            spocViewModel.setShowExpired(showExpired)
            spocViewModel.setShowOnlyUnsubmitted(showOnlyUnsubmitted)
            showSpocSortFilterDialog = false
          },
      )
    }

    if (showBykcFiltersDialog && currentScreen == AppScreen.BYKC_COURSES) {
      BykcCourseFiltersDialog(
          filters = bykcCourseFilters,
          filterOptions =
              buildBykcCourseFilterOptions(
                  bykcCoursesState.courses,
                  selectedCampuses = bykcCourseFilters.campuses,
              ),
          defaultFilters = defaultBykcFilters,
          onDismissRequest = { showBykcFiltersDialog = false },
          onFiltersChange = { updateBykcCourseFilters(it) },
      )
    }

    if (showJudgeSortFilterDialog && currentScreen == AppScreen.JUDGE_ASSIGNMENTS) {
      JudgeSortFilterDialog(
          sortField = judgeUiState.sortField,
          sortAscending = judgeUiState.sortAscending,
          showExpired = judgeUiState.showExpired,
          showOnlyUnfinished = judgeUiState.showOnlyUnfinished,
          onDismiss = { showJudgeSortFilterDialog = false },
          onApply = { sortField, sortAscending, showExpired, showOnlyUnfinished ->
            judgeViewModel.setSortField(sortField)
            if (judgeUiState.sortAscending != sortAscending) {
              judgeViewModel.toggleSortDirection()
            }
            judgeViewModel.setShowExpired(showExpired)
            judgeViewModel.setShowOnlyUnfinished(showOnlyUnfinished)
            showJudgeSortFilterDialog = false
          },
      )
    }
  }
}
