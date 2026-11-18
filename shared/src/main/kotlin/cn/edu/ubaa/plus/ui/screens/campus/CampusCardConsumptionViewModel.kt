package cn.edu.ubaa.plus.ui.screens.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.feature.*
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal data class ConsumptionUiState(
    val start: LocalDate,
    val end: LocalDate,
    val snapshot: CampusCardConsumptionDto? =
        FeatureReadCache.get("card", consumptionCacheKey(start, end)),
    val loading: Boolean = false,
    val error: String? = null,
    val requested: Boolean = false,
)

internal class CampusCardConsumptionViewModel(
    private val api: CampusCardConsumptionApi = CampusCardConsumptionApi(),
    private val today: () -> LocalDate = ::campusToday,
) : ViewModel() {
  private val initialDate = today()
  private val mutable = MutableStateFlow(ConsumptionUiState(initialDate, initialDate))
  val state = mutable.asStateFlow()
  private var job: Job? = null
  private var generation = 0

  fun selectStart(date: LocalDate) {
    select(date, mutable.value.end)
  }

  fun selectEnd(date: LocalDate) {
    select(mutable.value.start, date)
  }

  private fun select(start: LocalDate, end: LocalDate) {
    if (
        !validConsumptionRange(start, end, today()) ||
            (start == mutable.value.start && end == mutable.value.end)
    )
        return
    job?.cancel()
    generation++
    mutable.value = ConsumptionUiState(start, end)
  }

  fun ensureLoaded() {
    if (!mutable.value.requested) query()
  }

  fun query() {
    val selected = mutable.value
    if (selected.loading || !validConsumptionRange(selected.start, selected.end, today())) return
    val version = ++generation
    val scope = SchoolReadCoordinator.capture()
    mutable.value = selected.copy(loading = true, error = null, requested = true)
    job =
        viewModelScope.launch {
          val result = api.query(selected.start, selected.end)
          if (version != generation || !scope.isCurrent()) return@launch
          mutable.value =
              mutable.value.copy(
                  loading = false,
                  snapshot = result.getOrNull() ?: selected.snapshot,
                  error =
                      result.exceptionOrNull()?.message
                          ?: FeatureReadCache.readWarning(
                              "card",
                              consumptionCacheKey(selected.start, selected.end),
                          ),
              )
        }
  }
}
