package cn.edu.ubaa.plus.api.storage

import cn.edu.ubaa.plus.api.local.LocalJudgeApiCache
import cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator
import cn.edu.ubaa.plus.repository.GlobalTermRepository
import cn.edu.ubaa.plus.util.BusinessDispatchers
import cn.edu.ubaa.plus.widget.WidgetTodoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 清理临时业务数据，不清除凭据、Cookie、偏好、本地化课表和未决写入保护。 */
internal object CacheMaintenance {
  private val mutex = Mutex()
  private val revision = MutableStateFlow(0L)
  val changes: StateFlow<Long> = revision

  suspend fun writeIfCurrent(expectedRevision: Long, block: () -> Unit): Boolean =
      mutex.withLock {
        if (revision.value != expectedRevision) return@withLock false
        withContext(BusinessDispatchers.io) { block() }
        true
      }

  suspend fun clear(): Unit =
      mutex.withLock {
        withContext(kotlinx.coroutines.NonCancellable + BusinessDispatchers.io) {
          try {
            SchoolReadCoordinator.invalidate()
            FeatureReadCache.clearChecked()
            CachedHomeStore.clear()
            WidgetTodoStore.clear()
            GradeScoreCacheStore.clearAllScopes()
            GlobalTermRepository.instance.clear()
            LocalJudgeApiCache.clearAll()
          } finally {
            revision.value++
          }
        }
      }
}
