package cn.edu.ubaa.plus.model.dto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibBookBookingStatusTest {
  @Test
  fun `ended booking cannot be cancelled`() {
    val booking = LibBookBookingDto(id = "b1", status = "8", statusName = "已结束")

    assertFalse(booking.canCancelBooking())
    assertEquals("该预约已结束或已取消，无需取消", booking.cancelBlockedMessage())
  }

  @Test
  fun `user cancelled booking cannot be cancelled`() {
    val booking = LibBookBookingDto(id = "b1", status = "6", statusName = "用户取消")

    assertFalse(booking.canCancelBooking())
    assertEquals("该预约已结束或已取消，无需取消", booking.cancelBlockedMessage())
  }

  @Test
  fun `active booking with id can be cancelled`() {
    val booking = LibBookBookingDto(id = "b1", status = "1", statusName = "已预约")

    assertTrue(booking.canCancelBooking())
    assertEquals(null, booking.cancelBlockedMessage())
  }

  @Test
  fun `booking without id cannot be cancelled`() {
    val booking = LibBookBookingDto(id = "", status = "1", statusName = "已预约")

    assertFalse(booking.canCancelBooking())
    assertEquals("预约记录不存在或已失效，请刷新后重试", booking.cancelBlockedMessage())
  }

  @Test
  fun `temporarily left booking shows return sign in instead of sign in`() {
    val booking =
        LibBookBookingDto(
            id = "b1",
            status = "3",
            statusName = "临时离开",
            flagIn = "1",
            flagLeave = "1",
            flagOut = "1",
        )

    assertTrue(booking.canReturnSignIn())
    assertFalse(booking.canSignIn())
    assertTrue(booking.canTemporaryLeave())
    assertTrue(booking.canCheckout())
  }

  @Test
  fun `return sign in is hidden for terminal bookings`() {
    val booking = LibBookBookingDto(id = "b1", status = "8", statusName = "已结束", flagLeave = "1")

    assertFalse(booking.canReturnSignIn())
  }
}
