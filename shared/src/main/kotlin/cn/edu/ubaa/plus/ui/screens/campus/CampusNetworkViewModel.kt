package cn.edu.ubaa.plus.ui.screens.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.CampusNetworkApi
import cn.edu.ubaa.plus.api.feature.CampusNetworkInfoDto
import cn.edu.ubaa.plus.api.feature.CampusTrafficDto
import cn.edu.ubaa.plus.api.feature.ItPortalApi
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampusNetworkUiState(
    val isLoading: Boolean = false,
    val info: CampusNetworkInfoDto? = null,
    val traffic: CampusTrafficDto? = FeatureReadCache.get("network", "traffic"),
    val netFee: String? = null,
    val netFeeUrl: String? = null,
    val error: String? = null,
)

class CampusNetworkViewModel(
    private val networkApi: CampusNetworkApi = CampusNetworkApi(),
    private val portalApi: ItPortalApi = ItPortalApi(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(CampusNetworkUiState())
  val uiState: StateFlow<CampusNetworkUiState> = _uiState.asStateFlow()
  private var loadedOnce = false

  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    refresh()
  }

  fun refresh() {
    if (_uiState.value.isLoading) return
    loadedOnce = true
    val scope = SchoolReadCoordinator.capture()
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      val usageJob = async { networkApi.getTraffic() }
      val feeJob = async {
        SchoolReadCoordinator.read("network", "fee") { portalApi.findPersonalValue("net.netFee") }
      }
      val srunJob = async { networkApi.getSrunInfo() }
      val usageResult = usageJob.await()
      val traffic = usageResult.getOrNull()
      val feeResult = feeJob.await()
      val netFee = feeResult.getOrNull()
      val srun = srunJob.await()
      if (!scope.isCurrent()) return@launch
      val owner = cn.edu.ubaa.plus.repository.ScheduleStore.account()
      val ownInfo = srun.getOrNull()?.takeIf { it.userName == owner }
      _uiState.update {
        it.copy(
            isLoading = false,
            info = ownInfo,
            traffic = traffic ?: it.traffic,
            netFee = cn.edu.ubaa.plus.util.formatMoney(netFee?.value) ?: it.netFee,
            netFeeUrl = netFee?.url ?: it.netFeeUrl,
            error =
                usageResult.exceptionOrNull()?.message
                    ?: FeatureReadCache.readWarning("network", "traffic")
                    ?: if (feeResult.isFailure) "网费余额更新失败，保留上次数据" else null,
        )
      }
    }
  }

  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  fun resetLoadedState() {
    loadedOnce = false
    _uiState.value = CampusNetworkUiState()
  }
}
