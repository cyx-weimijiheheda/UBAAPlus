package cn.edu.ubaa.plus.api.diagnostics

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.recovery.FreshSchoolRead
import cn.edu.ubaa.plus.testing.TestSettings
import cn.edu.ubaa.plus.ui.screens.menu.regularFeatureItems
import io.ktor.http.HttpStatusCode
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkDiagnosticsTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
  }

  private val target = DiagnosticTarget("schedule", "课表查询", "https://school.example")

  @Test
  fun `目录覆盖所有功能入口并保持直接访问策略`() {
    val all = diagnosticTargets("https://service.example")
    assertEquals(
        regularFeatureItems().map { it.id }.toSet() + setOf("auth", "app_service"),
        all.map { it.id }.toSet(),
    )
    assertEquals(all.size, all.map { it.id }.distinct().size)
    assertEquals(
        ConnectionMode.DIRECT,
        all.single { it.id == "cgyy" }.actualMode(ConnectionMode.WEBVPN),
    )
    assertEquals(
        ConnectionMode.DIRECT,
        all.single { it.id == "libservice" }.actualMode(ConnectionMode.WEBVPN),
    )
    assertEquals(
        ConnectionMode.WEBVPN,
        all.single { it.id == "schedule" }.actualMode(ConnectionMode.WEBVPN),
    )
  }

  @Test
  fun `服务响应不等同于业务登录且诊断使用新鲜读取上下文`() = runTest {
    var fresh = false
    val backend =
        object : NetworkDiagnosticBackend {
          override suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode) = 302

          override suspend fun read(target: DiagnosticTarget) {
            fresh = currentCoroutineContext()[FreshSchoolRead.Key] != null
            throw ApiCallException("secret-token", status = HttpStatusCode.Unauthorized)
          }
        }
    val rows = mutableListOf<DiagnosticRow>()
    NetworkDiagnosticRunner(backend).run(listOf(target), ConnectionMode.WEBVPN, true, { true }) {
      rows += it
    }
    assertTrue(fresh)
    assertEquals(ProbeStatus.RESPONSE, rows.last().network.status)
    assertEquals(302, rows.last().network.httpStatus)
    assertEquals(ProbeStatus.AUTH_REQUIRED, rows.last().business.status)
    val report = exportNetworkDiagnostics(listOf(rows.last()))
    assertFalse(report.contains("secret-token"))
    assertFalse(report.contains("school.example"))
  }

  @Test
  fun `没有登录仍检测服务且不调用学校业务`() = runTest {
    var reads = 0
    val backend =
        object : NetworkDiagnosticBackend {
          override suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode) = 200

          override suspend fun read(target: DiagnosticTarget) {
            reads++
          }
        }
    var last: DiagnosticRow? = null
    NetworkDiagnosticRunner(backend).run(listOf(target), ConnectionMode.DIRECT, false, { true }) {
      last = it
    }
    assertEquals(0, reads)
    assertEquals(ProbeStatus.SUCCESS, last!!.network.status)
    assertEquals(ProbeReason.LOGIN_REQUIRED, last!!.business.reason)
  }

  @Test
  fun `超时区分于主动取消且可以停止后续检测`() = runTest {
    val backend =
        object : NetworkDiagnosticBackend {
          override suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode): Int {
            delay(1000)
            return 200
          }

          override suspend fun read(target: DiagnosticTarget) {
            delay(1000)
          }
        }
    var last: DiagnosticRow? = null
    NetworkDiagnosticRunner(backend, networkTimeoutMs = 10, businessTimeoutMs = 10).run(
        listOf(target),
        ConnectionMode.DIRECT,
        true,
        { true },
    ) {
      last = it
    }
    assertEquals(ProbeStatus.TIMEOUT, last!!.network.status)
    assertEquals(ProbeStatus.TIMEOUT, last!!.business.status)
    val rows = mutableListOf<DiagnosticRow>()
    val job = launch {
      NetworkDiagnosticRunner(backend).run(listOf(target), ConnectionMode.DIRECT, true, { true }) {
        rows += it
      }
    }
    runCurrent()
    job.cancelAndJoin()
    advanceTimeBy(2000)
    runCurrent()
    assertEquals(1, rows.size)
  }

  @Test
  fun `账户路线改变时不发布旧结果`() = runTest {
    var current = true
    val backend =
        object : NetworkDiagnosticBackend {
          override suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode): Int {
            current = false
            return 200
          }

          override suspend fun read(target: DiagnosticTarget) {
            fail("must not read changed account")
          }
        }
    val rows = mutableListOf<DiagnosticRow>()
    assertFailsWith<CancellationException> {
      NetworkDiagnosticRunner(backend).run(
          listOf(target),
          ConnectionMode.DIRECT,
          true,
          { current },
      ) {
        rows += it
      }
    }
    assertEquals(1, rows.size)
  }

  @Test
  fun `错误分类不导出异常原文且区分权限网络与不适用`() {
    assertEquals(
        ProbeReason.TLS,
        diagnosticFailure(javax.net.ssl.SSLHandshakeException("private")).reason,
    )
    assertEquals(
        ProbeReason.DNS,
        diagnosticFailure(java.net.UnknownHostException("private")).reason,
    )
    assertEquals(
        ProbeReason.UNSUPPORTED,
        diagnosticFailure(ApiCallException("private", code = "unsupported_portal")).reason,
    )
    assertEquals(
        ProbeReason.PERMISSION,
        diagnosticFailure(ApiCallException("private", status = HttpStatusCode.Forbidden)).reason,
    )
  }
}
