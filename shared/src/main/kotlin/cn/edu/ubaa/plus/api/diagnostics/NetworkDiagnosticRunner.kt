package cn.edu.ubaa.plus.api.diagnostics

import cn.edu.ubaa.plus.api.ConnectionMode
import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.recovery.FreshSchoolRead
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal interface NetworkDiagnosticBackend {
  suspend fun transport(target: DiagnosticTarget, mode: ConnectionMode): Int

  suspend fun read(target: DiagnosticTarget)
}

internal class DiagnosticSkip(val reason: ProbeReason) : Exception()

internal class NetworkDiagnosticRunner(
    private val backend: NetworkDiagnosticBackend = SchoolNetworkDiagnosticBackend(),
    private val now: () -> Long = System::currentTimeMillis,
    private val nanoTime: () -> Long = System::nanoTime,
    private val networkTimeoutMs: Long = 8_000,
    private val businessTimeoutMs: Long = 40_000,
) {
  suspend fun run(
      targets: List<DiagnosticTarget>,
      mode: ConnectionMode,
      authenticated: Boolean,
      isCurrent: () -> Boolean,
      onUpdate: (DiagnosticRow) -> Unit,
  ): Unit = coroutineScope {
    val permits = Semaphore(2)
    fun checkScope() {
      if (!isCurrent()) throw CancellationException("诊断作用域已改变")
    }
    targets
        .map { target ->
          async {
            permits.withPermit {
              checkScope()
              var row =
                  DiagnosticRow(
                      target.id,
                      target.title,
                      target.actualMode(mode).storageKey,
                      network = ProbeResult(ProbeStatus.RUNNING),
                      checkedAt = now(),
                  )
              onUpdate(row)
              val network =
                  measure(networkTimeoutMs) {
                    val status = backend.transport(target, mode)
                    ProbeResult(
                        status =
                            when (status) {
                              in 200..299 -> ProbeStatus.SUCCESS
                              in 300..399 -> ProbeStatus.RESPONSE
                              else -> ProbeStatus.RESPONSE
                            },
                        reason =
                            when (status) {
                              in 200..299 -> ProbeReason.HTTP_RESPONSE
                              in 300..399 -> ProbeReason.LOGIN_REDIRECT
                              else -> ProbeReason.HTTP_ERROR
                            },
                        httpStatus = status,
                    )
                  }
              checkScope()
              row =
                  row.copy(
                      network = network,
                      business =
                          if (authenticated || target.id == "app_service")
                              ProbeResult(ProbeStatus.RUNNING)
                          else ProbeResult(ProbeStatus.SKIPPED, ProbeReason.LOGIN_REQUIRED),
                  )
              onUpdate(row)
              if (row.business.status == ProbeStatus.RUNNING) {
                row =
                    row.copy(
                        business =
                            measure(businessTimeoutMs) {
                              withContext(FreshSchoolRead) { backend.read(target) }
                              ProbeResult(ProbeStatus.SUCCESS, ProbeReason.OK)
                            }
                    )
              }
              checkScope()
              onUpdate(row)
            }
          }
        }
        .awaitAll()
    Unit
  }

  private suspend fun measure(timeout: Long, block: suspend () -> ProbeResult): ProbeResult {
    val start = nanoTime()
    val result =
        try {
          withTimeoutOrNull(timeout) { block() }
              ?: ProbeResult(ProbeStatus.TIMEOUT, ProbeReason.TIMEOUT)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          diagnosticFailure(e)
        }
    return result.copy(elapsedMs = ((nanoTime() - start) / 1_000_000).coerceAtLeast(0))
  }
}

internal fun diagnosticFailure(error: Throwable): ProbeResult {
  if (error is DiagnosticSkip) return ProbeResult(ProbeStatus.SKIPPED, error.reason)
  val chain =
      generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(8).toList()
  val api = chain.filterIsInstance<ApiCallException>().firstOrNull()
  return when {
    api?.code == "unsupported_portal" -> ProbeResult(ProbeStatus.SKIPPED, ProbeReason.UNSUPPORTED)
    api?.code in
        setOf("unauthenticated", "invalid_token", "libbook_auth_failed", "offline_cache_missing") ||
        api?.status?.value == 401 -> ProbeResult(ProbeStatus.AUTH_REQUIRED, ProbeReason.AUTH)
    api?.status?.value == 403 ->
        ProbeResult(ProbeStatus.FAILED, ProbeReason.PERMISSION, httpStatus = 403)
    chain.any {
      it is io.ktor.client.plugins.HttpRequestTimeoutException ||
          it is io.ktor.client.network.sockets.ConnectTimeoutException ||
          it is java.net.SocketTimeoutException
    } || api?.code in setOf("judge_timeout", "libbook_timeout") || api?.message == "请求超时，请稍后重试" ->
        ProbeResult(ProbeStatus.TIMEOUT, ProbeReason.TIMEOUT)
    chain.any { it is UnknownHostException } -> ProbeResult(ProbeStatus.FAILED, ProbeReason.DNS)
    chain.any { it is SSLException } -> ProbeResult(ProbeStatus.FAILED, ProbeReason.TLS)
    chain.any { it is kotlinx.serialization.SerializationException } ||
        api?.code in setOf("finance_format", "classroom_response_error") ->
        ProbeResult(ProbeStatus.FAILED, ProbeReason.PARSE)
    api?.status?.value?.let { it >= 500 } == true ->
        ProbeResult(ProbeStatus.FAILED, ProbeReason.UPSTREAM, httpStatus = api.status.value)
    chain.any { it is IOException } -> ProbeResult(ProbeStatus.FAILED, ProbeReason.NETWORK)
    else -> ProbeResult(ProbeStatus.FAILED, ProbeReason.UNKNOWN)
  }
}

@Serializable
internal data class NetworkDiagnosticReport(
    val schemaVersion: Int = 1,
    val appVersion: String,
    val exportedAt: Long,
    val rows: List<DiagnosticRow>,
    val issues: List<cn.edu.ubaa.plus.api.storage.LocalDiagnostics.Issue>,
)

internal fun exportNetworkDiagnostics(
    rows: List<DiagnosticRow>,
    now: Long = System.currentTimeMillis(),
): String =
    Json { prettyPrint = true }
        .encodeToString(
            NetworkDiagnosticReport(
                appVersion = cn.edu.ubaa.plus.BuildKonfig.VERSION,
                exportedAt = now,
                rows = rows,
                issues = cn.edu.ubaa.plus.api.storage.LocalDiagnostics.issues(),
            )
        )
