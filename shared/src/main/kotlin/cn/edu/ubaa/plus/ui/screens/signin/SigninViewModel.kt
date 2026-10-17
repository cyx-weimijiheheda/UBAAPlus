package cn.edu.ubaa.plus.ui.screens.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.SigninApi
import cn.edu.ubaa.plus.api.feature.parseIclassSigninPayload
import cn.edu.ubaa.plus.model.dto.IclassAttendanceRecordDto
import cn.edu.ubaa.plus.model.dto.SigninClassDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** 本学期考勤记录。 */
    val attendanceRecords: List<IclassAttendanceRecordDto> = emptyList(),
    val attendanceTermName: String? = null,
    val attendanceLoading: Boolean = false,
    val attendanceError: String? = null,
)

/** 管理课堂签到功能的 ViewModel。 */
class SigninViewModel(
    private val signinApi: SigninApi = SigninApi(),
) : ViewModel() {
  private var loadedOnce = false
  private var attendanceLoadedOnce = false

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

  /** 加载本学期考勤记录。 */
  fun ensureAttendanceLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && attendanceLoadedOnce) return
    loadAttendance()
  }

  fun loadAttendance() {
    attendanceLoadedOnce = true
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(attendanceLoading = true, attendanceError = null)
      signinApi
          .getAttendance()
          .onSuccess { response ->
            _uiState.value =
                _uiState.value.copy(
                    attendanceLoading = false,
                    attendanceRecords = response.data,
                    attendanceTermName = response.termName,
                    attendanceError = null,
                )
          }
          .onFailure {
            _uiState.value =
                _uiState.value.copy(
                    attendanceLoading = false,
                    attendanceError = it.message ?: "考勤查询失败，请稍后重试",
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
    attendanceLoadedOnce = false
    ensureAttendanceLoaded(forceRefresh = true)
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
    _uiState.value = SigninUiState()
  }

  /** 清除当前的签到结果提示。 */
  fun clearSigninResult() {
    _uiState.value = _uiState.value.copy(signinResult = null)
  }
}
