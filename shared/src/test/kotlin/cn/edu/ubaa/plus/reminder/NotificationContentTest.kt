package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationContentTest {

  private val booking =
      LibBookBookingDto(
          id = "b1",
          nameMerge = "图书馆三层 A区",
          areaName = "A区",
          seatNo = "A12",
          day = "2026-11-06",
          beginTime = "14:00",
          endTime = "16:00",
          status = "3",
          statusName = "使用中",
      )

  @Test
  fun `预约详情带座位时间与状态`() {
    val detail = LibBookNotifications.bookingDetail(booking)
    assertTrue(detail.contains("图书馆三层 A区"))
    assertTrue(detail.contains("14:00-16:00"))
    assertTrue(detail.contains("使用中"))
  }

  @Test
  fun `nameMerge 为空时回退分区与座位号`() {
    val detail = LibBookNotifications.bookingDetail(booking.copy(nameMerge = ""))
    assertTrue(detail.contains("A区 / A12"))
  }

  @Test
  fun `成功与失败的标题可区分`() {
    val ok =
        LibBookNotifications.Outcome.entries
            .filter { it.succeeded }
            .map { LibBookNotifications.titleOf(it) }
    val fail =
        LibBookNotifications.Outcome.entries
            .filterNot { it.succeeded }
            .map { LibBookNotifications.titleOf(it) }
    assertTrue(ok.none { it.contains("失败") })
    assertTrue(fail.all { it.contains("失败") })
  }

  @Test
  fun `作业通知正文依次包含课程标题与截止时间`() {
    val text = assignmentSummary("高等数学", "第三章习题", "2026-11-07 23:59:59")
    assertTrue(text.startsWith("高等数学"))
    assertTrue(text.contains("第三章习题"))
    assertTrue(text.contains("截止"))
  }

  @Test
  fun `作业缺少课程名时不留下多余分隔`() {
    assertEquals("第三章习题", assignmentSummary("", "第三章习题", null))
  }

  @Test
  fun `作业候选转成事件后保留去重 key 与类型`() {
    val candidate =
        ReminderCandidate(
            key = "spoc-new:1",
            kind = ReminderEventKind.NEW_ASSIGNMENT,
            title = "新作业",
            text = "正文",
            route = AppNotificationRoute.SpocAssignment("1"),
        )
    val event = candidate.toEvent(kotlinx.datetime.LocalDateTime(2026, 11, 6, 9, 0))
    assertEquals("spoc-new:1", event.key)
    assertEquals(ReminderEventKind.NEW_ASSIGNMENT, event.kind)
    assertEquals(candidate.route, event.route)
  }
}
