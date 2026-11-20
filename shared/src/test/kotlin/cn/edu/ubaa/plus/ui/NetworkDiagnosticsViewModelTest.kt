package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.diagnostics.*
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.testing.TestSettings
import cn.edu.ubaa.plus.ui.screens.diagnostics.NetworkDiagnosticsViewModel
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkDiagnosticsViewModelTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
  }

  @AfterTest
  fun cleanup() {
    Dispatchers.resetMain()
    cn.edu.ubaa.plus.util.BusinessDispatchers.io = Dispatchers.Unconfined
  }

  @Test
  fun `停止检测后迟到的网络结果不会重新覆盖已停止状态`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    cn.edu.ubaa.plus.util.BusinessDispatchers.io = StandardTestDispatcher(testScheduler)
    val response = CompletableDeferred<Int>()
    var reads = 0
    val backend =
        object : NetworkDiagnosticBackend {
          override suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode) =
              withContext(NonCancellable) { response.await() }

          override suspend fun read(target: DiagnosticTarget) {
            reads++
          }
        }
    val model =
        NetworkDiagnosticsViewModel(
            NetworkDiagnosticRunner(backend),
            listOf(DiagnosticTarget("auth", "认证", "https://example.test")),
        )
    model.start(ConnectionMode.DIRECT, true)
    runCurrent()
    assertTrue(model.state.value.running)
    model.stop()
    response.complete(200)
    advanceUntilIdle()
    assertFalse(model.state.value.running)
    assertEquals(ProbeStatus.CANCELED, model.state.value.rows.single().network.status)
    assertEquals(0, reads)
  }

  @Test
  fun `身份代次变化时取消检测不读取或发布旧账户内容`() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    cn.edu.ubaa.plus.util.BusinessDispatchers.io = StandardTestDispatcher(testScheduler)
    var reads = 0
    val backend =
        object : NetworkDiagnosticBackend {
          override suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode): Int {
            delay(10)
            SchoolReadCoordinator.invalidate()
            return 200
          }

          override suspend fun read(target: DiagnosticTarget) {
            reads++
          }
        }
    val model =
        NetworkDiagnosticsViewModel(
            NetworkDiagnosticRunner(backend),
            listOf(DiagnosticTarget("auth", "认证", "https://example.test")),
        )
    model.start(ConnectionMode.DIRECT, true)
    advanceUntilIdle()
    assertFalse(model.state.value.running)
    assertEquals(0, reads)
    assertEquals(ProbeStatus.CANCELED, model.state.value.rows.single().network.status)
  }
}
