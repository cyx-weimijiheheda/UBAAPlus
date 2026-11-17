package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.local.LocalAuthSession
import cn.edu.ubaa.plus.api.local.LocalAuthSessionStore
import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.repository.*
import cn.edu.ubaa.plus.testing.TestSettings
import com.russhwolf.settings.MapSettings
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer

class CacheMaintenanceTest {
  @Test
  fun clearingTemporaryDataKeepsLoginPreferencesLocalizedScheduleAndUnresolvedWrites() = runTest {
    TestSettings.installInMemorySettings()
    ConnectionModeStore.save(ConnectionMode.DIRECT)
    ConnectionRuntime.resolveSelectedMode()
    CachedUserStore.remember(UserData("Synthetic User", "alice"))
    CredentialStore.saveCredentials("alice", "synthetic-password")
    AuthTokensStore.save(StoredAuthTokens("synthetic-token", "synthetic-refresh"))
    LocalAuthSessionStore.save(
        LocalAuthSession("alice", UserData("Synthetic User", "alice"), "now", "now")
    )
    AppPreferencesStore.settings = MapSettings()
    AppPreferencesStore.setMysterySwitchEnabled(true)
    YgdkReminderStore.setEnabled("alice", true)
    val localized = listOf(SemesterSchedule(emptyList(), "term", emptyList(), emptyMap()))
    ScheduleStore.write("alice", localized)
    CachedHomeStore.write(CachedHomeSnapshot("alice"))
    FeatureReadCache.save("alice", "grades", "term", serializer<List<String>>(), listOf("old"))
    FeatureReadCache.settings.putBoolean("write_pending:synthetic", true)
    val revision = CacheMaintenance.changes.value
    CacheMaintenance.clear()
    assertNull(FeatureReadCache.get<List<String>>("grades", "term"))
    assertNull(CachedHomeStore.read("alice"))
    assertEquals("alice", ScheduleStore.account())
    assertEquals(localized, ScheduleStore.read("alice"))
    assertEquals("alice", CachedUserStore.current()?.schoolid)
    assertEquals("synthetic-password", CredentialStore.getPassword())
    assertEquals("synthetic-token", AuthTokensStore.get()?.accessToken)
    assertEquals("alice", LocalAuthSessionStore.get()?.user?.schoolid)
    assertTrue(AppPreferencesStore.state.value.mysterySwitchEnabled)
    assertTrue(YgdkReminderStore.isEnabled("alice"))
    assertTrue(FeatureReadCache.settings.getBoolean("write_pending:synthetic", false))
    var staleWrite = false
    assertFalse(CacheMaintenance.writeIfCurrent(revision) { staleWrite = true })
    assertFalse(staleWrite)
    assertEquals(revision + 1, CacheMaintenance.changes.value)
  }
}
