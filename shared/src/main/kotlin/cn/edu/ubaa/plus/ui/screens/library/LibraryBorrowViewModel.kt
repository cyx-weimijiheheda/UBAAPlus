package cn.edu.ubaa.plus.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.LibraryBorrowApi
import cn.edu.ubaa.plus.api.feature.LibraryLoanDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryBorrowTab(val label: String) {
  CURRENT("当前借阅"),
  HISTORY("借阅历史"),
}

data class LibraryBorrowUiState(
    val isLoading: Boolean = false,
    val selectedTab: LibraryBorrowTab = LibraryBorrowTab.CURRENT,
    val currentLoans: List<LibraryLoanDto> = emptyList(),
    val history: List<LibraryLoanDto> = emptyList(),
    val error: String? = null,
)

class LibraryBorrowViewModel(
    private val api: LibraryBorrowApi = LibraryBorrowApi(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(LibraryBorrowUiState())
  val uiState: StateFlow<LibraryBorrowUiState> = _uiState.asStateFlow()
  private var loadedOnce = false

  fun ensureLoaded(forceRefresh: Boolean = false) {
    if (!forceRefresh && loadedOnce) return
    refresh()
  }

  fun selectTab(tab: LibraryBorrowTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  fun refresh() {
    loadedOnce = true
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, error = null) }
      val current = api.getCurrentLoans()
      val history = api.getHistory()
      _uiState.update {
        it.copy(
            isLoading = false,
            currentLoans = current.getOrDefault(it.currentLoans),
            history = history.getOrDefault(it.history),
            error =
                (current.exceptionOrNull() ?: history.exceptionOrNull())?.message?.takeIf { message
                  ->
                  message.isNotBlank()
                },
        )
      }
    }
  }

  fun resetLoadedState() {
    loadedOnce = false
    _uiState.value = LibraryBorrowUiState()
  }
}
