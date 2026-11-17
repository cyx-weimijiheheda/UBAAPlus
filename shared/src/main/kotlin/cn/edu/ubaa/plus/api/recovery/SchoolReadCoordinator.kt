package cn.edu.ubaa.plus.api.recovery

import cn.edu.ubaa.plus.api.ConnectionRuntime
import cn.edu.ubaa.plus.api.auth.*
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.repository.ScheduleStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** 同查询合并；只读请求最多恢复一次，学校写入不会进入这个入口。 */
internal object SchoolReadCoordinator {
  @Volatile
  var onlineAllowed = true
    private set

  private var epoch = 0L
  private var recoveryRevision = 0L
  private var lastRecoveryScope: Scope? = null
  private var lastRecoveryAt = 0L
  private var lastRecoverySucceeded = false
  private val recoveryMutex = Mutex()
  private val lock = Any()
  private val pending = mutableMapOf<String, CompletableDeferred<Result<Any?>>>()
  internal var restoreSession: suspend () -> Boolean = ::restoreCurrentSession

  data class Scope(
      val owner: String?,
      val mode: cn.edu.ubaa.plus.api.ConnectionMode?,
      val epoch: Long,
  ) {
    fun isCurrent() =
        owner == ScheduleStore.account() &&
            mode == ConnectionRuntime.currentMode() &&
            epoch == SchoolReadCoordinator.epoch
  }

  fun capture() = Scope(ScheduleStore.account(), ConnectionRuntime.currentMode(), epoch)

  fun allowOnline(allowed: Boolean) {
    synchronized(lock) {
      if (onlineAllowed != allowed) {
        onlineAllowed = allowed
        epoch++
      }
    }
  }

  fun invalidate() {
    synchronized(lock) {
      epoch++
      pending.values.forEach { it.cancel() }
      pending.clear()
    }
  }

  inline suspend fun <reified T> read(
      feature: String,
      query: String,
      cache: Boolean = true,
      crossinline block: suspend () -> Result<T>,
  ): Result<T> = read(feature, query, serializer<T>(), cache) { block() }

  @Suppress("UNCHECKED_CAST")
  suspend fun <T> read(
      feature: String,
      query: String,
      serializer: KSerializer<T>,
      cache: Boolean,
      block: suspend () -> Result<T>,
  ): Result<T> {
    val scope = capture()
    if (!onlineAllowed)
        return FeatureReadCache.get(feature, query, serializer)?.let { Result.success(it) }
            ?: Result.failure(ApiCallException("登录后可更新此内容", code = "offline_cache_missing"))
    val key = "${scope.owner}:${scope.mode}:${scope.epoch}:$feature:$query"
    var owns = false
    val deferred =
        synchronized(lock) {
          pending.getOrPut(key) {
            owns = true
            CompletableDeferred()
          }
        }
    if (!owns) return deferred.await() as Result<T>
    try {
      val revision = recoveryRevision
      var result = block()
      if (isAuthenticationFailure(result.exceptionOrNull()) && scope.isCurrent()) {
        val recovered =
            recoveryMutex.withLock {
              if (!scope.isCurrent()) false
              else if (recoveryRevision != revision) true
              else if (
                  lastRecoveryScope == scope &&
                      kotlin.time.Clock.System.now().toEpochMilliseconds() - lastRecoveryAt in
                          0..30_000
              )
                  lastRecoverySucceeded
              else {
                lastRecoveryScope = scope
                lastRecoveryAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
                restoreSession().also {
                  lastRecoverySucceeded = it
                  if (it) recoveryRevision++
                }
              }
            }
        if (recovered && scope.isCurrent()) result = block()
      }
      if (!scope.isCurrent()) throw CancellationException("账号或连接方式已变化")
      result.exceptionOrNull()?.let {
        if (it is CancellationException) throw it
        LocalDiagnostics.record(feature, "read", it)
        FeatureReadCache.attempted(feature, query)
      }
      if (result.isSuccess && cache)
          FeatureReadCache.save(scope.owner, feature, query, serializer, result.getOrThrow())
      if (result.isFailure && cache) {
        FeatureReadCache.get(feature, query, serializer)?.let { result = Result.success(it) }
      }
      deferred.complete(result as Result<Any?>)
      return result
    } catch (e: Throwable) {
      deferred.completeExceptionally(e)
      throw e
    } finally {
      synchronized(lock) { if (pending[key] === deferred) pending.remove(key) }
    }
  }

  private suspend fun restoreCurrentSession(): Boolean {
    val owner = ScheduleStore.account() ?: return false
    val service = AuthService()
    val status = service.getAuthStatus().getOrNull()
    if (status != null) return status.user.schoolid == owner
    if (!CredentialStore.isAutoLogin() || CredentialStore.getUsername() != owner) return false
    val password = CredentialStore.getPassword()?.takeIf { it.isNotBlank() } ?: return false
    return service.login(owner, password).getOrNull()?.user?.schoolid == owner
  }
}

internal fun isAuthenticationFailure(error: Throwable?): Boolean {
  val e = error as? ApiCallException ?: return false
  return e.status?.value == 401 ||
      e.code in
          setOf(
              "unauthenticated",
              "unauthorized",
              "invalid_token",
              "invalid_refresh_token",
              "spoc_auth_failed",
              "judge_auth_failed",
              "libbook_auth_failed",
              "network_auth",
              "card_unauthenticated",
          )
}
