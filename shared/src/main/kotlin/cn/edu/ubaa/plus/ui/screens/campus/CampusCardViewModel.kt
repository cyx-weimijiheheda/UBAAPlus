package cn.edu.ubaa.plus.ui.screens.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.CampusCardApi
import cn.edu.ubaa.plus.api.feature.CampusCardBalanceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CampusCardUiState(
    val isLoading: Boolean = false,
    val balance: CampusCardBalanceDto? = null,
    val error: String? = null,
)

class CampusCardViewModel(
    private val api: CampusCardApi = CampusCardApi(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(CampusCardUiState())
  val uiState: StateFlow<CampusCardUiState> = _uiState.asStateFlow()
  private var loadedOnce = false

  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    refresh()
  }

  fun refresh() {
    loadedOnce = true
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      api.getBalance()
          .onSuccess { balance ->
            _uiState.update { it.copy(isLoading = false, balance = balance, error = null) }
          }
          .onFailure { error ->
            _uiState.update { it.copy(isLoading = false, error = error.message ?: "校园卡余额加载失败") }
          }
    }
  }

  fun resetLoadedState() {
    loadedOnce = false
    _uiState.value = CampusCardUiState()
  }
}
