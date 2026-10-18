package cn.edu.ubaa.plus.ui.screens.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.CampusNetworkApi
import cn.edu.ubaa.plus.api.feature.CampusNetworkInfoDto
import cn.edu.ubaa.plus.api.feature.CampusTrafficDto
import cn.edu.ubaa.plus.api.feature.ItPortalApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampusNetworkUiState(
    val isLoading: Boolean = false,
    val info: CampusNetworkInfoDto? = null,
    val traffic: CampusTrafficDto? = null,
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
    loadedOnce = true
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      val srun = networkApi.getSrunInfo()
      val traffic = if (srun.getOrNull() == null) networkApi.getTraffic().getOrNull() else null
      val netFee = runCatching { portalApi.findPersonalValue("net.netFee").getOrNull() }.getOrNull()
      val hasData = srun.getOrNull() != null || traffic != null || netFee != null
      _uiState.update {
        it.copy(
            isLoading = false,
            info = srun.getOrNull(),
            traffic = traffic,
            netFee = netFee?.value?.takeIf { value -> value.isNotBlank() },
            netFeeUrl = netFee?.url,
            error =
                if (hasData) null
                else {
                  srun.exceptionOrNull()?.message ?: "校园网信息加载失败，请连接校园网后重试"
                },
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
