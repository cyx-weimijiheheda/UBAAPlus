package cn.edu.ubaa.plus.ui.screens.dorm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.DormElectricityApi
import cn.edu.ubaa.plus.api.storage.AppPreferencesStore
import cn.edu.ubaa.plus.model.dto.DormMeterInfoDto
import cn.edu.ubaa.plus.model.dto.DormRoomDto
import cn.edu.ubaa.plus.model.dto.DormTopUpDto
import kotlin.math.floor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DormElectricityUiState(
    val roomsLoading: Boolean = false,
    val rooms: List<DormRoomDto> = emptyList(),
    val roomsError: String? = null,
    val selectedCampus: String = "",
    val selectedBuilding: String = "",
    val selectedRoom: String = "",
    val selectedMeterId: Int? = null,
    val isQuerying: Boolean = false,
    val meterInfo: DormMeterInfoDto? = null,
    val topUps: List<DormTopUpDto> = emptyList(),
    val queryError: String? = null,
    val rechargePowerText: String = "",
    val isRecharging: Boolean = false,
    val rechargeError: String? = null,
    val payUrl: String? = null,
) {
  val campusOptions: List<String>
    get() = rooms.map { it.campus }.filter { it.isNotBlank() }.distinct().sorted()

  val buildingOptions: List<String>
    get() =
        rooms
            .filter { it.campus == selectedCampus }
            .map { it.building }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

  val roomOptions: List<String>
    get() =
        rooms
            .filter { it.campus == selectedCampus && it.building == selectedBuilding }
            .map { it.room }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))

  val meterOptions: List<DormRoomDto>
    get() =
        rooms.filter {
          it.campus == selectedCampus && it.building == selectedBuilding && it.room == selectedRoom
        }

  val selectedMeter: DormRoomDto?
    get() = meterOptions.firstOrNull { it.id == selectedMeterId }

  val selectedMeterLabel: String
    get() = selectedMeter?.let { "${it.meterNo} · ${it.name.ifBlank { it.address }}" }.orEmpty()

  val estimatedAmount: Double?
    get() {
      val info = meterInfo ?: return null
      val power = rechargePowerText.toDoubleOrNull() ?: return null
      if (power <= 0) return null
      val ct = info.ct.coerceAtLeast(1)
      val writePower = floor(power / ct).toInt()
      if (writePower <= 0) return null
      return writePower * ct * info.price
    }

  val writePower: Int?
    get() {
      val info = meterInfo ?: return null
      val power = rechargePowerText.toDoubleOrNull() ?: return null
      return floor(power / info.ct.coerceAtLeast(1)).toInt().takeIf { it > 0 }
    }

  val dispatchedPower: Int?
    get() = writePower?.let { it * (meterInfo?.ct ?: 1) }

  val canRecharge: Boolean
    get() = meterInfo != null && writePower != null && !isRecharging
}

class DormElectricityViewModel(
    private val api: DormElectricityApi = DormElectricityApi(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(DormElectricityUiState())
  val uiState: StateFlow<DormElectricityUiState> = _uiState.asStateFlow()
  private var loadedOnce = false
  private var queryEpoch = 0

  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    loadRooms()
  }

  /** 下拉刷新：重新加载宿舍列表，已有查询结果时顺带重查。 */
  fun refresh() {
    loadRooms()
    if (_uiState.value.selectedRoom.isNotBlank()) {
      query()
    }
  }

  fun loadRooms() {
    loadedOnce = true
    viewModelScope.launch {
      _uiState.update { it.copy(roomsLoading = true, roomsError = null) }
      api.getRooms()
          .onSuccess { rooms ->
            val prefs = AppPreferencesStore.state.value
            val campus =
                prefs.dormCampus.takeIf { saved -> rooms.any { it.campus == saved } }
                    ?: rooms.firstOrNull()?.campus.orEmpty()
            val building =
                prefs.dormBuilding.takeIf { saved ->
                  rooms.any { it.campus == campus && it.building == saved }
                } ?: rooms.firstOrNull { it.campus == campus }?.building.orEmpty()
            val room =
                prefs.dormRoom.takeIf { saved ->
                  rooms.any { it.campus == campus && it.building == building && it.room == saved }
                }
                    ?: rooms
                        .firstOrNull { it.campus == campus && it.building == building }
                        ?.room
                        .orEmpty()
            val meters =
                rooms.filter { it.campus == campus && it.building == building && it.room == room }
            _uiState.update {
              it.copy(
                  roomsLoading = false,
                  rooms = rooms,
                  roomsError = null,
                  selectedCampus = campus,
                  selectedBuilding = building,
                  selectedRoom = room,
                  selectedMeterId = meters.firstOrNull()?.id,
              )
            }
            query()
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(roomsLoading = false, roomsError = error.message ?: "宿舍电表列表加载失败")
            }
          }
    }
  }

  fun selectCampus(campus: String) {
    val buildings =
        _uiState.value.rooms.filter { it.campus == campus }.map { it.building }.distinct()
    val building = buildings.firstOrNull().orEmpty()
    val rooms = _uiState.value.rooms.filter { it.campus == campus && it.building == building }
    val room = rooms.firstOrNull()?.room.orEmpty()
    val meters = rooms.filter { it.room == room }
    _uiState.update {
      it.copy(
          selectedCampus = campus,
          selectedBuilding = building,
          selectedRoom = room,
          selectedMeterId = meters.firstOrNull()?.id,
          meterInfo = null,
          topUps = emptyList(),
          queryError = null,
      )
    }
    query()
  }

  fun selectBuilding(building: String) {
    val state = _uiState.value
    val rooms = state.rooms.filter { it.campus == state.selectedCampus && it.building == building }
    val room = rooms.firstOrNull()?.room.orEmpty()
    val meters = rooms.filter { it.room == room }
    _uiState.update {
      it.copy(
          selectedBuilding = building,
          selectedRoom = room,
          selectedMeterId = meters.firstOrNull()?.id,
          meterInfo = null,
          topUps = emptyList(),
          queryError = null,
      )
    }
    query()
  }

  fun selectRoom(room: String) {
    val state = _uiState.value
    val meters =
        state.rooms.filter {
          it.campus == state.selectedCampus &&
              it.building == state.selectedBuilding &&
              it.room == room
        }
    _uiState.update {
      it.copy(
          selectedRoom = room,
          selectedMeterId = meters.firstOrNull()?.id,
          meterInfo = null,
          topUps = emptyList(),
          queryError = null,
      )
    }
    query()
  }

  fun selectMeter(id: Int) {
    if (_uiState.value.selectedMeterId == id) return
    _uiState.update {
      it.copy(selectedMeterId = id, meterInfo = null, topUps = emptyList(), queryError = null)
    }
    query()
  }

  fun query() {
    val meterId = _uiState.value.selectedMeterId ?: return
    val epoch = ++queryEpoch
    viewModelScope.launch {
      _uiState.update { it.copy(isQuerying = true, queryError = null, rechargeError = null) }
      val infoResult = api.getMeterInfo(meterId)
      if (epoch != queryEpoch) return@launch
      infoResult
          .onSuccess { info ->
            val topUps = api.getTopUps(meterId).getOrDefault(emptyList())
            if (epoch != queryEpoch) return@launch
            _uiState.update {
              it.copy(
                  isQuerying = false,
                  meterInfo = info,
                  topUps = topUps,
                  queryError = null,
              )
            }
          }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  isQuerying = false,
                  meterInfo = null,
                  topUps = emptyList(),
                  queryError = error.message ?: "电表信息加载失败",
              )
            }
          }
    }
  }

  fun updateRechargePower(text: String) {
    _uiState.update { it.copy(rechargePowerText = text.filter { c -> c.isDigit() || c == '.' }) }
  }

  fun recharge() {
    val state = _uiState.value
    val meter = state.meterInfo ?: return
    val writePower = state.writePower ?: return
    if (state.isRecharging) return
    viewModelScope.launch {
      _uiState.update { it.copy(isRecharging = true, rechargeError = null) }
      api.recharge(meter.id, writePower)
          .onSuccess { url -> _uiState.update { it.copy(isRecharging = false, payUrl = url) } }
          .onFailure { error ->
            _uiState.update {
              it.copy(
                  isRecharging = false,
                  rechargeError = error.message ?: "购电请求失败",
              )
            }
          }
    }
  }

  fun consumePayUrl(): String? {
    val url = _uiState.value.payUrl ?: return null
    _uiState.update { it.copy(payUrl = null) }
    return url
  }

  fun clearRechargeError() {
    _uiState.update { it.copy(rechargeError = null) }
  }

  fun resetLoadedState() {
    loadedOnce = false
    queryEpoch++
    _uiState.value = DormElectricityUiState()
  }
}
