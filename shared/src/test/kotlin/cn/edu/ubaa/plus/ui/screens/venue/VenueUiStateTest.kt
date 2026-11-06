package cn.edu.ubaa.plus.ui.screens.venue

import cn.edu.ubaa.plus.api.venue.VenueDayInfo
import cn.edu.ubaa.plus.api.venue.VenueSpace
import cn.edu.ubaa.plus.api.venue.VenueSpaceMode
import cn.edu.ubaa.plus.api.venue.VenueSpaceSlot
import cn.edu.ubaa.plus.api.venue.VenueTimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenueUiStateTest {

  private val slots =
      listOf(
          VenueTimeSlot(1, "07:00", "08:00"),
          VenueTimeSlot(2, "08:00", "09:00"),
          VenueTimeSlot(3, "09:00", "10:00"),
      )

  private val day =
      VenueDayInfo(
          siteId = 1,
          date = "2026-10-18",
          orderToken = "token",
          isFee = false,
          slots = slots,
          spaces =
              listOf(
                  VenueSpace(
                      spaceId = 10,
                      spaceName = "1号场",
                      groupId = null,
                      slots =
                          mapOf(
                              1 to VenueSpaceSlot(VenueSpaceSlot.STATUS_FREE, null, null),
                              2 to VenueSpaceSlot(VenueSpaceSlot.STATUS_RESERVED, null, null),
                              3 to VenueSpaceSlot(VenueSpaceSlot.STATUS_DISABLED, null, null),
                          ),
                  ),
                  VenueSpace(
                      spaceId = 11,
                      spaceName = "2号场",
                      groupId = null,
                      slots =
                          mapOf(
                              1 to VenueSpaceSlot(VenueSpaceSlot.STATUS_RESERVED, null, null),
                              2 to VenueSpaceSlot(VenueSpaceSlot.STATUS_FREE, null, null),
                              3 to VenueSpaceSlot(VenueSpaceSlot.STATUS_DISABLED, null, null),
                          ),
                  ),
              ),
          phone = null,
          isCaptchaCheck = false,
          buddies = emptyList(),
          buddyNumMin = 0,
          buddyNumMax = 3,
          reservationHour = 2,
      )

  @Test
  fun `下拉框只列出至少有一个空闲场地的时段`() {
    val state = VenueUiState(dayInfo = day)

    assertEquals(listOf(1, 2), state.selectableSlots.map { it.timeId })
  }

  @Test
  fun `未放票时回退到时段模板`() {
    val state = VenueUiState(dayInfo = null, slotTemplate = slots)

    assertEquals(slots, state.selectableSlots)
  }

  @Test
  fun `目标时段不可用时保留选择并附加提示`() {
    val state =
        VenueUiState(
            dayInfo = day,
            selectedStartBegin = "09:00",
        )

    assertTrue(state.selectedSlotLabel.contains("09:00-10:00"))
    assertTrue(state.selectedSlotLabel.contains("当前无空闲"))
  }

  @Test
  fun `两小时时长要求整段都有空闲场地`() {
    val state = VenueUiState(dayInfo = day, durationMinutes = 120)

    assertEquals(listOf(1), state.selectableSlots.map { it.timeId })
    assertEquals("07:00-09:00", state.selectedTargetRangeLabelIfSelected("07:00"))
  }

  @Test
  fun `指定场地不兜底时要求该场地整段空闲`() {
    val state =
        VenueUiState(
            dayInfo = day,
            durationMinutes = 120,
            selectedStartBegin = "07:00",
            selectedSpaceId = 10,
            spaceMode = VenueSpaceMode.FIXED,
            fallbackAnySpace = false,
        )

    assertTrue(state.selectableSlots.isEmpty())
  }

  @Test
  fun `超长单时段给出跨度提示`() {
    val longDay = day.copy(slots = listOf(VenueTimeSlot(9, "09:30", "22:00")))
    val state = VenueUiState(dayInfo = longDay, durationMinutes = 120, selectedStartBegin = "09:30")

    assertEquals("09:30-22:00", state.selectedTargetRangeLabel)
    assertTrue(state.targetSpanWarning?.contains("超过连续 2 小时") == true)
  }

  private fun VenueUiState.selectedTargetRangeLabelIfSelected(start: String): String {
    val withStart = copy(selectedStartBegin = start)
    return withStart.selectedTargetRangeLabel
  }
}
