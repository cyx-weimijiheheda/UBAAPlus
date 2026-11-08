package cn.edu.ubaa.plus.ui.screens.libbook

import cn.edu.ubaa.plus.model.dto.LibBookQrScanResultDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibBookScanStatusTest {

  private fun scan(
      code: String = "",
      status: String = "",
      statusName: String = "",
      message: String = "",
  ) =
      LibBookQrScanResultDto(
          bookId = "b1",
          seatId = "s1",
          seatNo = "A12",
          areaName = "三层 A区",
          day = "2026-11-06",
          beginTime = "14:00",
          endTime = "16:00",
          status = status,
          statusName = statusName,
          code = code,
          message = message,
      )

  @Test
  fun `返回码 1 视为已签到落座`() {
    assertTrue(scan(code = "1").qrScanLanded())
    assertEquals("已签到落座", scan(code = "1").scanResultMessage())
  }

  @Test
  fun `上游文案优先于码表`() {
    assertEquals("签到成功", scan(code = "1", message = "签到成功").scanResultMessage())
  }

  @Test
  fun `已知码有明确解释`() {
    assertEquals("当前不在可签到时间段", scan(code = "3").scanResultMessage())
    assertEquals("座位状态异常，请到座位屏确认", scan(code = "2").scanResultMessage())
  }

  @Test
  fun `未知码不假装理解而是提示去确认`() {
    val message = scan(code = "9").scanResultMessage()
    assertTrue(message.contains("9"))
    assertTrue(message.contains("未确认"))
    assertFalse(scan(code = "9").qrScanLanded())
  }

  @Test
  fun `完全没有状态信息时给出兜底文案`() {
    assertTrue(scan().scanResultMessage().contains("我的预约"))
  }

  @Test
  fun `使用中状态名也算已落座`() {
    assertTrue(scan(statusName = "使用中").qrScanLanded())
  }

  @Test
  fun `扫码结果能转成通知用的预约快照`() {
    val snapshot = scan(code = "1").toBookingSnapshot()
    assertEquals("A12", snapshot.seatNo)
    assertEquals("14:00", snapshot.beginTime)
    assertEquals("2026-11-06", snapshot.day)
    assertTrue(snapshot.statusName.isNotBlank())
  }
}
