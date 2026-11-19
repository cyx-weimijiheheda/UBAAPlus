package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.model.dto.CgyyTimeSlotDto
import cn.edu.ubaa.plus.ui.common.components.*
import cn.edu.ubaa.plus.ui.screens.cgyy.cgyyTimeBreaks
import cn.edu.ubaa.plus.ui.screens.classroom.classroomFreeSections
import kotlin.test.*

class AvailabilityTimelineTest {
  @Test
  fun `连续空闲合并且占用节次保留原位置`() {
    val free = classroomFreeSections("1, 2,2, 5,6, 14,0,15,bad")
    assertEquals(setOf(1, 2, 5, 6, 14), free)
    val runs =
        availabilityRuns(
            (1..14).map {
              if (it in free) AvailabilityTone.AVAILABLE else AvailabilityTone.UNAVAILABLE
            }
        )
    assertEquals(listOf(2, 2, 2, 7, 1), runs.map { it.length })
    assertEquals(
        listOf(
            AvailabilityTone.AVAILABLE,
            AvailabilityTone.UNAVAILABLE,
            AvailabilityTone.AVAILABLE,
            AvailabilityTone.UNAVAILABLE,
            AvailabilityTone.AVAILABLE,
        ),
        runs.map { it.tone },
    )
    assertEquals(14, runs.sumOf { it.length })
    assertTrue(classroomFreeSections("").isEmpty())
  }

  @Test
  fun `已选时段独立着色且未提供的数据不推断可用`() {
    assertEquals(
        listOf(
            AvailabilityRun(AvailabilityTone.AVAILABLE, 1),
            AvailabilityRun(AvailabilityTone.SELECTED, 2),
            AvailabilityRun(AvailabilityTone.UNAVAILABLE, 1),
        ),
        availabilityRuns(
            listOf(
                AvailabilityTone.AVAILABLE,
                AvailabilityTone.SELECTED,
                AvailabilityTone.SELECTED,
                AvailabilityTone.UNAVAILABLE,
            )
        ),
    )
    assertTrue(availabilityRuns(emptyList()).isEmpty())
  }

  @Test
  fun `研讨室跨真实时间间隔不画成连续预约条`() {
    val slots =
        listOf(
            CgyyTimeSlotDto(8, "08:00", "10:00", ""),
            CgyyTimeSlotDto(23, "10:00", "12:00", ""),
            CgyyTimeSlotDto(42, "14:00", "16:00", ""),
            CgyyTimeSlotDto(99, "", "", ""),
        )
    val breaks = cgyyTimeBreaks(slots)
    assertEquals(setOf(2, 3), breaks)
    assertEquals(
        listOf(2, 1, 1),
        availabilityRuns(List(4) { AvailabilityTone.AVAILABLE }, breaks).map { it.length },
    )
  }
}
