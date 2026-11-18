package cn.edu.ubaa.plus.testing

import cn.edu.ubaa.plus.api.ConnectionModeStore
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.storage.AnnouncementReadStore
import cn.edu.ubaa.plus.api.storage.AuthTokensStore
import cn.edu.ubaa.plus.api.storage.BykcCourseFilterStore
import cn.edu.ubaa.plus.api.storage.CgyyReservationFormStore
import cn.edu.ubaa.plus.api.storage.CredentialStore
import cn.edu.ubaa.plus.api.storage.GradeScoreCacheStore
import cn.edu.ubaa.plus.api.storage.YgdkReminderStore
import cn.edu.ubaa.plus.repository.ScheduleStore
import com.russhwolf.settings.MapSettings

/**
 * 把各存储单例切换到内存实现，供单元测试使用。
 *
 * `multiplatform-settings-no-arg` 的 `Settings()` 在 Android 上需要 Application context（由
 * androidx.startup 的 ContentProvider 提供），而普通 JVM 单元测试没有 Android 运行时， 直接调用会抛
 * NullPointerException。测试只关心业务逻辑，不关心底层存储后端， 因此在 set up 阶段统一注入内存实现。
 *
 * 放在 `src/debug` 源集：**不会进入 release 产物**，但同模块（以及依赖本模块 debug variant 的模块）的测试都能访问，含 `internal` 的存储单例。
 */
object TestSettings {
  fun installInMemorySettings() {
    cn.edu.ubaa.plus.util.BusinessDispatchers.io = kotlinx.coroutines.Dispatchers.Unconfined
    cn.edu.ubaa.plus.widget.WidgetTodoStore.settings = MapSettings()
    cn.edu.ubaa.plus.api.storage.FeatureReadCache.settings = MapSettings()
    cn.edu.ubaa.plus.api.storage.LocalDiagnostics.settings = MapSettings()
    cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator.invalidate()
    cn.edu.ubaa.plus.api.recovery.SchoolReadCoordinator.allowOnline(true)
    AuthTokensStore.settings = MapSettings()
    cn.edu.ubaa.plus.api.storage.PublicNoticeStore.settings = MapSettings()
    CredentialStore.settings = MapSettings()
    cn.edu.ubaa.plus.api.storage.ProfileAvatarStore.settings = MapSettings()
    cn.edu.ubaa.plus.api.storage.CachedUserStore.settings = MapSettings()
    cn.edu.ubaa.plus.api.storage.CachedHomeStore.settings = MapSettings()
    ConnectionModeStore.settings = MapSettings()
    ScheduleStore.settings = MapSettings()
    GradeScoreCacheStore.settings = MapSettings()
    BykcCourseFilterStore.settings = MapSettings()
    AnnouncementReadStore.settings = MapSettings()
    CgyyReservationFormStore.settings = MapSettings()
    YgdkReminderStore.settings = MapSettings()
    cn.edu.ubaa.plus.reminder.LibBookLeaveStore.settings = MapSettings()
    cn.edu.ubaa.plus.reminder.ReminderSeenStore.settings = MapSettings()
    LocalAuthSessionStore.settings = MapSettings()
    LocalCookieStore.settings = MapSettings()
  }
}
