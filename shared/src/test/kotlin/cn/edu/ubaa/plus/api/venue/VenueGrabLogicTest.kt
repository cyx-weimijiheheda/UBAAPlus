package cn.edu.ubaa.plus.api.venue

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VenueGrabLogicTest {
  private val template =
      listOf(
          VenueTimeSlot(1, "18:00", "19:00"),
          VenueTimeSlot(2, "19:00", "20:00"),
          VenueTimeSlot(3, "20:00", "21:00"),
      )

  @Test
  fun `按时长推导目标时段`() {
    assertEquals(
        listOf(template[1]),
        resolveVenueTargetSlots(template, "19:00", hours = 1),
    )
    assertEquals(
        listOf(template[1], template[2]),
        resolveVenueTargetSlots(template, "19:00", hours = 2),
    )
  }

  @Test
  fun `时长超过两个时段时最多取两个`() {
    assertEquals(2, resolveVenueTargetSlots(template, "18:00", hours = 2).size)
  }

  @Test
  fun `未知开始时间返回空`() {
    assertTrue(resolveVenueTargetSlots(template, "07:00", hours = 1).isEmpty())
  }

  @Test
  fun `指定场地可用时使用指定场地`() {
    val day = dayInfo(freeSpaceIds = setOf(10, 11))
    val selection =
        pickVenueSelection(
            dayInfo = day,
            targetSlots = listOf(template[1]),
            preferredSpaceByBegin = mapOf("19:00" to 11),
            fallbackAnySpace = false,
            randomSpace = false,
            random = Random(0),
        )

    assertEquals(11, selection?.single()?.spaceId)
  }

  @Test
  fun `指定场地不可用且不兜底时返回空`() {
    val day = dayInfo(freeSpaceIds = setOf(10))
    val selection =
        pickVenueSelection(
            dayInfo = day,
            targetSlots = listOf(template[1]),
            preferredSpaceByBegin = mapOf("19:00" to 99),
            fallbackAnySpace = false,
            randomSpace = false,
            random = Random(0),
        )

    assertNull(selection)
  }

  @Test
  fun `指定场地不可用时可以兜底到其它空闲场地`() {
    val day = dayInfo(freeSpaceIds = setOf(10))
    val selection =
        pickVenueSelection(
            dayInfo = day,
            targetSlots = listOf(template[1]),
            preferredSpaceByBegin = mapOf("19:00" to 99),
            fallbackAnySpace = true,
            randomSpace = false,
            random = Random(0),
        )

    assertEquals(10, selection?.single()?.spaceId)
  }

  @Test
  fun `连续时段优先复用同一场地`() {
    val day = dayInfo(freeSpaceIds = setOf(10, 11))
    val selection =
        pickVenueSelection(
            dayInfo = day,
            targetSlots = listOf(template[1], template[2]),
            preferredSpaceByBegin = emptyMap(),
            fallbackAnySpace = true,
            randomSpace = true,
            random = Random(1),
        )

    assertEquals(2, selection?.size)
    assertEquals(selection?.get(0)?.spaceId, selection?.get(1)?.spaceId)
  }

  @Test
  fun `任意时段没有空位时返回空`() {
    val day = dayInfo(freeSpaceIds = emptySet())
    assertNull(
        pickVenueSelection(
            dayInfo = day,
            targetSlots = listOf(template[1]),
            preferredSpaceByBegin = emptyMap(),
            fallbackAnySpace = true,
            randomSpace = false,
            random = Random(0),
        )
    )
  }

  @Test
  fun `错误分类与 Python 客户端一致`() {
    assertEquals(VenueFailureKind.RETRY, classifyVenueFailure("该场地已被预约", null))
    assertEquals(VenueFailureKind.RETRY, classifyVenueFailure("当前预约人数过多", null))
    assertEquals(VenueFailureKind.NOT_RELEASED, classifyVenueFailure("最多只能查看3天", null))
    assertEquals(VenueFailureKind.CAPTCHA_RATE_LIMITED, classifyVenueFailure("验证码次数超出", null))
    assertEquals(VenueFailureKind.CAPTCHA_INVALID, classifyVenueFailure("验证失败", null))
    assertEquals(VenueFailureKind.AUTH_EXPIRED, classifyVenueFailure("x", "venue_unauthenticated"))
    assertEquals(VenueFailureKind.STOP, classifyVenueFailure("每天只能预约2次", null))
  }

  private fun dayInfo(freeSpaceIds: Set<Int>): VenueDayInfo {
    val spaces =
        (10..11).map { spaceId ->
          VenueSpace(
              spaceId = spaceId,
              spaceName = "${spaceId}号",
              groupId = null,
              slots =
                  template.associate { slot ->
                    slot.timeId to
                        VenueSpaceSlot(
                            status =
                                if (spaceId in freeSpaceIds) VenueSpaceSlot.STATUS_FREE
                                else VenueSpaceSlot.STATUS_RESERVED,
                            fee = null,
                            remark = null,
                        )
                  },
          )
        }
    return VenueDayInfo(
        siteId = 1,
        date = "2026-09-26",
        orderToken = "token",
        isFee = false,
        slots = template,
        spaces = spaces,
        phone = null,
        isCaptchaCheck = false,
        buddies = emptyList(),
        buddyNumMin = 0,
        buddyNumMax = 3,
        reservationHour = 1,
    )
  }
}
