package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.ui.screens.bykc.*
import kotlin.test.*
import kotlinx.datetime.LocalDateTime

class BykcConflictCheckTest {
  @Test
  fun boundariesTouchingAreAllowedButOverlapIsReported() {
    val start = LocalDateTime(2026, 11, 17, 10, 0)
    val end = LocalDateTime(2026, 11, 17, 11, 0)
    val before = CourseConflict("before", LocalDateTime(2026, 11, 17, 9, 0), start)
    val after = CourseConflict("after", end, LocalDateTime(2026, 11, 17, 12, 0))
    val conflict =
        CourseConflict(
            "exam",
            LocalDateTime(2026, 11, 17, 10, 30),
            LocalDateTime(2026, 11, 17, 11, 30),
        )
    assertEquals(
        listOf(conflict),
        overlappingCourses(start, end, listOf(before, after, conflict, conflict)),
    )
  }
}
