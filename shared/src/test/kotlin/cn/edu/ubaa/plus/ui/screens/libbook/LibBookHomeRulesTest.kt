package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibBookHomeRulesTest {

  private fun booking(
      id: String,
      day: String = "2026-11-06",
      begin: String = "10:00",
      status: String = "4",
      statusName: String = "未签到",
  ) =
      LibBookBookingDto(
          id = id,
          nameMerge = "图书馆三层",
          seatNo = "A12",
          day = day,
          beginTime = begin,
          endTime = "12:00",
          status = status,
          statusName = statusName,
      )

  @Test
  fun `待签到状态不被通用使用中文案改变可执行操作`() {
    val waiting = booking("waiting", status = "4", statusName = "使用中")
    assertEquals(
        listOf(LibBookCardAction.CANCEL),
        waiting.cardActions().filter(waiting::isCardActionEnabled),
    )
  }

  @Test
  fun `刷新后使用状态覆盖残留操作标志`() {
    val returned =
        booking("returned", status = "3", statusName = "使用中")
            .copy(flagLeave = "1", flagIn = "1", flagOut = "0")
    assertEquals("使用中", libBookHomeStatusLabel(returned))
    assertEquals(
        listOf(LibBookCardAction.LEAVE, LibBookCardAction.CHECKOUT),
        returned.cardActions().filter(returned::isCardActionEnabled),
    )
    assertEquals(
        "未签到",
        libBookHomeStatusLabel(booking("waiting", status = "4", statusName = "使用中")),
    )
  }

  @Test
  fun `使用中的预约优先于待签到`() {
    val bookings =
        listOf(
            booking("in-use", begin = "10:00", status = "3", statusName = "使用中"),
            booking("early", begin = "08:00"),
            booking("terminal", status = "6", statusName = "已取消"),
            booking("tomorrow", day = "2026-11-07"),
        )

    assertEquals("in-use", selectHomeLibBookBooking(bookings, "2026-11-06")?.id)
  }

  @Test
  fun `没有使用中的预约时取最早开始的待签到`() {
    val bookings = listOf(booking("late", begin = "14:00"), booking("early", begin = "08:00"))

    assertEquals("early", selectHomeLibBookBooking(bookings, "2026-11-06")?.id)
  }

  @Test
  fun `day 字段带时间或斜杠也能匹配今天`() {
    assertEquals(
        "with-time",
        selectHomeLibBookBooking(
                listOf(booking("with-time", day = "2026-11-06 00:00:00")),
                "2026-11-06",
            )
            ?.id,
    )
    assertEquals(
        "slashed",
        selectHomeLibBookBooking(listOf(booking("slashed", day = "2026/11/06")), "2026-11-06")?.id,
    )
  }

  @Test
  fun `状态文案缺失时按可操作状态兜底`() {
    assertEquals(
        "待签到",
        libBookHomeStatusLabel(booking("reserved", status = "", statusName = "")),
    )
    assertEquals(
        "使用中",
        libBookHomeStatusLabel(booking("in-use", status = "3", statusName = "")),
    )
    assertEquals(
        "未签到",
        libBookHomeStatusLabel(booking("reserved", status = "4", statusName = "未签到")),
    )
  }

  @Test
  fun `没有今天的预约时返回空`() {
    assertNull(
        selectHomeLibBookBooking(listOf(booking("tomorrow", day = "2026-11-07")), "2026-11-06")
    )
  }

  @Test
  fun `不同状态只启用允许的操作`() {
    fun enabled(booking: LibBookBookingDto) =
        booking.cardActions().filter(booking::isCardActionEnabled)
    assertEquals(
        listOf(LibBookCardAction.CANCEL),
        enabled(booking("reserved")),
    )
    assertEquals(
        listOf(LibBookCardAction.LEAVE, LibBookCardAction.CHECKOUT),
        enabled(booking("in-use", status = "3", statusName = "使用中")),
    )
    assertEquals(
        listOf(LibBookCardAction.CHECKOUT),
        enabled(booking("left", status = "7", statusName = "临时离开")),
    )
  }

  @Test
  fun `已结束预约没有操作`() {
    assertEquals(emptyList(), booking("done", status = "8", statusName = "已完成").cardActions())
  }
}
