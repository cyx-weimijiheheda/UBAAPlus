package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.model.dto.Exam
import cn.edu.ubaa.plus.ui.screens.menu.HomeTodoSource
import cn.edu.ubaa.plus.ui.screens.menu.buildHomeTodoItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime

class HomeExamTodoTest {
  @Test
  fun `only upcoming exams within fourteen calendar days are shown in order`() {
    val exams =
        listOf(
            Exam(courseName = "边界", examDate = "2026-11-30", startTime = "09:00"),
            Exam(courseName = "明天", examDate = "2026-11-17", startTime = "10:00"),
            Exam(courseName = "太远", examDate = "2026-12-01", startTime = "09:00"),
            Exam(courseName = "已开始", examDate = "2026-11-16", startTime = "08:00"),
            Exam(courseName = "未排定"),
        )
    val items =
        buildHomeTodoItems(
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            exams = exams,
            now = LocalDateTime(2026, 11, 16, 9, 0),
        )
    assertEquals(listOf("明天", "边界"), items.map { it.title })
    assertEquals(listOf(HomeTodoSource.EXAM, HomeTodoSource.EXAM), items.map { it.source })
  }
}
