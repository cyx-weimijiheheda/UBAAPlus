package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.model.dto.UserData
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.screens.menu.*
import com.russhwolf.settings.MapSettings
import kotlin.test.*
import kotlinx.datetime.LocalDateTime

class CachedHomeStoreTest {
  @BeforeTest
  fun setup() {
    ScheduleStore.settings = MapSettings()
    CachedUserStore.settings = MapSettings()
    CachedHomeStore.settings = MapSettings()
  }

  private fun todo(id: String, source: HomeTodoSource) =
      HomeTodoItem(
          id,
          source,
          "课程",
          "",
          "",
          "",
          LocalDateTime(2026, 11, 17, 12, 0),
          HomeTodoAction.OpenExam,
      )

  @Test
  fun migrationUsesOnlyThePersistedMatchingAccountNeverSavedCredentialsAlone() {
    cn.edu.ubaa.plus.testing.TestSettings.installInMemorySettings()
    cn.edu.ubaa.plus.api.ConnectionModeStore.save(cn.edu.ubaa.plus.api.ConnectionMode.DIRECT)
    cn.edu.ubaa.plus.api.ConnectionRuntime.resolveSelectedMode()
    ScheduleStore.useAccount("alice")
    CredentialStore.saveCredentials("alice", "synthetic-password")
    CachedUserStore.migrateVerifiedAccount()
    assertNull(CachedUserStore.current())
    cn.edu.ubaa.plus.api.local.LocalAuthSessionStore.save(
        cn.edu.ubaa.plus.api.local.LocalAuthSession(
            "alice",
            UserData("Alice", "alice"),
            "2026-11-17T08:00:00Z",
            "2026-11-17T08:00:00Z",
        )
    )
    CachedUserStore.migrateVerifiedAccount()
    assertEquals("alice", CachedUserStore.current()?.schoolid)
    CachedUserStore.clear()
    ScheduleStore.forgetAccount()
    CachedUserStore.migrateVerifiedAccount()
    assertNull(CachedUserStore.current())
  }

  @Test
  fun onlyConfirmedMatchingAccountCanRestoreHomeAndExplicitLogoutRevokesIt() {
    assertNull(CachedUserStore.current())
    CachedUserStore.remember(UserData("Alice", "alice"))
    assertEquals("alice", CachedUserStore.current()?.schoolid)
    val snapshot = CachedHomeSnapshot("alice", todos = listOf(todo("exam", HomeTodoSource.EXAM)))
    CachedHomeStore.write(snapshot)
    assertEquals(snapshot, CachedHomeStore.read("alice"))
    ScheduleStore.useAccount("bob")
    assertNull(CachedUserStore.current())
    assertNull(CachedHomeStore.read("alice"))
    CachedHomeStore.write(snapshot.copy(todos = emptyList()))
    ScheduleStore.useAccount("alice")
    assertEquals(snapshot, CachedHomeStore.read("alice"))
    CachedUserStore.clear()
    CachedHomeStore.clear()
    assertNull(CachedUserStore.current())
    assertNull(CachedHomeStore.read("alice"))
  }

  @Test
  fun dailyCoursesAndAttendanceSurviveRestartButNeverLeakIntoAnotherDay() {
    CachedUserStore.remember(UserData("Alice", "alice"))
    val courses = listOf(cn.edu.ubaa.plus.model.dto.TodayClass("测试课程", "教室", "08:00-09:35", null))
    val attendance =
        listOf(
            cn.edu.ubaa.plus.model.dto.SigninClassDto(
                "course",
                "测试课程",
                "2026-11-17 08:00:00",
                "2026-11-17 09:35:00",
                1,
            )
        )
    CachedHomeStore.write(
        CachedHomeSnapshot(
            "alice",
            signin = attendance,
            todayClasses = courses,
            todayDate = "2026-11-17",
            signinDate = "2026-11-17",
        )
    )
    val restored = assertNotNull(CachedHomeStore.read("alice"))
    assertEquals(courses, restored.coursesOn("2026-11-17"))
    assertEquals(attendance, restored.signinOn("2026-11-17"))
    assertTrue(restored.coursesOn("2026-11-18").isEmpty())
    assertTrue(restored.signinOn("2026-11-18").isEmpty())
    assertTrue(CachedHomeSnapshot("alice").coursesOn("2026-11-17").isEmpty())
  }

  @Test
  fun failedSourceKeepsItsOwnCacheButAnEmptySuccessfulResponseRemovesOldItems() {
    val oldExam = todo("old-exam", HomeTodoSource.EXAM)
    val oldSpoc = todo("old-spoc", HomeTodoSource.SPOC)
    val fresh = todo("new-exam", HomeTodoSource.EXAM)
    assertEquals(
        setOf("new-exam", "old-spoc"),
        mergeHomeTodos(listOf(fresh), listOf(oldExam, oldSpoc), setOf(HomeTodoSource.SPOC))
            .map { it.id }
            .toSet(),
    )
    assertEquals(listOf(fresh), mergeHomeTodos(listOf(fresh), listOf(oldExam, oldSpoc), emptySet()))
  }
}
