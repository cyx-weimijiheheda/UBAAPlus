package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.model.dto.*
import cn.edu.ubaa.plus.ui.screens.libbook.*
import com.russhwolf.settings.MapSettings
import kotlin.test.*
import kotlinx.datetime.LocalDateTime

class LibBookLeaveStoreTest {
  private fun time(clock: String) = LocalDateTime.parse("2026-11-17T$clock")

  private val booking =
      LibBookBookingDto(
          "b1",
          day = "2026-11-17",
          endTime = "23:00",
          status = "3",
          statusName = "使用中",
          flagLeave = "1",
          flagOut = "1",
      )

  @BeforeTest
  fun setup() {
    LibBookLeaveStore.settings = MapSettings()
  }

  private fun leave(clock: String) =
      successfulLibBookLeave(booking, LibBookSeatActionResponse(true, "已暂离"), time(clock))

  @Test
  fun deadlineUsesOrdinaryMealAndBookingEndBoundaries() {
    for ((start, end) in
        listOf(
            "09:00" to "09:30",
            "10:30" to "12:30",
            "13:30" to "15:30",
            "13:31" to "14:01",
            "16:30" to "18:30",
            "19:00" to "21:00",
            "19:01" to "19:31",
        )) {
      assertEquals(time(end), calculateLibBookReturnDeadline(booking, time(start)))
    }
    assertEquals(time("23:00"), calculateLibBookReturnDeadline(booking, time("22:50")))
    val official =
        successfulLibBookLeave(
            booking,
            LibBookSeatActionResponse(true, "ok", leaveDeadline = "2026-11-17 09:45:00"),
            time("09:00"),
        )
    assertEquals(time("09:45"), libBookReturnDeadline(official))
    assertFalse(official.leaveDeadlineEstimated)
  }

  @Test
  fun returnedStateOverridesOldFlagsAndReenablesLeave() {
    assertFalse(booking.canReturnSignIn())
    assertTrue(booking.canTemporaryLeave())
    assertEquals(
        listOf(LibBookCardAction.LEAVE, LibBookCardAction.CHECKOUT),
        booking.cardActions().filter(booking::isCardActionEnabled),
    )
  }

  @Test
  fun tenMinuteReminderNeedsFreshTemporaryLeaveAndOnlyNotifiesOnce() {
    val left = leave("09:00")
    LibBookLeaveStore.record("alice", left)
    LibBookLeaveStore.reconcile("alice", listOf(left), time("09:01"))
    assertTrue(LibBookLeaveStore.claimDue("alice", listOf(left), time("09:19")).isEmpty())
    assertEquals(1, LibBookLeaveStore.claimDue("alice", listOf(left), time("09:20")).size)
    assertTrue(LibBookLeaveStore.claimDue("alice", listOf(left), time("09:21")).isEmpty())
  }

  @Test
  fun returnCancelsOldCycleAndAnotherLeaveGetsItsOwnDeadline() {
    val left = leave("09:00")
    LibBookLeaveStore.record("alice", left)
    LibBookLeaveStore.reconcile("alice", listOf(left), time("09:01"))
    val returned = LibBookLeaveStore.reconcile("alice", listOf(booking), time("09:10")).single()
    assertTrue(returned.canTemporaryLeave())
    assertTrue(LibBookLeaveStore.records("alice").isEmpty())
    assertTrue(LibBookLeaveStore.claimDue("alice", listOf(booking), time("09:20")).isEmpty())
    val again = leave("09:15")
    LibBookLeaveStore.record("alice", again)
    LibBookLeaveStore.reconcile("alice", listOf(again), time("09:16"))
    assertEquals(1, LibBookLeaveStore.claimDue("alice", listOf(again), time("09:35")).size)
  }

  @Test
  fun missingOrFailedQueryNeverClaimsReminderAndAccountsAreIsolated() {
    val left = leave("09:00")
    LibBookLeaveStore.record("alice", left)
    assertTrue(LibBookLeaveStore.claimDue("alice", emptyList(), time("09:20")).isEmpty())
    assertFalse(LibBookLeaveStore.records("alice").single().notified)
    assertTrue(LibBookLeaveStore.claimDue("bob", listOf(left), time("09:20")).isEmpty())
    assertTrue(LibBookLeaveStore.claimDue("alice", listOf(left), time("09:30")).isEmpty())
  }

  @Test
  fun successfulLeaveToleratesBriefListPropagationButNeverClaimsFromOldUsingState() {
    val left = leave("09:00")
    LibBookLeaveStore.record("alice", left)
    assertTrue(
        LibBookLeaveStore.reconcile("alice", listOf(booking), time("09:00:02"))
            .single()
            .canReturnSignIn()
    )
    assertTrue(LibBookLeaveStore.claimDue("alice", listOf(booking), time("09:20")).isEmpty())
    assertFalse(
        LibBookLeaveStore.reconcile("alice", listOf(booking), time("09:01"))
            .single()
            .canReturnSignIn()
    )
  }

  @Test
  fun externalLeaveWithoutTimestampDoesNotInventDeadline() {
    val external = booking.copy(status = "7", statusName = "临时离开")
    assertNull(libBookReturnDeadline(external))
    LibBookLeaveStore.reconcile("alice", listOf(external), time("09:20"))
    assertTrue(LibBookLeaveStore.records("alice").isEmpty())
    assertTrue(libBookReturnHint(external, time("09:20"))!!.contains("未提供离馆时间"))
  }
}
