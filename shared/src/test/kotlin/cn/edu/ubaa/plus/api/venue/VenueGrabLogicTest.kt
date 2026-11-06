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
    val oneHour = resolveVenueTargetSlots(template, "19:00", minutes = 60)
    assertEquals(listOf(template[1]), oneHour.slots)
    assertEquals("19:00-20:00", oneHour.rangeLabel)
    assertTrue(oneHour.isComplete)

    val twoHours = resolveVenueTargetSlots(template, "19:00", minutes = 120)
    assertEquals(listOf(template[1], template[2]), twoHours.slots)
    assertEquals("19:00-21:00", twoHours.rangeLabel)
  }

  @Test
  fun `半小时场馆两小时取四个时段`() {
    val halfHourTemplate =
        listOf(
            VenueTimeSlot(1, "10:00", "10:30"),
            VenueTimeSlot(2, "10:30", "11:00"),
            VenueTimeSlot(3, "11:00", "11:30"),
            VenueTimeSlot(4, "11:30", "12:00"),
        )

    val resolution = resolveVenueTargetSlots(halfHourTemplate, "10:00", minutes = 120)

    assertEquals(4, resolution.slots.size)
    assertEquals("10:00-12:00", resolution.rangeLabel)
    assertEquals(120, resolution.spanMinutes)
  }

  @Test
  fun `单个超长时段会提示超过两小时`() {
    val longSlot = listOf(VenueTimeSlot(1, "09:30", "22:00"))

    val resolution = resolveVenueTargetSlots(longSlot, "09:30", minutes = 120)

    assertEquals(1, resolution.slots.size)
    assertEquals(750, resolution.spanMinutes)
    assertTrue(resolution.exceedsTwoHours)
    assertEquals("09:30-22:00", resolution.rangeLabel)
  }

  @Test
  fun `当日剩余时段不足时标记未完成`() {
    val resolution = resolveVenueTargetSlots(template, "20:00", minutes = 120)

    assertEquals(1, resolution.slots.size)
    assertEquals(60, resolution.resolvedMinutes)
    assertTrue(!resolution.isComplete)
  }

  @Test
  fun `未知开始时间返回空`() {
    assertTrue(resolveVenueTargetSlots(template, "07:00", minutes = 60).slots.isEmpty())
  }

  @Test
  fun `可选时长按场馆最小时段推导`() {
    val halfHour = listOf(VenueTimeSlot(1, "10:00", "10:30"), VenueTimeSlot(2, "10:30", "11:00"))
    assertEquals(listOf(30, 60, 90, 120), venueDurationOptions(halfHour))
    assertEquals(listOf(60, 120), venueDurationOptions(template))
    assertEquals(listOf(120), venueDurationOptions(listOf(VenueTimeSlot(1, "11:45", "13:45"))))
    assertEquals(listOf(120), venueDurationOptions(listOf(VenueTimeSlot(1, "09:30", "22:00"))))
  }

  @Test
  fun `时长文案与取值收敛`() {
    assertEquals("30 分钟", venueDurationLabel(30))
    assertEquals("1 小时", venueDurationLabel(60))
    assertEquals("1.5 小时", venueDurationLabel(90))
    assertEquals("2 小时", venueDurationLabel(120))
    assertEquals("12.5 小时", venueDurationLabel(750))

    val halfHour = listOf(VenueTimeSlot(1, "10:00", "10:30"))
    assertEquals(60, coerceVenueDuration(60, halfHour))
    assertEquals(60, coerceVenueDuration(0, template))
    assertEquals(120, coerceVenueDuration(200, template))
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
