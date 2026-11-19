package cn.edu.ubaa.plus.ui.screens.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class FinanceUiState(
    val snapshot: FinanceIncomes? = FeatureReadCache.get("finance", "incomes"),
    val loading: Boolean = false,
    val error: String? = null,
)

internal class FinanceViewModel(private val api: FinanceApi = FinanceApi()) : ViewModel() {
  private val mutable = MutableStateFlow(FinanceUiState())
  val state = mutable.asStateFlow()
  private var loaded = false

  fun ensureLoaded() {
    if (!loaded) refresh()
  }

  fun refresh() {
    if (mutable.value.loading) return
    loaded = true
    val scope = SchoolReadCoordinator.capture()
    mutable.value = mutable.value.copy(loading = true, error = null)
    viewModelScope.launch {
      val result = api.incomes()
      if (!scope.isCurrent()) return@launch
      mutable.value =
          mutable.value.copy(
              loading = false,
              snapshot = result.getOrNull() ?: mutable.value.snapshot,
              error =
                  result.exceptionOrNull()?.message
                      ?: FeatureReadCache.readWarning("finance", "incomes"),
          )
    }
  }
}
