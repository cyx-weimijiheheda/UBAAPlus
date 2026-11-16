package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.screens.menu.*
import cn.edu.ubaa.plus.widget.WidgetTodoStore
import com.russhwolf.settings.MapSettings
import kotlin.test.*
import kotlinx.datetime.LocalDateTime

class WidgetTodoStoreTest {
  @Test
  fun snapshotIsAccountScopedAndExpiredItemsDisappear() {
    val old = ScheduleStore.settings
    try {
      ScheduleStore.settings = MapSettings()
      WidgetTodoStore.settings = MapSettings()
      ScheduleStore.useAccount("alice")
      val now = LocalDateTime(2026, 11, 16, 12, 0)
      val due = LocalDateTime(2026, 11, 16, 13, 0)
      val item =
          HomeTodoItem(
              "1",
              HomeTodoSource.EXAM,
              "数学",
              "教室",
              "待考试",
              "13:00",
              due,
              HomeTodoAction.OpenExam,
          )
      WidgetTodoStore.write("alice", listOf(item), now)
      assertEquals("数学", WidgetTodoStore.read("alice", now)?.items?.single()?.title)
      assertNull(WidgetTodoStore.read("bob", now))
      assertNull(WidgetTodoStore.read(null, now))
      assertTrue(
          WidgetTodoStore.read("alice", LocalDateTime(2026, 11, 16, 14, 0))!!.items.isEmpty()
      )
      WidgetTodoStore.write("bob", emptyList(), now)
      assertEquals(1, WidgetTodoStore.read("alice", now)?.items?.size)
      WidgetTodoStore.clear()
      assertNull(WidgetTodoStore.read("alice", now))
    } finally {
      ScheduleStore.settings = old
    }
  }
}
