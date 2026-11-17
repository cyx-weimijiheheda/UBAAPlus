package cn.edu.ubaa.plus.api.recovery

import cn.edu.ubaa.plus.api.auth.ApiCallException
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.storage.FeatureReadCache
import cn.edu.ubaa.plus.api.storage.LocalDiagnostics
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** 学校写入只发送一次；丢失回执后仅只读核对，并保留跨重启的防重提交标记。 */
internal object SchoolWriteCoordinator {
  private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

  private fun pendingKey(owner: String?, feature: String, target: String): String? {
    if (owner == null) return null
    val digest =
        MessageDigest.getInstance("SHA-256")
            .digest("$owner:$feature:$target".toByteArray())
            .joinToString("") { "%02x".format(it) }
    return "write_pending:$digest"
  }

  private fun unknown(cause: Throwable? = null) =
      ApiCallException("操作结果尚未确认，请先查询学校记录，勿重复提交", code = "outcome_unknown", cause = cause)

  suspend fun <T> perform(
      feature: String,
      target: String,
      accepted: (T) -> Boolean = { true },
      submit: suspend () -> Result<T>,
      verify: suspend () -> T?,
  ): Result<T> {
    if (!SchoolReadCoordinator.onlineAllowed)
        return Result.failure(ApiCallException("请登录后再操作", code = "unauthenticated"))
    val scope = SchoolReadCoordinator.capture()
    val authenticatedOwner = runCatching { LocalAuthSessionStore.get()?.user?.schoolid }.getOrNull()
    if (scope.owner != null && authenticatedOwner != null && scope.owner != authenticatedOwner)
        return Result.failure(ApiCallException("账号状态已变化，请重新登录", code = "account_changed"))
    val owner =
        scope.owner ?: runCatching { LocalAuthSessionStore.get()?.user?.schoolid }.getOrNull()
    val key = pendingKey(owner, feature, target)
    val lockKey = key ?: "$feature:$target:${scope.mode}"
    val mutex = locks.getOrPut(lockKey) { Mutex() }
    if (!mutex.tryLock())
        return Result.failure(ApiCallException("操作处理中，请勿重复提交", code = "operation_in_progress"))
    try {
      return run execute@{
        if (!scope.isCurrent()) throw CancellationException("账号或连接方式已变化")
        suspend fun checked(): T? =
            try {
              val value = withContext(FreshSchoolRead) { verify() }
              if (!scope.isCurrent()) throw CancellationException("账号或连接方式已变化")
              value
            } catch (e: CancellationException) {
              throw e
            } catch (_: Exception) {
              null
            }
        val pending =
            key?.let {
              runCatching { FeatureReadCache.settings.getBoolean(it, false) }.getOrDefault(true)
            } ?: false
        if (pending) {
          val confirmed = checked()
          if (confirmed != null) {
            key?.let { FeatureReadCache.settings.remove(it) }
            return@execute Result.success(confirmed)
          }
          return@execute Result.failure(unknown())
        }
        if (key != null) {
          val saved = runCatching { FeatureReadCache.settings.putBoolean(key, true) }.isSuccess
          if (!saved)
              return@execute Result.failure(
                  ApiCallException("本机操作记录不可用，请稍后重试", code = "write_storage_unavailable")
              )
        }
        val result =
            try {
              submit()
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              Result.failure(e)
            }
        if (!scope.isCurrent()) throw CancellationException("账号或连接方式已变化")
        val error = result.exceptionOrNull()
        if (error is CancellationException) throw error
        if (result.isSuccess) {
          // 明确的学校回执可确认成功/拒绝；回读用于刷新状态，不用回读失败推翻已知回执。
          key?.let { FeatureReadCache.settings.remove(it) }
          if (accepted(result.getOrThrow())) {
            FeatureReadCache.invalidate(feature)
            checked()
          }
          return@execute result
        }
        if (knownRejection(error)) {
          key?.let { FeatureReadCache.settings.remove(it) }
          error?.let { LocalDiagnostics.record(feature, "write", it) }
          return@execute result
        }
        val confirmed = checked()
        if (confirmed != null) {
          key?.let { FeatureReadCache.settings.remove(it) }
          FeatureReadCache.invalidate(feature)
          return@execute Result.success(confirmed)
        }
        val unresolved = unknown(error)
        LocalDiagnostics.record(feature, "write", unresolved)
        Result.failure(unresolved)
      }
    } finally {
      mutex.unlock()
    }
  }

  private fun knownRejection(error: Throwable?): Boolean {
    val e = error as? ApiCallException ?: return false
    if (e.cause is java.io.IOException || e.cause is kotlinx.serialization.SerializationException)
        return false
    val venue = e.cause as? cn.edu.ubaa.plus.api.venue.VenueApiException
    return venue?.businessCode != null ||
        (e.status?.value in 400..499 && e.status?.value !in setOf(408, 425)) ||
        e.code in
            setOf(
                "course_full",
                "course_not_selectable",
                "already_selected",
                "invalid_request",
                "reservation_invalid",
                "reservation_token_missing",
            )
  }
}
