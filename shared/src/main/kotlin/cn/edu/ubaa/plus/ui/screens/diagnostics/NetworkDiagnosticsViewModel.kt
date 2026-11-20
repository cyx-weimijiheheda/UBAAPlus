package cn.edu.ubaa.plus.ui.screens.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.diagnostics.*
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.util.BusinessDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class NetworkDiagnosticsState(
    val rows: List<DiagnosticRow> = emptyList(),
    val running: Boolean = false,
    val activeIds: Set<String> = emptySet(),
    val completedIds: Set<String> = emptySet(),
)

internal class NetworkDiagnosticsViewModel(
    private val runner: NetworkDiagnosticRunner = NetworkDiagnosticRunner(),
    val targets: List<DiagnosticTarget> = diagnosticTargets(),
) : ViewModel() {
  private val mutable = MutableStateFlow(NetworkDiagnosticsState())
  val state = mutable.asStateFlow()
  private var job: Job? = null
  private var generation = 0

  fun initialize(mode: ConnectionMode) {
    if (mutable.value.rows.isEmpty())
        mutable.value =
            NetworkDiagnosticsState(
                rows =
                    targets.map { DiagnosticRow(it.id, it.title, it.actualMode(mode).storageKey) }
            )
  }

  fun start(mode: ConnectionMode, authenticated: Boolean, id: String? = null) {
    if (mutable.value.running) return
    initialize(mode)
    val selected = targets.filter { id == null || it.id == id }
    if (selected.isEmpty()) return
    val token = ++generation
    val scope = SchoolReadCoordinator.capture()
    val ids = selected.map { it.id }.toSet()
    mutable.update {
      it.copy(
          running = true,
          activeIds = ids,
          completedIds = emptySet(),
          rows =
              it.rows.map { row ->
                if (row.id in ids)
                    row.copy(network = ProbeResult(), business = ProbeResult(), checkedAt = null)
                else row
              },
      )
    }
    job =
        viewModelScope.launch(BusinessDispatchers.io) {
          try {
            runner.run(selected, mode, authenticated, scope::isCurrent) { row ->
              if (token == generation && scope.isCurrent())
                  mutable.update { state ->
                    state.copy(
                        rows = state.rows.map { if (it.id == row.id) row else it },
                        completedIds =
                            if (
                                row.business.status !in setOf(ProbeStatus.IDLE, ProbeStatus.RUNNING)
                            )
                                state.completedIds + row.id
                            else state.completedIds,
                    )
                  }
            }
          } finally {
            if (token == generation)
                mutable.update { it.copy(running = false, rows = it.rows.map(::stoppedRow)) }
          }
        }
  }

  fun stop() {
    generation++
    job?.cancel()
    mutable.update { it.copy(running = false, rows = it.rows.map(::stoppedRow)) }
  }

  private fun stoppedRow(row: DiagnosticRow) =
      row.copy(
          network =
              if (row.network.status == ProbeStatus.RUNNING)
                  ProbeResult(ProbeStatus.CANCELED, ProbeReason.STOPPED)
              else row.network,
          business =
              if (row.business.status == ProbeStatus.RUNNING)
                  ProbeResult(ProbeStatus.CANCELED, ProbeReason.STOPPED)
              else row.business,
      )
}
