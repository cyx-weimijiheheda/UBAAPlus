package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.model.dto.SigninClassDto
import cn.edu.ubaa.plus.model.dto.TodayClass
import cn.edu.ubaa.plus.ui.screens.menu.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDateTime

class HomeCourseStatusTest {
  private val course = TodayClass("数学", null, "08:00-09:40", null)
  private val attendance = SigninClassDto("1", "数学", "08:00", "09:40", 0)

  @Test
  fun states() {
    val before = LocalDateTime(2026, 11, 16, 9, 0)
    val after = LocalDateTime(2026, 11, 16, 10, 0)
    assertEquals(HomeCourseStatus.UNKNOWN, homeCourseStatus(course, null, before, false))
    assertEquals(HomeCourseStatus.PENDING, homeCourseStatus(course, attendance, before, true))
    assertEquals(HomeCourseStatus.MISSED, homeCourseStatus(course, attendance, after, true))
    assertEquals(
        HomeCourseStatus.COMPLETE,
        homeCourseStatus(course, attendance.copy(signStatus = 1), before, true),
    )
    assertEquals(HomeCourseStatus.COMPLETE, homeCourseStatus(course, null, after, true))
    assertEquals(HomeCourseStatus.PENDING, homeCourseStatus(course, null, before, true))
  }

  @Test
  fun matchingRequiresUnambiguousNameAndTime() {
    assertEquals(attendance, matchHomeSignin(course, listOf(attendance)))
    assertNull(matchHomeSignin(course, listOf(attendance, attendance.copy(courseId = "2"))))
    assertNull(matchHomeSignin(course, listOf(attendance.copy(classBeginTime = "10:00"))))
    assertEquals(
        attendance,
        matchHomeSignin(course.copy(bizName = "课", shortName = "数学"), listOf(attendance)),
    )
  }
}
