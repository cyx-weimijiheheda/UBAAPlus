package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.api.storage.*
import cn.edu.ubaa.plus.model.dto.*
import cn.edu.ubaa.plus.repository.*
import cn.edu.ubaa.plus.testing.TestSettings
import kotlin.test.*
import kotlinx.datetime.*
import kotlinx.serialization.serializer

class AcademicContextTest {
  @Test
  fun onlyCurrentAccountAndTermAffectPeDefaultAndManualChoiceWins() {
    TestSettings.installInMemorySettings()
    ScheduleStore.useAccount("alice")
    val today = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).date
    val terms = listOf(Term("current", "当前学期", true, 0), Term("old", "旧学期", false, 1))
    val weeks =
        listOf(
            Week(
                today.minus(DatePeriod(days = 3)).toString(),
                today.plus(DatePeriod(days = 3)).toString(),
                "current",
                true,
                1,
                "第一周",
            )
        )
    val pe =
        CourseClass("PE", "体育（1）", null, null, "08:00", "09:35", 1, 2, null, null, null, null, 1)
    FeatureReadCache.save("alice", "schedule", "terms", serializer<List<Term>>(), terms)
    FeatureReadCache.save("alice", "schedule", "weeks:current", serializer<List<Week>>(), weeks)
    FeatureReadCache.save(
        "alice",
        "schedule",
        "weekly:old:1",
        serializer<WeeklySchedule>(),
        WeeklySchedule(listOf(pe), "old", "旧"),
    )
    assertFalse(AcademicContext.hasPhysicalEducation("alice", today))
    FeatureReadCache.save(
        "alice",
        "schedule",
        "weekly:current:1",
        serializer<WeeklySchedule>(),
        WeeklySchedule(listOf(pe), "current", "当前"),
    )
    assertTrue(AcademicContext.hasPhysicalEducation("alice", today))
    assertTrue(YgdkReminderStore.isEnabled("alice"))
    YgdkReminderStore.setEnabled("alice", false)
    assertFalse(YgdkReminderStore.isEnabled("alice"))
    YgdkReminderStore.setEnabled("alice", true)
    assertTrue(YgdkReminderStore.isEnabled("alice"))
    assertFalse(AcademicContext.hasPhysicalEducation("bob", today))
    assertNull(AcademicContext.current("alice", today.plus(DatePeriod(years = 1))))
  }
}
