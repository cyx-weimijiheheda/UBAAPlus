package cn.edu.ubaa.plus.repository

import cn.edu.ubaa.plus.model.dto.*
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/**
 * 验证 [ScheduleStore] 在底层存储限制单个值长度时，仍能完整保存并还原整学期课表。
 *
 * 原实现依赖桌面专有的 `java.util.prefs.PreferencesSettings`（Android 没有 `java.util.prefs`）， 现改为用带长度上限的内存
 * Settings 复现同一约束，使该不变量在 Android 上同样受测。
 */
class ScheduleStorePreferencesTest {

  /** 包装 [delegate]，对单个字符串值的长度施加上限，模拟 Preferences 的单值长度限制。 */
  private class LimitedLengthSettings(private val delegate: Settings, private val maxLength: Int) :
      Settings by delegate {
    override fun putString(key: String, value: String) {
      require(value.length <= maxLength) { "value for '$key' exceeds $maxLength chars" }
      delegate.putString(key, value)
    }
  }

  @Test
  fun `超大课表快照会被拆分写入并在读取时还原`() {
    val term = Term("20261", "示例学期", true, 0)
    val start = LocalDate.parse("2026-09-07").toEpochDays()
    val weeks =
        (1..30).map {
          Week(
              LocalDate.fromEpochDays(start + (it - 1) * 7).toString(),
              LocalDate.fromEpochDays(start + (it - 1) * 7 + 6).toString(),
              term.itemCode,
              false,
              it,
              "第${it}周",
          )
        }
    val course =
        CourseClass(
            "sample",
            "示例课程🧪",
            "class-1",
            "2",
            "08:00",
            "09:35",
            1,
            2,
            "示例教室",
            "示例教师",
            null,
            null,
            1,
        )
    val snapshots =
        listOf(
            SemesterSchedule(
                listOf(term),
                term.itemCode,
                weeks,
                weeks.associate {
                  it.serialNumber to WeeklySchedule(listOf(course), term.itemCode, term.itemName)
                },
            )
        )
    // 前提：序列化后的快照确实超过单值上限，否则这个测试没有意义
    assertTrue(Json.encodeToString(snapshots).length > MAX_VALUE_LENGTH)

    val original = ScheduleStore.settings
    val backing = MapSettings()
    try {
      ScheduleStore.settings = LimitedLengthSettings(backing, MAX_VALUE_LENGTH)
      ScheduleStore.write("sample-account", snapshots)
      assertEquals(snapshots, ScheduleStore.read("sample-account"))
      // 写入的每一个值都必须在上限之内，说明大值确实被拆分了
      assertTrue(backing.keys.all { backing.getString(it, "").length <= MAX_VALUE_LENGTH })
    } finally {
      ScheduleStore.settings = original
    }
  }

  private companion object {
    const val MAX_VALUE_LENGTH = 8192
  }
}
