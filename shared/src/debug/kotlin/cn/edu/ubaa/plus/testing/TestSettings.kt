package cn.edu.ubaa.plus.testing

import cn.edu.ubaa.plus.api.ConnectionModeStore
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.local.LocalCookieStore
import cn.edu.ubaa.plus.api.storage.AnnouncementReadStore
import cn.edu.ubaa.plus.api.storage.AuthTokensStore
import cn.edu.ubaa.plus.api.storage.BykcCourseFilterStore
import cn.edu.ubaa.plus.api.storage.CgyyReservationFormStore
import cn.edu.ubaa.plus.api.storage.ClientIdStore
import cn.edu.ubaa.plus.api.storage.CredentialStore
import cn.edu.ubaa.plus.api.storage.DisclaimerStore
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
    AuthTokensStore.settings = MapSettings()
    ClientIdStore.settings = MapSettings()
    CredentialStore.settings = MapSettings()
    ConnectionModeStore.settings = MapSettings()
    ScheduleStore.settings = MapSettings()
    GradeScoreCacheStore.settings = MapSettings()
    BykcCourseFilterStore.settings = MapSettings()
    AnnouncementReadStore.settings = MapSettings()
    CgyyReservationFormStore.settings = MapSettings()
    YgdkReminderStore.settings = MapSettings()
    LocalAuthSessionStore.settings = MapSettings()
    LocalCookieStore.settings = MapSettings()
    DisclaimerStore.settings = MapSettings()
  }
}
