package cn.edu.ubaa.plus.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.SigninApi
import cn.edu.ubaa.plus.api.feature.parseIclassSigninPayload
import cn.edu.ubaa.plus.model.dto.IclassAttendanceCourseDto
import cn.edu.ubaa.plus.model.dto.IclassAttendanceRecordDto
import cn.edu.ubaa.plus.model.dto.IclassAttendanceTermDto
import cn.edu.ubaa.plus.model.dto.SigninClassDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 课堂签到模块 UI 状态。 */
data class SigninUiState(
    val isLoading: Boolean = false,
    val classes: List<SigninClassDto> = emptyList(),
    val error: String? = null,
    /** 签到操作的结果提示（如“签到成功”）。 */
    val signinResult: String? = null,
    /** 当前正在执行签到的课程 ID（用于显示局部加载状态）。 */
    val signingInCourseId: String? = null,
    /** 扫码签到请求进行中。 */
    val isScanSigning: Boolean = false,
    /** 可选学年学期。 */
    val attendanceTerms: List<IclassAttendanceTermDto> = emptyList(),
    /** 当前学期下的课程。 */
    val attendanceCourses: List<IclassAttendanceCourseDto> = emptyList(),
    val attendanceSelectedTermCode: String? = null,
    val attendanceSelectedCourseId: String? = null,
    /** 当前选中课程对应的考勤记录。 */
    val attendanceRecords: List<IclassAttendanceRecordDto> = emptyList(),
    val attendanceTermName: String? = null,
    val attendanceLoading: Boolean = false,
    val attendanceTermsLoading: Boolean = false,
    val attendanceCoursesLoading: Boolean = false,
    val attendanceError: String? = null,
) {
  val selectedAttendanceTerm: IclassAttendanceTermDto?
    get() = attendanceTerms.firstOrNull { it.code == attendanceSelectedTermCode }

  val selectedAttendanceCourse: IclassAttendanceCourseDto?
    get() = attendanceCourses.firstOrNull { it.courseId == attendanceSelectedCourseId }
}

/** 管理课堂签到功能的 ViewModel。 */
class SigninViewModel(
    private val signinApi: SigninApi = SigninApi(),
) : ViewModel() {
  private var loadedOnce = false
  private var attendanceLoadedOnce = false
  private var attendanceEpoch = 0

  private val _uiState = MutableStateFlow(SigninUiState())
  /** 签到状态流。 */
  val uiState: StateFlow<SigninUiState> = _uiState.asStateFlow()

  fun ensureTodayLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    loadTodayClasses()
  }

  internal fun hasTodayLoaded(): Boolean = loadedOnce

  /** 拉取今日所有可签到的课堂。 */
  fun loadTodayClasses() {
    loadedOnce = true
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      signinApi
          .getTodayClasses()
          .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, classes = it.data) }
          .onFailure {
            _uiState.value = _uiState.value.copy(isLoading = false, error = it.message ?: "加载课程失败")
          }
    }
  }

  /** 加载学年学期与课程索引，并自动选中当前学期第一门课程。 */
  fun ensureAttendanceLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && attendanceLoadedOnce) return
    reloadAttendanceIndex(preserveSelection = true)
  }

  /** 重新加载学期、课程与考勤；[preserveSelection] 为 true 时尽量保留当前选择。 */
  fun reloadAttendanceIndex(preserveSelection: Boolean = true) {
    attendanceLoadedOnce = true
    val epoch = ++attendanceEpoch
    viewModelScope.launch {
      _uiState.update {
        it.copy(
            attendanceTermsLoading = it.attendanceTerms.isEmpty(),
            attendanceCoursesLoading = true,
            attendanceLoading = true,
            attendanceError = null,
        )
      }
      val result = signinApi.getAttendanceTerms()
      if (epoch != attendanceEpoch) return@launch
      val terms = result.getOrNull()
      if (terms == null) {
        _uiState.update {
          it.copy(
              attendanceTermsLoading = false,
              attendanceCoursesLoading = false,
              attendanceLoading = false,
              attendanceError = result.exceptionOrNull()?.message ?: DEFAULT_ATTENDANCE_ERROR,
          )
        }
        return@launch
      }
      if (terms.isEmpty()) {
        _uiState.update {
          it.copy(
              attendanceTerms = emptyList(),
              attendanceCourses = emptyList(),
              attendanceSelectedTermCode = null,
              attendanceSelectedCourseId = null,
              attendanceRecords = emptyList(),
              attendanceTermsLoading = false,
              attendanceCoursesLoading = false,
              attendanceLoading = false,
          )
        }
        return@launch
      }

      val previousTermCode = _uiState.value.attendanceSelectedTermCode
      val previousCourseId = _uiState.value.attendanceSelectedCourseId
      val term =
          (if (preserveSelection) terms.firstOrNull { it.code == previousTermCode } else null)
              ?: terms.firstOrNull { it.isCurrent }
              ?: terms.first()
      _uiState.update {
        it.copy(
            attendanceTerms = terms,
            attendanceTermsLoading = false,
            attendanceSelectedTermCode = term.code,
            attendanceTermName = term.name,
            attendanceCourses = emptyList(),
            attendanceSelectedCourseId = null,
            attendanceRecords = emptyList(),
            attendanceCoursesLoading = true,
            attendanceLoading = true,
        )
      }
      loadCoursesAndRecords(
          epoch = epoch,
          termCode = term.code,
          preferredCourseId = if (preserveSelection) previousCourseId else null,
          preserveCourse = preserveSelection,
      )
    }
  }

  /** 切换学年学期：清空课程与记录，自动选中新学期第一门课程。 */
  fun selectAttendanceTerm(termCode: String) {
    val term = _uiState.value.attendanceTerms.firstOrNull { it.code == termCode } ?: return
    if (
        _uiState.value.attendanceSelectedTermCode == termCode &&
            _uiState.value.attendanceCourses.isNotEmpty()
    ) {
      return
    }
    val epoch = ++attendanceEpoch
    _uiState.update {
      it.copy(
          attendanceSelectedTermCode = term.code,
          attendanceTermName = term.name,
          attendanceCourses = emptyList(),
          attendanceSelectedCourseId = null,
          attendanceRecords = emptyList(),
          attendanceCoursesLoading = true,
          attendanceLoading = true,
          attendanceError = null,
      )
    }
    viewModelScope.launch {
      loadCoursesAndRecords(
          epoch = epoch,
          termCode = term.code,
          preferredCourseId = null,
          preserveCourse = false,
      )
    }
  }

  /** 切换课程并加载该课程考勤。 */
  fun selectAttendanceCourse(courseId: String) {
    val state = _uiState.value
    val termCode = state.attendanceSelectedTermCode ?: return
    if (state.attendanceSelectedCourseId == courseId && state.attendanceRecords.isNotEmpty()) return
    val epoch = ++attendanceEpoch
    _uiState.update {
      it.copy(
          attendanceSelectedCourseId = courseId,
          attendanceRecords = emptyList(),
          attendanceLoading = true,
          attendanceError = null,
      )
    }
    viewModelScope.launch { loadAttendanceRecords(epoch, termCode, courseId) }
  }

  /** 按失败阶段重试：学期、课程或考勤。 */
  fun retryAttendance() {
    val state = _uiState.value
    when {
      state.attendanceTerms.isEmpty() -> reloadAttendanceIndex(preserveSelection = false)
      state.attendanceCourses.isEmpty() -> {
        val termCode = state.attendanceSelectedTermCode
        if (termCode == null) {
          reloadAttendanceIndex(preserveSelection = false)
        } else {
          val epoch = ++attendanceEpoch
          _uiState.update {
            it.copy(
                attendanceCoursesLoading = true,
                attendanceLoading = true,
                attendanceError = null,
            )
          }
          viewModelScope.launch {
            loadCoursesAndRecords(epoch, termCode, preferredCourseId = null, preserveCourse = false)
          }
        }
      }
      state.attendanceSelectedCourseId == null -> reloadAttendanceIndex(preserveSelection = true)
      else -> {
        val termCode = state.attendanceSelectedTermCode ?: return
        val epoch = ++attendanceEpoch
        _uiState.update { it.copy(attendanceLoading = true, attendanceError = null) }
        viewModelScope.launch {
          loadAttendanceRecords(epoch, termCode, state.attendanceSelectedCourseId)
        }
      }
    }
  }

  private suspend fun loadCoursesAndRecords(
      epoch: Int,
      termCode: String,
      preferredCourseId: String?,
      preserveCourse: Boolean,
  ) {
    val result = signinApi.getAttendanceCourses(termCode)
    if (epoch != attendanceEpoch) return
    val courses = result.getOrNull()
    if (courses == null) {
      _uiState.update {
        it.copy(
            attendanceCoursesLoading = false,
            attendanceLoading = false,
            attendanceError = result.exceptionOrNull()?.message ?: DEFAULT_ATTENDANCE_ERROR,
        )
      }
      return
    }
    val course =
        (if (preserveCourse) courses.firstOrNull { it.courseId == preferredCourseId } else null)
            ?: courses.firstOrNull()
    _uiState.update {
      it.copy(
          attendanceCourses = courses,
          attendanceSelectedCourseId = course?.courseId,
          attendanceRecords = emptyList(),
          attendanceCoursesLoading = false,
          attendanceLoading = course != null,
      )
    }
    if (course == null) {
      _uiState.update { it.copy(attendanceLoading = false) }
      return
    }
    loadAttendanceRecords(epoch, termCode, course.courseId)
  }

  private suspend fun loadAttendanceRecords(epoch: Int, termCode: String, courseId: String) {
    val result = signinApi.getAttendance(termCode, courseId)
    if (epoch != attendanceEpoch) return
    result
        .onSuccess { response ->
          _uiState.update {
            it.copy(
                attendanceLoading = false,
                attendanceRecords = response.data,
                attendanceTermName = response.termName ?: it.attendanceTermName,
                attendanceError = null,
            )
          }
        }
        .onFailure { error ->
          _uiState.update {
            it.copy(
                attendanceLoading = false,
                attendanceError = error.message ?: DEFAULT_ATTENDANCE_ERROR,
            )
          }
        }
  }

  /** 解析扫码结果并提交签到；无效二维码只提示，不发起请求。 */
  fun performScannedSignin(rawContent: String) {
    val payload = parseIclassSigninPayload(rawContent)
    if (payload == null) {
      _uiState.value = _uiState.value.copy(signinResult = "无法识别的签到二维码")
      return
    }
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isScanSigning = true, signinResult = null)
      signinApi
          .performSignin(payload.courseSchedId, payload.timestamp)
          .onSuccess { response ->
            _uiState.value =
                _uiState.value.copy(
                    isScanSigning = false,
                    signinResult =
                        if (response.success) {
                          "扫码签到成功"
                        } else {
                          "扫码签到失败：${response.message}"
                        },
                )
            if (response.success) refreshAfterSignin()
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isScanSigning = false,
                    signinResult = "扫码签到异常：${it.message}",
                )
          }
    }
  }

  private fun refreshAfterSignin() {
    loadedOnce = false
    ensureTodayLoaded(forceRefresh = true)
    if (attendanceLoadedOnce) {
      reloadAttendanceIndex(preserveSelection = true)
    }
  }

  /** 提交签到请求。 */
  fun performSignin(courseId: String) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(signingInCourseId = courseId, signinResult = null)
      signinApi
          .performSignin(courseId)
          .onSuccess { response ->
            _uiState.value =
                _uiState.value.copy(
                    signingInCourseId = null,
                    signinResult = if (response.success) "签到成功" else "签到失败: ${response.message}",
                )
            if (response.success) refreshAfterSignin()
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(signingInCourseId = null, signinResult = "签到异常: ${it.message}")
          }
    }
  }

  /** 重置内部加载标记与 UI 状态，用于连接模式切换等场景。 */
  fun resetLoadedState() {
    loadedOnce = false
    attendanceLoadedOnce = false
    attendanceEpoch++
    _uiState.value = SigninUiState()
  }

  /** 清除当前的签到结果提示。 */
  fun clearSigninResult() {
    _uiState.value = _uiState.value.copy(signinResult = null)
  }

  private companion object {
    const val DEFAULT_ATTENDANCE_ERROR = "考勤查询失败，请以校园网登录查看"
  }
}
