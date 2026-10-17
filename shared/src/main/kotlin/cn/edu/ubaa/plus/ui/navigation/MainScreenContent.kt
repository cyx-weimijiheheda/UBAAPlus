package cn.edu.ubaa.plus.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.model.dto.BykcCourseDto
import cn.edu.ubaa.plus.model.dto.CourseClass
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.model.dto.UserInfo
import cn.edu.ubaa.plus.ui.screens.bykc.*
import cn.edu.ubaa.plus.ui.screens.bykc.BykcChosenCoursesUiState
import cn.edu.ubaa.plus.ui.screens.bykc.BykcCourseDetailUiState
import cn.edu.ubaa.plus.ui.screens.bykc.BykcCourseFilters
import cn.edu.ubaa.plus.ui.screens.bykc.BykcCoursesUiState
import cn.edu.ubaa.plus.ui.screens.bykc.BykcViewModel
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyHomeScreen
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyLockCodeScreen
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyOrdersScreen
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyReserveFormScreen
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyReservePickerScreen
import cn.edu.ubaa.plus.ui.screens.cgyy.CgyyViewModel
import cn.edu.ubaa.plus.ui.screens.classroom.ClassroomQueryScreen
import cn.edu.ubaa.plus.ui.screens.classroom.ClassroomViewModel
import cn.edu.ubaa.plus.ui.screens.evaluation.EvaluationScreen
import cn.edu.ubaa.plus.ui.screens.evaluation.EvaluationViewModel
import cn.edu.ubaa.plus.ui.screens.exam.ExamScreen
import cn.edu.ubaa.plus.ui.screens.exam.ExamViewModel
import cn.edu.ubaa.plus.ui.screens.grade.GradeScoreWatchUiState
import cn.edu.ubaa.plus.ui.screens.grade.GradeScoreWatchViewModel
import cn.edu.ubaa.plus.ui.screens.grade.GradeScreen
import cn.edu.ubaa.plus.ui.screens.grade.GradeViewModel
import cn.edu.ubaa.plus.ui.screens.judge.JudgeAssignmentDetailScreen
import cn.edu.ubaa.plus.ui.screens.judge.JudgeAssignmentsScreen
import cn.edu.ubaa.plus.ui.screens.judge.JudgeViewModel
import cn.edu.ubaa.plus.ui.screens.libbook.LibBookBookingsScreen
import cn.edu.ubaa.plus.ui.screens.libbook.LibBookHomeScreen
import cn.edu.ubaa.plus.ui.screens.libbook.LibBookReserveScreen
import cn.edu.ubaa.plus.ui.screens.libbook.LibBookViewModel
import cn.edu.ubaa.plus.ui.screens.menu.*
import cn.edu.ubaa.plus.ui.screens.menu.HomeTodoItem
import cn.edu.ubaa.plus.ui.screens.menu.HomeTodoSource
import cn.edu.ubaa.plus.ui.screens.profile.ProfileScreen
import cn.edu.ubaa.plus.ui.screens.schedule.CourseDetailScreen
import cn.edu.ubaa.plus.ui.screens.schedule.ScheduleScreen
import cn.edu.ubaa.plus.ui.screens.schedule.ScheduleUiState
import cn.edu.ubaa.plus.ui.screens.schedule.ScheduleViewModel
import cn.edu.ubaa.plus.ui.screens.schedule.TodayScheduleState
import cn.edu.ubaa.plus.ui.screens.signin.SigninScreen
import cn.edu.ubaa.plus.ui.screens.signin.SigninUiState
import cn.edu.ubaa.plus.ui.screens.signin.SigninViewModel
import cn.edu.ubaa.plus.ui.screens.spoc.SpocAssignmentDetailScreen
import cn.edu.ubaa.plus.ui.screens.spoc.SpocAssignmentsScreen
import cn.edu.ubaa.plus.ui.screens.spoc.SpocViewModel
import cn.edu.ubaa.plus.ui.screens.venue.VenueScreen
import cn.edu.ubaa.plus.ui.screens.venue.VenueViewModel
import cn.edu.ubaa.plus.ui.screens.ygdk.YgdkClockinFormScreen
import cn.edu.ubaa.plus.ui.screens.ygdk.YgdkHomeScreen
import cn.edu.ubaa.plus.ui.screens.ygdk.YgdkUiState
import cn.edu.ubaa.plus.ui.screens.ygdk.YgdkViewModel

/** 主界面各屏幕渲染所需的只读状态与 ViewModel。 */
internal class MainScreenContentState(
    val currentScreen: AppScreen,
    val userData: UserData,
    val userInfo: UserInfo?,
    val connectionMode: ConnectionMode,
    val availableConnectionModes: List<ConnectionMode>,
    val todayScheduleState: TodayScheduleState,
    val homeIsRefreshing: Boolean,
    val homeTodoItems: List<HomeTodoItem>,
    val homeTodoLoading: Boolean,
    val homeTodoLoadingSources: List<HomeTodoSource>,
    val homeTodoFailedSources: List<HomeTodoSource>,
    val signinUiState: SigninUiState,
    val gradeScoreWatchUiState: GradeScoreWatchUiState,
    val scheduleUiState: ScheduleUiState,
    val bykcCoursesState: BykcCoursesUiState,
    val bykcDetailState: BykcCourseDetailUiState,
    val bykcChosenState: BykcChosenCoursesUiState,
    val bykcGrabState: BykcGrabState,
    val bykcCourseFilters: BykcCourseFilters,
    val ygdkUiState: YgdkUiState,
    val selectedCourse: CourseClass?,
    val selectedBykcCourseId: Long?,
    val selectedBykcCourseSnapshot: BykcCourseDto?,
    val selectedSpocAssignmentId: String?,
    val selectedJudgeCourseId: String?,
    val selectedJudgeAssignmentId: String?,
    val scheduleViewModel: ScheduleViewModel,
    val examViewModel: ExamViewModel?,
    val gradeViewModel: GradeViewModel?,
    val gradeScoreWatchViewModel: GradeScoreWatchViewModel,
    val signinViewModel: SigninViewModel,
    val evaluationViewModel: EvaluationViewModel?,
    val cgyyViewModel: CgyyViewModel?,
    val venueViewModel: VenueViewModel?,
    val libBookViewModel: LibBookViewModel?,
    val classroomViewModel: ClassroomViewModel?,
    val spocViewModel: SpocViewModel,
    val judgeViewModel: JudgeViewModel,
    val bykcViewModel: BykcViewModel,
    val ygdkViewModel: YgdkViewModel?,
    val bykcCoursesListState: LazyListState,
)

/** 主界面各屏幕需要的导航与事件回调。 */
internal class MainScreenActions(
    val navigateTo: (AppScreen) -> Unit,
    val navigateBack: () -> Unit,
    val refreshHomeData: () -> Unit,
    val openScoresFromHomeNotice: () -> Unit,
    val handleHomeTodoClick: (HomeTodoItem) -> Unit,
    val updateBykcCourseFilters: (BykcCourseFilters) -> Unit,
    val onConnectionModeSelected: (ConnectionMode) -> Unit,
    val onSelectedCourseChange: (CourseClass?) -> Unit,
    val onSelectedBykcCourseChange: (Long?, BykcCourseDto?) -> Unit,
    val onSelectedSpocAssignmentChange: (String?) -> Unit,
    val onSelectedJudgeAssignmentChange: (String?, String?) -> Unit,
    val onLogoutClick: () -> Unit,
)

/**
 * 按 [MainScreenContentState.currentScreen] 渲染对应页面。
 *
 * 从 MainAppScreen 中拆出，避免单个 Composable 同时承担导航分发与全部状态管理。
 */
@Composable
internal fun MainScreenContent(state: MainScreenContentState, actions: MainScreenActions) {
  with(state) {
    with(actions) {
      when (currentScreen) {
        AppScreen.HOME ->
            HomeScreen(
                todayClasses = todayScheduleState.todayClasses,
                isLoading = todayScheduleState.isLoading,
                isRefreshing = homeIsRefreshing,
                error = todayScheduleState.error,
                todoItems = homeTodoItems,
                todoLoading = homeTodoLoading,
                todoLoadingSources = homeTodoLoadingSources,
                todoFailedSources = homeTodoFailedSources,
                signingTodoId =
                    signinUiState.signingInCourseId?.let { courseId -> "signin:$courseId" },
                scoreUpdateNotice = gradeScoreWatchUiState.notice,
                onRetrySchedule = { scheduleViewModel.loadTodaySchedule() },
                onRefresh = { refreshHomeData() },
                onOpenScoresClick = { openScoresFromHomeNotice() },
                onDismissScoreNotice = { gradeScoreWatchViewModel.consumeNotice() },
                onTodoClick = { todoItem -> handleHomeTodoClick(todoItem) },
                onSigninTodoClick = { courseId -> signinViewModel.performSignin(courseId) },
            )
        AppScreen.REGULAR ->
            RegularFeaturesScreen(
                onScheduleClick = { navigateTo(AppScreen.SCHEDULE) },
                onExamClick = { navigateTo(AppScreen.EXAM) },
                onGradeClick = { navigateTo(AppScreen.GRADE) },
                onBykcClick = { navigateTo(AppScreen.BYKC_HOME) },
                onClassroomClick = { navigateTo(AppScreen.CLASSROOM_QUERY) },
                onSpocClick = { navigateTo(AppScreen.SPOC_ASSIGNMENTS) },
                onJudgeClick = { navigateTo(AppScreen.JUDGE_ASSIGNMENTS) },
                onLibBookClick = { navigateTo(AppScreen.LIBBOOK_HOME) },
                onVenueClick = { navigateTo(AppScreen.VENUE_HOME) },
                onCgyyClick = { navigateTo(AppScreen.CGYY_HOME) },
                onYgdkClick = { navigateTo(AppScreen.YGDK_HOME) },
                onEvaluationClick = { navigateTo(AppScreen.EVALUATION) },
            )
        AppScreen.PROFILE ->
            ProfileScreen(
                userData = userData,
                userInfo = userInfo,
                connectionMode = connectionMode,
                onSettingsClick = { navigateTo(AppScreen.SETTINGS) },
                onAboutClick = { navigateTo(AppScreen.ABOUT) },
                onLogoutClick = onLogoutClick,
            )
        AppScreen.VENUE_HOME -> venueViewModel?.let { VenueScreen(viewModel = it) }
        AppScreen.SETTINGS ->
            SettingsScreen(
                currentMode = connectionMode,
                availableModes = availableConnectionModes,
                onModeSelected = onConnectionModeSelected,
            )
        AppScreen.ABOUT -> AboutScreen()
        AppScreen.SCHEDULE ->
            ScheduleScreen(
                terms = scheduleUiState.terms,
                weeks = scheduleUiState.weeks,
                weeklySchedule = scheduleUiState.weeklySchedule,
                weekSchedules = scheduleUiState.weekSchedules,
                selectedTerm = scheduleUiState.selectedTerm,
                selectedWeek = scheduleUiState.selectedWeek,
                isLoading = scheduleUiState.isLoading,
                error = scheduleUiState.error,
                isUpdating = scheduleUiState.isUpdating,
                updatedAt = scheduleUiState.updatedAt,
                diagnosticResponse = scheduleUiState.diagnosticResponse,
                onUpdate = { scheduleViewModel.updateSchedule() },
                onRefresh = { scheduleViewModel.ensureScheduleLoaded(forceRefresh = true) },
                onImportCurrentTerm = { scheduleViewModel.updateSchedule(currentTerm = true) },
                onTermSelected = { scheduleViewModel.selectTerm(it) },
                onWeekSelected = { scheduleViewModel.selectWeek(it) },
                onNavigateBack = { navigateBack() },
                onCourseClick = {
                  onSelectedCourseChange(it)
                  navigateTo(AppScreen.COURSE_DETAIL)
                },
            )
        AppScreen.EXAM -> examViewModel?.let { ExamScreen(viewModel = it) }
        AppScreen.GRADE -> gradeViewModel?.let { GradeScreen(viewModel = it) }
        AppScreen.COURSE_DETAIL -> selectedCourse?.let { CourseDetailScreen(course = it) }
        AppScreen.BYKC_HOME ->
            BykcHomeScreen(
                onSelectCourseClick = { navigateTo(AppScreen.BYKC_COURSES) },
                onMyCoursesClick = { navigateTo(AppScreen.BYKC_CHOSEN) },
                onStatisticsClick = {
                  bykcViewModel.ensureStatisticsLoaded()
                  navigateTo(AppScreen.BYKC_STATISTICS)
                },
            )
        AppScreen.BYKC_STATISTICS -> BykcStatisticsScreen(viewModel = bykcViewModel)
        AppScreen.SIGNIN -> SigninScreen(viewModel = signinViewModel)
        AppScreen.BYKC_COURSES ->
            BykcCoursesScreen(
                courses = bykcCoursesState.courses,
                isLoading = bykcCoursesState.isLoading,
                isLoadingMore = bykcCoursesState.isLoadingMore,
                hasMorePages = bykcCoursesState.hasMorePages,
                filters = bykcCourseFilters,
                error = bykcCoursesState.error,
                listState = bykcCoursesListState,
                onCourseClick = {
                  onSelectedBykcCourseChange(it.id, it)
                  bykcViewModel.loadCourseDetail(it.id)
                  navigateTo(AppScreen.BYKC_DETAIL)
                },
                onFiltersChange = updateBykcCourseFilters,
                onRefresh = {
                  bykcViewModel.loadCourses(includeExpired = bykcCourseFilters.requiresAllCourses())
                },
                onLoadMore = {
                  bykcViewModel.loadMoreCourses(
                      includeExpired = bykcCourseFilters.requiresAllCourses()
                  )
                },
            )
        AppScreen.BYKC_DETAIL ->
            BykcCourseDetailScreen(
                course = bykcDetailState.course,
                listSnapshot = selectedBykcCourseSnapshot,
                isLoading = bykcDetailState.isLoading,
                error = bykcDetailState.error,
                operationInProgress = bykcDetailState.operationInProgress,
                operationMessage = bykcDetailState.operationMessage,
                onSelectClick = {
                  selectedBykcCourseId?.let { bykcViewModel.selectCourse(it) { _, _ -> } }
                },
                onDeselectClick = {
                  selectedBykcCourseId?.let { bykcViewModel.deselectCourse(it) { _, _ -> } }
                },
                onSignInClick = {
                  selectedBykcCourseId?.let {
                    bykcViewModel.signCourse(it, null, null, 1) { _, _ -> }
                  }
                },
                onSignOutClick = {
                  selectedBykcCourseId?.let {
                    bykcViewModel.signCourse(it, null, null, 2) { _, _ -> }
                  }
                },
                onClearMessage = { bykcViewModel.clearOperationMessage() },
                grabState = bykcGrabState,
                onGrabStart = { scheduledStart, retrySeconds ->
                  selectedBykcCourseId?.let { courseId ->
                    bykcViewModel.startGrab(
                        courseId = courseId,
                        courseName = bykcDetailState.course?.courseName ?: "",
                        scheduledStart = scheduledStart,
                        retrySeconds = retrySeconds,
                        selectEnd = bykcDetailState.course?.courseSelectEndDate,
                    )
                  }
                },
                onGrabStop = { bykcViewModel.stopGrab() },
            )
        AppScreen.BYKC_CHOSEN ->
            BykcChosenCoursesScreen(
                courses = bykcChosenState.courses,
                isLoading = bykcChosenState.isLoading,
                error = bykcChosenState.error,
                onCourseClick = {
                  onSelectedBykcCourseChange(it.courseId, null)
                  bykcViewModel.loadCourseDetail(it.courseId)
                  navigateTo(AppScreen.BYKC_DETAIL)
                },
                onRefresh = { bykcViewModel.loadChosenCourses() },
            )
        AppScreen.CLASSROOM_QUERY ->
            classroomViewModel?.let {
              ClassroomQueryScreen(viewModel = it, onBackClick = { navigateBack() })
            }
        AppScreen.CGYY_HOME ->
            CgyyHomeScreen(
                onReserveClick = { navigateTo(AppScreen.CGYY_RESERVE_PICKER) },
                onOrdersClick = {
                  cgyyViewModel?.ensureOrdersLoaded()
                  navigateTo(AppScreen.CGYY_ORDERS)
                },
                onLockCodeClick = { navigateTo(AppScreen.CGYY_LOCK_CODE) },
            )
        AppScreen.CGYY_RESERVE_PICKER ->
            cgyyViewModel?.let {
              CgyyReservePickerScreen(
                  viewModel = it,
                  onNext = { navigateTo(AppScreen.CGYY_RESERVE_FORM) },
              )
            }
        AppScreen.CGYY_RESERVE_FORM ->
            cgyyViewModel?.let {
              CgyyReserveFormScreen(
                  viewModel = it,
                  onBackToSelection = { navigateBack() },
                  onSubmitSuccess = { navigateTo(AppScreen.CGYY_ORDERS) },
              )
            }
        AppScreen.CGYY_ORDERS -> cgyyViewModel?.let { CgyyOrdersScreen(viewModel = it) }
        AppScreen.CGYY_LOCK_CODE -> cgyyViewModel?.let { CgyyLockCodeScreen(viewModel = it) }
        AppScreen.EVALUATION -> evaluationViewModel?.let { EvaluationScreen(viewModel = it) }
        AppScreen.YGDK_HOME ->
            ygdkViewModel?.let {
              YgdkHomeScreen(
                  uiState = ygdkUiState,
                  onRefresh = { it.refreshAll() },
                  onLoadMore = { it.loadMoreRecords() },
                  onAddClick = { navigateTo(AppScreen.YGDK_FORM) },
                  onHomeReminderEnabledChange = { enabled -> it.setHomeReminderEnabled(enabled) },
                  onMessageConsumed = { it.clearSubmitMessage() },
              )
            }
        AppScreen.YGDK_FORM ->
            ygdkViewModel?.let { viewModel ->
              YgdkClockinFormScreen(
                  uiState = ygdkUiState,
                  imagePicker =
                      cn.edu.ubaa.plus.ui.common.util.rememberPlatformImagePicker(
                          onImagePicked = { viewModel.setPhoto(it) },
                          onError = { viewModel.showMessage(it) },
                      ),
                  onItemSelected = { viewModel.updateItemId(it) },
                  onStartTimeChange = { viewModel.updateStartTime(it) },
                  onEndTimeChange = { viewModel.updateEndTime(it) },
                  onPlaceChange = { viewModel.updatePlace(it) },
                  onShareChange = { viewModel.setShareToSquare(it) },
                  onClearPhoto = { viewModel.clearPhoto() },
                  onSubmit = { viewModel.submitClockin { navigateBack() } },
              )
            }
        AppScreen.SPOC_ASSIGNMENTS ->
            SpocAssignmentsScreen(
                viewModel = spocViewModel,
                onAssignmentClick = {
                  onSelectedSpocAssignmentChange(it.assignmentId)
                  spocViewModel.loadAssignmentDetail(it.assignmentId)
                  navigateTo(AppScreen.SPOC_ASSIGNMENT_DETAIL)
                },
            )
        AppScreen.SPOC_ASSIGNMENT_DETAIL ->
            SpocAssignmentDetailScreen(
                viewModel = spocViewModel,
                onRetry = {
                  selectedSpocAssignmentId?.let { assignmentId ->
                    spocViewModel.loadAssignmentDetail(assignmentId)
                  }
                },
            )
        AppScreen.JUDGE_ASSIGNMENTS ->
            JudgeAssignmentsScreen(
                viewModel = judgeViewModel,
                onAssignmentClick = {
                  onSelectedJudgeAssignmentChange(it.courseId, it.assignmentId)
                  judgeViewModel.loadAssignmentDetail(it.courseId, it.assignmentId)
                  navigateTo(AppScreen.JUDGE_ASSIGNMENT_DETAIL)
                },
            )
        AppScreen.JUDGE_ASSIGNMENT_DETAIL ->
            JudgeAssignmentDetailScreen(
                viewModel = judgeViewModel,
                onRetry = {
                  val courseId = selectedJudgeCourseId
                  val assignmentId = selectedJudgeAssignmentId
                  if (courseId != null && assignmentId != null) {
                    judgeViewModel.loadAssignmentDetail(courseId, assignmentId)
                  }
                },
            )
        AppScreen.LIBBOOK_HOME ->
            LibBookHomeScreen(
                onReserveClick = { navigateTo(AppScreen.LIBBOOK_RESERVE) },
                onBookingsClick = {
                  libBookViewModel?.ensureBookingsLoaded()
                  navigateTo(AppScreen.LIBBOOK_BOOKINGS)
                },
            )
        AppScreen.LIBBOOK_RESERVE ->
            libBookViewModel?.let {
              LibBookReserveScreen(
                  viewModel = it,
                  onSubmitSuccess = { navigateTo(AppScreen.LIBBOOK_BOOKINGS) },
              )
            }
        AppScreen.LIBBOOK_BOOKINGS ->
            libBookViewModel?.let { LibBookBookingsScreen(viewModel = it) }
      }
    }
  }
}
