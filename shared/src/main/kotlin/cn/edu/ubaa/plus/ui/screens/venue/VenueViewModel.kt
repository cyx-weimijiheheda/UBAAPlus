package cn.edu.ubaa.plus.ui.screens.venue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.AppForegroundMonitor
import cn.edu.ubaa.plus.api.storage.CredentialStore
import cn.edu.ubaa.plus.api.venue.VenueApi
import cn.edu.ubaa.plus.api.venue.VenueAuthOutcome
import cn.edu.ubaa.plus.api.venue.VenueBuddy
import cn.edu.ubaa.plus.api.venue.VenueCaptchaAnswer
import cn.edu.ubaa.plus.api.venue.VenueDayInfo
import cn.edu.ubaa.plus.api.venue.VenueGrabController
import cn.edu.ubaa.plus.api.venue.VenueGrabState
import cn.edu.ubaa.plus.api.venue.VenueGrabTask
import cn.edu.ubaa.plus.api.venue.VenueMembershipCode
import cn.edu.ubaa.plus.api.venue.VenueOrderRecord
import cn.edu.ubaa.plus.api.venue.VenuePoint
import cn.edu.ubaa.plus.api.venue.VenueSite
import cn.edu.ubaa.plus.api.venue.VenueSpace
import cn.edu.ubaa.plus.api.venue.VenueSpaceMode
import cn.edu.ubaa.plus.api.venue.VenueTimeSlot
import cn.edu.ubaa.plus.api.venue.campusLabel
import cn.edu.ubaa.plus.api.venue.sportCategoryLabel
import cn.edu.ubaa.plus.api.venue.venueCampusOptions
import cn.edu.ubaa.plus.api.venue.venueOptionLabel
import cn.edu.ubaa.plus.api.venue.venueSiteOptions
import cn.edu.ubaa.plus.api.venue.venueSportOptions
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

private const val ORDER_PAGE_SIZE = 20

enum class VenueAuthPhase {
  CHECKING,
  READY,
  NEEDS_LOGIN,
  NEEDS_CAPTCHA,
  FAILED,
}

data class VenueLoginForm(
    val username: String = "",
    val password: String = "",
    val captcha: String = "",
    val captchaImage: String? = null,
    val execution: String? = null,
    val rememberPassword: Boolean = false,
    val error: String? = null,
)

/** 运动场地预约记录弹窗状态。 */
data class VenueOrderHistoryUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val records: List<VenueOrderRecord> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
)

data class VenueMembershipCodeUiState(
    val isLoading: Boolean = false,
    val code: VenueMembershipCode? = null,
    val error: String? = null,
)

data class VenueUiState(
    val authPhase: VenueAuthPhase = VenueAuthPhase.CHECKING,
    val authMessage: String? = null,
    val loginForm: VenueLoginForm = VenueLoginForm(),
    val isLoggingIn: Boolean = false,
    val sites: List<VenueSite> = emptyList(),
    val selectedSiteId: Int? = null,
    val advanceDays: Int = 3,
    val selectedDate: String = currentVenueDate().toString(),
    val slotTemplate: List<VenueTimeSlot> = emptyList(),
    val dayInfo: VenueDayInfo? = null,
    val isLoadingDayInfo: Boolean = false,
    val dayInfoMessage: String? = null,
    val selectedStartBegin: String? = null,
    val selectedSpaceId: Int? = null,
    val durationHours: Int = 1,
    val spaceMode: VenueSpaceMode = VenueSpaceMode.FIXED,
    val randomSpace: Boolean = false,
    val fallbackAnySpace: Boolean = true,
    val buddies: List<VenueBuddy> = emptyList(),
    val selectedBuddyIds: Set<Int> = emptySet(),
    val buddyLimit: Int = 3,
    val phone: String = "",
    val pollIntervalText: String = "0.35",
    val useSchedule: Boolean = true,
    val grabAtText: String = "07:00:00",
    val message: String? = null,
) {
  val selectedSite: VenueSite?
    get() = sites.firstOrNull { it.id == selectedSiteId }

  /** 三级下拉：校区 → 运动类型 → 场馆。 */
  val campusOptions: List<String>
    get() = venueCampusOptions(sites)

  val selectedCampus: String
    get() = selectedSite?.campusLabel() ?: campusOptions.firstOrNull().orEmpty()

  val sportOptions: List<String>
    get() = venueSportOptions(sites, selectedCampus)

  val selectedSport: String
    get() = selectedSite?.sportCategoryLabel() ?: sportOptions.firstOrNull().orEmpty()

  val venueOptions: List<VenueSite>
    get() = venueSiteOptions(sites, selectedCampus, selectedSport)

  val selectedVenueLabel: String
    get() = selectedSite?.venueOptionLabel().orEmpty()

  val availableDates: List<LocalDate>
    get() {
      val today = currentVenueDate()
      return (0..advanceDays.coerceIn(0, 14)).map {
        today.plus(it, kotlinx.datetime.DateTimeUnit.DAY)
      }
    }

  /** 下拉框只列出至少还有一块空闲场地的时段；未放票时回退到时段模板。 */
  val selectableSlots: List<VenueTimeSlot>
    get() {
      val day = dayInfo ?: return slotTemplate
      return day.slots.filter { slot ->
        day.spaces.any { space -> space.slot(slot.timeId)?.isFree == true }
      }
    }

  /** 当前目标时段的展示文本；目标已不可用时保留选择并附加提示。 */
  val selectedSlotLabel: String
    get() {
      val start = selectedStartBegin ?: return ""
      val source = dayInfo?.slots ?: slotTemplate
      val slot = source.firstOrNull { it.beginTime == start }
      val label = slot?.label ?: start
      val unavailable =
          dayInfo != null && slot != null && selectableSlots.none { it.timeId == slot.timeId }
      return if (unavailable) "$label（当前无空闲）" else label
    }
}

/**
 * 运动场地页面的状态管理。
 *
 * 认证、场地列表、可用性表格、同伴与抢单参数都在这里；抢单任务由 [VenueGrabController] 持有， 离开页面但应用仍在前台时继续运行。
 */
class VenueViewModel(
    private val venueApi: VenueApi = VenueApi(),
    foreground: StateFlow<Boolean> = AppForegroundMonitor.foreground,
    now: () -> Instant = { Clock.System.now() },
) : ViewModel() {
  private val _state = MutableStateFlow(VenueUiState())
  val state: StateFlow<VenueUiState> = _state.asStateFlow()

  private val grabController = VenueGrabController(venueApi, viewModelScope, foreground, now)
  val grabState: StateFlow<VenueGrabState> = grabController.state

  private val _membershipCodeState = MutableStateFlow(VenueMembershipCodeUiState())
  val membershipCodeState: StateFlow<VenueMembershipCodeUiState> =
      _membershipCodeState.asStateFlow()
  private var membershipCodeEpoch = 0

  private val _orderHistoryState = MutableStateFlow(VenueOrderHistoryUiState())
  val orderHistoryState: StateFlow<VenueOrderHistoryUiState> = _orderHistoryState.asStateFlow()
  private var orderEpoch = 0

  private var initialized = false
  private var autoLoginAttempted = false
  private var dayInfoEpoch = 0
  private var templateEpoch = 0
  private var templateLoadedForSite: Int? = null

  fun ensureInitialized() {
    if (initialized) return
    initialized = true
    viewModelScope.launch { initialize() }
  }

  fun resetLoadedState() {
    grabController.stop()
    venueApi.logout()
    initialized = false
    autoLoginAttempted = false
    templateLoadedForSite = null
    templateEpoch++
    _state.value = VenueUiState()
    clearMembershipCode()
    clearOrderHistory()
  }

  private suspend fun initialize() {
    _state.update {
      it.copy(
          authPhase = VenueAuthPhase.CHECKING,
          authMessage = null,
          message = null,
      )
    }
    handleAuthOutcome(venueApi.resumeSession())
  }

  private suspend fun handleAuthOutcome(outcome: VenueAuthOutcome) {
    when (outcome) {
      VenueAuthOutcome.Ready -> onAuthenticated()
      is VenueAuthOutcome.NeedsCredentials -> {
        val username = CredentialStore.getUsername().orEmpty()
        val password = CredentialStore.getPassword().orEmpty()
        _state.update {
          it.copy(
              authPhase = VenueAuthPhase.NEEDS_LOGIN,
              authMessage = outcome.message ?: "需要登录运动场地系统",
              loginForm =
                  it.loginForm.copy(
                      username = it.loginForm.username.ifBlank { username },
                      password = it.loginForm.password.ifBlank { password },
                      error = null,
                  ),
          )
        }
        if (!autoLoginAttempted && username.isNotBlank() && password.isNotBlank()) {
          autoLoginAttempted = true
          loginWith(username, password, null, null, remember = false)
        }
      }
      is VenueAuthOutcome.NeedsCaptcha -> {
        _state.update {
          it.copy(
              authPhase = VenueAuthPhase.NEEDS_CAPTCHA,
              authMessage = "请输入统一认证验证码",
              loginForm =
                  it.loginForm.copy(
                      username = it.loginForm.username.ifBlank { outcome.username },
                      captchaImage = outcome.captcha.base64Image,
                      execution = outcome.execution,
                      captcha = "",
                      error = null,
                  ),
          )
        }
      }
      is VenueAuthOutcome.Failed ->
          _state.update {
            when (it.authPhase) {
              VenueAuthPhase.NEEDS_LOGIN,
              VenueAuthPhase.NEEDS_CAPTCHA ->
                  it.copy(
                      authPhase = VenueAuthPhase.NEEDS_LOGIN,
                      authMessage = outcome.message,
                      loginForm = it.loginForm.copy(error = outcome.message),
                  )
              else ->
                  it.copy(
                      authPhase = VenueAuthPhase.NEEDS_LOGIN,
                      authMessage = outcome.message,
                      loginForm = it.loginForm.copy(error = outcome.message),
                  )
            }
          }
    }
  }

  private suspend fun onAuthenticated() {
    _state.update {
      it.copy(
          authPhase = VenueAuthPhase.READY,
          authMessage = null,
          loginForm = VenueLoginForm(),
      )
    }
    loadSites()
  }

  fun login() {
    val form = _state.value.loginForm
    if (form.username.isBlank() || form.password.isBlank()) {
      _state.update { it.copy(loginForm = form.copy(error = "学号和密码不能为空")) }
      return
    }
    if (_state.value.authPhase == VenueAuthPhase.NEEDS_CAPTCHA && form.captcha.isBlank()) {
      _state.update { it.copy(loginForm = form.copy(error = "请输入验证码")) }
      return
    }
    viewModelScope.launch {
      _state.update { it.copy(isLoggingIn = true, loginForm = form.copy(error = null)) }
      val outcome =
          venueApi.login(
              username = form.username,
              password = form.password,
              captcha = form.captcha.takeIf { it.isNotBlank() },
              execution = form.execution,
          )
      _state.update { it.copy(isLoggingIn = false) }
      handleAuthOutcome(outcome)
      if (outcome == VenueAuthOutcome.Ready && form.rememberPassword) {
        CredentialStore.setRememberPassword(true)
        CredentialStore.saveCredentials(form.username, form.password)
      }
    }
  }

  private suspend fun loginWith(
      username: String,
      password: String,
      captcha: String?,
      execution: String?,
      remember: Boolean,
  ) {
    val outcome = venueApi.login(username, password, captcha, execution)
    handleAuthOutcome(outcome)
    if (outcome == VenueAuthOutcome.Ready && remember) {
      CredentialStore.setRememberPassword(true)
      CredentialStore.saveCredentials(username, password)
    }
  }

  fun updateLoginForm(
      username: String? = null,
      password: String? = null,
      captcha: String? = null,
      remember: Boolean? = null,
  ) {
    _state.update {
      it.copy(
          loginForm =
              it.loginForm.copy(
                  username = username ?: it.loginForm.username,
                  password = password ?: it.loginForm.password,
                  captcha = captcha ?: it.loginForm.captcha,
                  rememberPassword = remember ?: it.loginForm.rememberPassword,
                  error = null,
              )
      )
    }
  }

  private suspend fun loadSites() {
    venueApi
        .getSites()
        .onSuccess { result ->
          val current = _state.value.selectedSiteId
          val selected = result.sites.firstOrNull { it.id == current } ?: result.sites.firstOrNull()
          _state.update {
            it.copy(
                sites = result.sites,
                advanceDays = result.advanceReservationDays,
                selectedSiteId = selected?.id,
                selectedDate = currentVenueDate().toString(),
                dayInfo = null,
                slotTemplate = emptyList(),
                selectedStartBegin = null,
                selectedSpaceId = null,
            )
          }
          templateLoadedForSite = null
          selected?.let {
            loadSlotTemplate(it.id)
            loadDayInfo()
          }
          if (result.sites.isEmpty()) {
            _state.update { it.copy(dayInfoMessage = "没有可预约的运动场地") }
          }
        }
        .onFailure { error ->
          _state.update { state ->
            if (
                (error as? cn.edu.ubaa.plus.api.auth.ApiCallException)?.code ==
                    "venue_unauthenticated"
            ) {
              state.copy(authPhase = VenueAuthPhase.NEEDS_LOGIN, authMessage = "登录状态已失效")
            } else {
              state.copy(message = error.message ?: "运动场地列表加载失败")
            }
          }
        }
    loadBuddies()
  }

  private suspend fun loadBuddies() {
    venueApi.getBuddies().onSuccess { buddies ->
      _state.update {
        it.copy(
            buddies = buddies,
            selectedBuddyIds =
                it.selectedBuddyIds.filter { id -> buddies.any { b -> b.id == id } }.toSet(),
        )
      }
    }
  }

  fun selectSite(siteId: Int) {
    if (_state.value.selectedSiteId == siteId) return
    templateEpoch++
    _state.update {
      it.copy(
          selectedSiteId = siteId,
          dayInfo = null,
          slotTemplate = emptyList(),
          selectedStartBegin = null,
          selectedSpaceId = null,
          dayInfoMessage = null,
      )
    }
    viewModelScope.launch {
      loadSlotTemplate(siteId)
      loadDayInfo()
    }
  }

  /** 切换校区：自动选中该校区第一片场地。 */
  fun selectCampus(campus: String) {
    val state = _state.value
    if (state.selectedCampus == campus) return
    val sports = venueSportOptions(state.sites, campus)
    val sport = sports.firstOrNull { it == state.selectedSport } ?: sports.firstOrNull().orEmpty()
    val site = venueSiteOptions(state.sites, campus, sport).firstOrNull() ?: return
    selectSite(site.id)
  }

  /** 切换运动类型：自动选中该校区该运动的第一片场地。 */
  fun selectSport(sport: String) {
    val state = _state.value
    if (state.selectedSport == sport) return
    val site = venueSiteOptions(state.sites, state.selectedCampus, sport).firstOrNull() ?: return
    selectSite(site.id)
  }

  /** 切换场馆（具体可预约场地）。 */
  fun selectVenue(siteId: Int) {
    selectSite(siteId)
  }

  fun selectDate(date: LocalDate) {
    _state.update {
      it.copy(
          selectedDate = date.toString(),
          dayInfo = null,
          selectedStartBegin = null,
          selectedSpaceId = null,
          dayInfoMessage = null,
      )
    }
    viewModelScope.launch { loadDayInfo() }
  }

  fun refreshDayInfo() {
    viewModelScope.launch { loadDayInfo() }
  }

  /** 加载会员码；同一时间只允许一个请求，过期响应会被丢弃。 */
  fun loadMembershipCode() {
    if (_state.value.authPhase != VenueAuthPhase.READY) {
      _membershipCodeState.update { it.copy(isLoading = false, error = "请先登录运动场地系统") }
      return
    }
    if (_membershipCodeState.value.isLoading) return
    val epoch = ++membershipCodeEpoch
    _membershipCodeState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      venueApi
          .getMembershipCode()
          .onSuccess { code ->
            if (epoch != membershipCodeEpoch) return@onSuccess
            _membershipCodeState.value =
                VenueMembershipCodeUiState(isLoading = false, code = code, error = null)
          }
          .onFailure { error ->
            if (epoch != membershipCodeEpoch) return@onFailure
            _membershipCodeState.update {
              it.copy(isLoading = false, error = error.message ?: "会员码加载失败")
            }
          }
    }
  }

  fun clearMembershipCode() {
    membershipCodeEpoch++
    _membershipCodeState.value = VenueMembershipCodeUiState()
  }

  /** 加载第一页预约记录。 */
  fun loadOrderHistory(refresh: Boolean = true) {
    if (_state.value.authPhase != VenueAuthPhase.READY) {
      _orderHistoryState.update { it.copy(isLoading = false, error = "请先登录运动场地系统") }
      return
    }
    if (_orderHistoryState.value.isLoading) return
    val epoch = ++orderEpoch
    _orderHistoryState.update {
      it.copy(
          isLoading = true,
          isLoadingMore = false,
          error = null,
          records = if (refresh) emptyList() else it.records,
      )
    }
    viewModelScope.launch {
      venueApi
          .getMyOrders(page = 0, size = ORDER_PAGE_SIZE)
          .onSuccess { page ->
            if (epoch != orderEpoch) return@onSuccess
            _orderHistoryState.value =
                VenueOrderHistoryUiState(
                    records = page.records,
                    hasMore = page.records.size < page.totalElements,
                )
          }
          .onFailure { error ->
            if (epoch != orderEpoch) return@onFailure
            _orderHistoryState.update {
              it.copy(isLoading = false, error = error.message ?: "预约记录加载失败")
            }
          }
    }
  }

  /** 追加下一页预约记录。 */
  fun loadMoreOrderHistory() {
    val current = _orderHistoryState.value
    if (current.isLoading || current.isLoadingMore || !current.hasMore) return
    val epoch = orderEpoch
    val page = current.records.size / ORDER_PAGE_SIZE
    _orderHistoryState.update { it.copy(isLoadingMore = true, error = null) }
    viewModelScope.launch {
      venueApi
          .getMyOrders(page = page, size = ORDER_PAGE_SIZE)
          .onSuccess { result ->
            if (epoch != orderEpoch) return@onSuccess
            val merged = current.records + result.records
            _orderHistoryState.value =
                VenueOrderHistoryUiState(
                    records = merged,
                    hasMore = merged.size < result.totalElements,
                )
          }
          .onFailure { error ->
            if (epoch != orderEpoch) return@onFailure
            _orderHistoryState.update {
              it.copy(isLoadingMore = false, error = error.message ?: "预约记录加载失败")
            }
          }
    }
  }

  fun clearOrderHistory() {
    orderEpoch++
    _orderHistoryState.value = VenueOrderHistoryUiState()
  }

  private suspend fun loadSlotTemplate(siteId: Int) {
    if (templateLoadedForSite == siteId && _state.value.slotTemplate.isNotEmpty()) return
    val epoch = ++templateEpoch
    val today = currentVenueDate()
    var template: List<VenueTimeSlot> = emptyList()
    for (offset in 0..7) {
      val date = today.plus(offset, kotlinx.datetime.DateTimeUnit.DAY).toString()
      val day = venueApi.getDayInfo(siteId, date).getOrNull()
      if (epoch != templateEpoch || _state.value.selectedSiteId != siteId) return
      if (day != null && day.slots.isNotEmpty()) {
        template = day.slots
        break
      }
    }
    if (epoch != templateEpoch || _state.value.selectedSiteId != siteId) return
    if (template.isEmpty()) template = fallbackVenueTemplate()
    templateLoadedForSite = siteId
    _state.update { it.copy(slotTemplate = template) }
  }

  private suspend fun loadDayInfo() {
    val siteId = _state.value.selectedSiteId ?: return
    val date = _state.value.selectedDate
    val epoch = ++dayInfoEpoch
    _state.update { it.copy(isLoadingDayInfo = true, dayInfoMessage = null) }

    val result = venueApi.getDayInfo(siteId, date)
    if (epoch != dayInfoEpoch) return

    result
        .onSuccess { day ->
          if (epoch != dayInfoEpoch) return@onSuccess
          if (day == null) {
            _state.update {
              it.copy(
                  isLoadingDayInfo = false,
                  dayInfo = null,
                  dayInfoMessage = "该日期尚未放票，可先按开始时间配置定时抢场地",
              )
            }
          } else {
            _state.update {
              it.copy(
                  isLoadingDayInfo = false,
                  dayInfo = day,
                  buddyLimit = day.buddyNumMax.coerceIn(1, 3),
                  phone = it.phone.ifBlank { day.phone.orEmpty() },
                  selectedBuddyIds =
                      it.selectedBuddyIds
                          .filter { id -> day.buddies.any { buddy -> buddy.id == id } }
                          .toSet(),
                  dayInfoMessage = null,
              )
            }
          }
        }
        .onFailure { error ->
          _state.update { state ->
            state.copy(
                isLoadingDayInfo = false,
                dayInfoMessage = error.message ?: "场地信息加载失败",
            )
          }
        }
  }

  /** 从下拉框选择目标开始时间；已放票时校验指定场地在该时段是否仍空闲。 */
  fun selectTemplateSlot(slot: VenueTimeSlot) {
    _state.update {
      val keepFixedSpace =
          it.spaceMode == VenueSpaceMode.FIXED &&
              it.selectedSpaceId?.let { spaceId ->
                val space = it.dayInfo?.spaces?.firstOrNull { space -> space.spaceId == spaceId }
                space == null || space.slot(slot.timeId)?.isFree == true
              } == true
      it.copy(
          selectedStartBegin = slot.beginTime,
          selectedSpaceId = if (keepFixedSpace) it.selectedSpaceId else null,
          message = null,
      )
    }
  }

  fun selectCell(space: VenueSpace, slot: VenueTimeSlot) {
    _state.update {
      it.copy(
          selectedStartBegin = slot.beginTime,
          selectedSpaceId =
              if (it.spaceMode == VenueSpaceMode.FIXED) space.spaceId else it.selectedSpaceId,
          message = null,
      )
    }
  }

  fun setDurationHours(hours: Int) {
    _state.update { it.copy(durationHours = hours.coerceIn(1, 2)) }
  }

  fun setSpaceMode(mode: VenueSpaceMode) {
    _state.update {
      it.copy(
          spaceMode = mode,
          selectedSpaceId = if (mode == VenueSpaceMode.AUTO) null else it.selectedSpaceId,
      )
    }
  }

  fun setRandomSpace(enabled: Boolean) {
    _state.update { it.copy(randomSpace = enabled) }
  }

  fun setFallbackAnySpace(enabled: Boolean) {
    _state.update { it.copy(fallbackAnySpace = enabled) }
  }

  fun toggleBuddy(buddyId: Int) {
    _state.update {
      val selected =
          if (buddyId in it.selectedBuddyIds) {
            it.selectedBuddyIds - buddyId
          } else if (it.selectedBuddyIds.size >= it.buddyLimit) {
            it.selectedBuddyIds
          } else {
            it.selectedBuddyIds + buddyId
          }
      it.copy(selectedBuddyIds = selected, message = null)
    }
  }

  fun addBuddy(userUid: String) {
    val uid = userUid.trim()
    if (uid.isEmpty()) return
    viewModelScope.launch {
      venueApi
          .addBuddy(uid)
          .onSuccess {
            _state.update { it.copy(message = "同伴已添加") }
            loadBuddies()
          }
          .onFailure { error ->
            _state.update { state -> state.copy(message = error.message ?: "添加同伴失败") }
          }
    }
  }

  fun deleteBuddy(buddyId: Int) {
    viewModelScope.launch {
      venueApi
          .deleteBuddy(buddyId)
          .onSuccess {
            _state.update {
              it.copy(
                  buddies = it.buddies.filterNot { buddy -> buddy.id == buddyId },
                  selectedBuddyIds = it.selectedBuddyIds - buddyId,
                  message = "同伴已删除",
              )
            }
          }
          .onFailure { error ->
            _state.update { state -> state.copy(message = error.message ?: "删除同伴失败") }
          }
    }
  }

  fun updatePollInterval(text: String) {
    _state.update { it.copy(pollIntervalText = text) }
  }

  fun setUseSchedule(enabled: Boolean) {
    _state.update { it.copy(useSchedule = enabled) }
  }

  fun updateGrabAt(text: String) {
    _state.update { it.copy(grabAtText = text) }
  }

  fun clearMessage() {
    _state.update { it.copy(message = null) }
  }

  fun startGrab() {
    val current = _state.value
    if (current.authPhase != VenueAuthPhase.READY) {
      _state.update { it.copy(message = "请先登录运动场地系统") }
      return
    }
    val siteId = current.selectedSiteId ?: return
    val startBegin = current.selectedStartBegin
    if (startBegin.isNullOrBlank()) {
      _state.update { it.copy(message = "请先在表格中点击一个目标时段") }
      return
    }
    val pollSeconds = current.pollIntervalText.toDoubleOrNull()?.coerceIn(0.2, 10.0) ?: 0.35
    val alreadyReleased = current.dayInfo != null
    val grabAt =
        if (!alreadyReleased && current.useSchedule) {
          parseGrabTime(current.grabAtText)
              ?: run {
                _state.update { it.copy(message = "开抢时间格式应为 HH:mm 或 HH:mm:ss") }
                return
              }
        } else {
          null
        }

    val preferred =
        if (current.spaceMode == VenueSpaceMode.FIXED && current.selectedSpaceId != null) {
          mapOf(startBegin to current.selectedSpaceId)
        } else {
          emptyMap()
        }

    grabController.start(
        VenueGrabTask(
            siteId = siteId,
            date = current.selectedDate,
            startBeginTime = startBegin,
            durationHours = current.durationHours,
            spaceMode = current.spaceMode,
            preferredSpaceByBegin = preferred,
            fallbackAnySpace = current.fallbackAnySpace,
            randomSpace = current.randomSpace,
            buddyIds = current.selectedBuddyIds.toList(),
            phone = current.phone.takeIf { it.isNotBlank() },
            pollInterval = (pollSeconds * 1000).toLong().milliseconds,
            grabAt = grabAt,
        )
    )
  }

  fun stopGrab() {
    grabController.stop()
  }

  fun submitCaptcha(points: List<VenuePoint>, imageWidth: Int, imageHeight: Int) {
    grabController.submitCaptcha(VenueCaptchaAnswer(points, imageWidth, imageHeight))
  }

  fun cancelCaptcha() {
    grabController.cancelCaptcha()
  }

  fun stopGrabForLogout() {
    grabController.stop(cn.edu.ubaa.plus.api.venue.VenueGrabStopReason.LOGOUT)
  }
}

internal fun currentVenueDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

internal fun parseGrabTime(text: String): LocalTime? =
    runCatching {
          val parts = text.trim().split(":")
          when (parts.size) {
            2 -> LocalTime(parts[0].toInt(), parts[1].toInt())
            3 -> LocalTime(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            else -> null
          }
        }
        .getOrNull()

internal fun fallbackVenueTemplate(): List<VenueTimeSlot> =
    (7..21).map { hour ->
      VenueTimeSlot(
          timeId = hour,
          beginTime = "%02d:00".format(hour),
          endTime = "%02d:00".format(hour + 1),
      )
    }
