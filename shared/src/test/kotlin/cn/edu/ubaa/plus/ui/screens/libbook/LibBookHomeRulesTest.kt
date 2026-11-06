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
  fun `只选今天的未结束预约并取最早开始`() {
    val bookings =
        listOf(
            booking("in-use", begin = "10:00", status = "3", statusName = "使用中"),
            booking("early", begin = "08:00"),
            booking("terminal", status = "6", statusName = "已取消"),
            booking("tomorrow", day = "2026-11-07"),
        )

    assertEquals("early", selectHomeLibBookBooking(bookings, "2026-11-06")?.id)
  }

  @Test
  fun `没有今天的预约时返回空`() {
    assertNull(
        selectHomeLibBookBooking(listOf(booking("tomorrow", day = "2026-11-07")), "2026-11-06")
    )
  }

  @Test
  fun `待签到显示取消与扫码签到`() {
    assertEquals(
        listOf(LibBookCardAction.CANCEL, LibBookCardAction.SIGN_IN),
        booking("reserved").cardActions(),
    )
  }

  @Test
  fun `使用中显示临时离开与完全离开`() {
    assertEquals(
        listOf(
            LibBookCardAction.CANCEL,
            LibBookCardAction.LEAVE,
            LibBookCardAction.CHECKOUT,
            LibBookCardAction.SIGN_IN,
        ),
        booking("in-use", status = "3", statusName = "使用中").cardActions(),
    )
  }

  @Test
  fun `临时离开后显示返回签到`() {
    assertEquals(
        listOf(
            LibBookCardAction.CANCEL,
            LibBookCardAction.RETURN_SIGN_IN,
            LibBookCardAction.CHECKOUT,
        ),
        booking("left", status = "7", statusName = "临时离开").cardActions(),
    )
  }

  @Test
  fun `已结束预约没有操作`() {
    assertEquals(emptyList(), booking("done", status = "8", statusName = "已完成").cardActions())
  }
}
