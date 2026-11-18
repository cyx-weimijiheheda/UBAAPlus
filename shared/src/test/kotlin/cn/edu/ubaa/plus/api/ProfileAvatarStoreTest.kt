package cn.edu.ubaa.plus.api

import cn.edu.ubaa.plus.api.storage.CacheMaintenance
import cn.edu.ubaa.plus.api.storage.ProfileAvatarStore
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.testing.TestSettings
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class ProfileAvatarStoreTest {
  @BeforeTest
  fun setup() {
    TestSettings.installInMemorySettings()
  }

  @Test
  fun thumbnailsStayWithTheirAccountAndFullLogoutCleanupKeepsOtherAccounts() = runTest {
    ScheduleStore.useAccount("alice")
    val a = byteArrayOf(1, 2, 3)
    ProfileAvatarStore.save("alice", a)
    ScheduleStore.useAccount("bob")
    assertNull(ProfileAvatarStore.read("alice"))
    assertNull(ProfileAvatarStore.read("bob"))
    ProfileAvatarStore.save("bob", byteArrayOf(4, 5, 6))
    assertFailsWith<IllegalArgumentException> { ProfileAvatarStore.save("alice", a) }
    CacheMaintenance.clearAccount("bob")
    ScheduleStore.useAccount("alice")
    assertContentEquals(a, ProfileAvatarStore.read("alice"))
    ScheduleStore.useAccount("bob")
    assertNull(ProfileAvatarStore.read("bob"))
  }

  @Test
  fun cacheClearRemovesPhotosAndDoesNotPermitLateImportsToRecreateThem() = runTest {
    ScheduleStore.useAccount("alice")
    ProfileAvatarStore.save("alice", byteArrayOf(1, 2, 3))
    val revision = CacheMaintenance.changes.value
    assertTrue(CacheMaintenance.cachedSizeBytes() > 0)
    CacheMaintenance.clear()
    assertNull(ProfileAvatarStore.read("alice"))
    assertEquals(0L, ProfileAvatarStore.sizeBytes())
    assertFalse(
        CacheMaintenance.writeIfCurrent(revision) {
          ProfileAvatarStore.save("alice", byteArrayOf(4, 5, 6))
        }
    )
    assertNull(ProfileAvatarStore.read("alice"))
  }
}
