package cn.edu.ubaa.plus.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class ReminderEventTest {

  @Test
  fun `补发窗口按类型区分`() {
    assertEquals(5.minutes, ReminderEventKind.COURSE_OPEN.catchUpWindow())
    assertEquals(20.minutes, ReminderEventKind.ICLASS_SIGN_IN.catchUpWindow())
    assertEquals(20.minutes, ReminderEventKind.BYKC_SIGN_IN.catchUpWindow())
    assertEquals(20.minutes, ReminderEventKind.BYKC_SIGN_OUT.catchUpWindow())
  }
}
