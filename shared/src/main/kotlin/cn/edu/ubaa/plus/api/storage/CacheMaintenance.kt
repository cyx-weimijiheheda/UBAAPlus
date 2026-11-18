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

  /** 可清理持久化缓存正文的 UTF-8 大小；不包含凭据、本地课表或应用资源。 */
  suspend fun cachedSizeBytes(): Long =
      mutex.withLock {
        withContext(BusinessDispatchers.io) {
          fun size(settings: com.russhwolf.settings.Settings, matches: (String) -> Boolean): Long =
              settings.keys.filter(matches).sumOf { key ->
                settings.getString(key, "").toByteArray(Charsets.UTF_8).size.toLong()
              }
          ProfileAvatarStore.sizeBytes() +
              size(FeatureReadCache.settings) { it.startsWith("business:") } +
              size(CachedHomeStore.settings) { it.startsWith("home_snapshot:") } +
              runCatching {
                    size(com.russhwolf.settings.Settings()) { it.startsWith("home_snapshot:") }
                  }
                  .getOrDefault(0L) +
              size(WidgetTodoStore.settings) { it == "widget_todo_snapshot" } +
              size(GradeScoreCacheStore.settings) {
                it.startsWith("grade_score_cache:") || it == "grade_score_cache_users"
              }
        }
      }

  /** 显式注销删除当前账户保存的数据；与设置中的临时缓存清理分开。 */
  suspend fun clearAccount(owner: String): Unit =
      mutex.withLock {
        withContext(kotlinx.coroutines.NonCancellable + BusinessDispatchers.io) {
          try {
            SchoolReadCoordinator.invalidate()
            ProfileAvatarStore.clearAccount(owner)
            FeatureReadCache.clearAccount(owner)
            CachedHomeStore.clearAccount(owner)
            WidgetTodoStore.clearAccount(owner)
            GradeScoreCacheStore.clearKnownUserAllModes(owner)
            cn.edu.ubaa.plus.repository.ScheduleStore.removeAccount(owner)
            cn.edu.ubaa.plus.reminder.LibBookLeaveStore.clearAccount(owner)
            cn.edu.ubaa.plus.reminder.ReminderSeenStore.clearUser(owner)
            BykcCourseFilterStore.clear(owner)
            YgdkReminderStore.clear(owner)
            CgyyReservationFormStore.clear()
            GlobalTermRepository.instance.clear()
            LocalJudgeApiCache.clearAll()
          } finally {
            revision.value++
          }
        }
      }

  suspend fun clear(): Unit =
      mutex.withLock {
        withContext(kotlinx.coroutines.NonCancellable + BusinessDispatchers.io) {
          try {
            SchoolReadCoordinator.invalidate()
            ProfileAvatarStore.clear()
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
