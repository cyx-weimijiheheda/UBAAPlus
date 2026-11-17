package cn.edu.ubaa.plus.reminder

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.dto.canReturnSignIn
import cn.edu.ubaa.plus.model.format.parseUbaaDateTime
import cn.edu.ubaa.plus.ui.screens.libbook.LIB_BOOK_ZONE
import cn.edu.ubaa.plus.ui.screens.libbook.libBookReturnDeadline
import com.russhwolf.settings.Settings
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class LibBookLeaveRecord(
    val booking: LibBookBookingDto,
    val confirmed: Boolean = false,
    val notified: Boolean = false,
)

/** 按账号和预约保存本次暂离；一次暂离结束后删除，下一次从新离开时刻重新计时。 */
internal object LibBookLeaveStore {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: Settings().also { backing = it }
    set(value) {
      backing = value
    }

  private val json = Json { ignoreUnknownKeys = true }

  private fun key(owner: String) = "libbook_leaves:$owner"

  @Synchronized
  fun records(owner: String): List<LibBookLeaveRecord> =
      runCatching {
            json.decodeFromString<List<LibBookLeaveRecord>>(settings.getString(key(owner), "[]"))
          }
          .getOrDefault(emptyList())

  private fun write(owner: String, records: List<LibBookLeaveRecord>) {
    settings.putString(key(owner), json.encodeToString(records))
  }

  @Synchronized
  fun record(owner: String, booking: LibBookBookingDto) {
    write(
        owner,
        records(owner).filter { it.booking.id != booking.id } + LibBookLeaveRecord(booking),
    )
  }

  @Synchronized
  fun remove(owner: String, id: String) {
    write(owner, records(owner).filter { it.booking.id != id })
  }

  @Synchronized
  fun clear() {
    settings.keys.filter { it.startsWith("libbook_leaves:") }.forEach(settings::remove)
  }

  @Synchronized
  fun reconcile(
      owner: String,
      bookings: List<LibBookBookingDto>,
      now: LocalDateTime,
  ): List<LibBookBookingDto> {
    val saved = records(owner).associateBy { it.booking.id }.toMutableMap()
    val result =
        bookings.map { booking ->
          val prior = saved[booking.id]
          when {
            booking.canReturnSignIn() -> {
              val upstreamStart = parseUbaaDateTime(booking.leaveStartedAt)
              val previousStart = prior?.booking?.leaveStartedAt?.let(::parseUbaaDateTime)
              val newCycle =
                  upstreamStart != null && previousStart != null && upstreamStart != previousStart
              val enriched =
                  booking.copy(
                      leaveStartedAt =
                          booking.leaveStartedAt.ifBlank {
                            prior?.booking?.leaveStartedAt.orEmpty()
                          },
                      leaveDeadline =
                          booking.leaveDeadline.ifBlank {
                            if (newCycle) "" else prior?.booking?.leaveDeadline.orEmpty()
                          },
                      leaveDeadlineEstimated =
                          if (parseUbaaDateTime(booking.leaveDeadline) != null) false
                          else if (newCycle) true
                          else prior?.booking?.leaveDeadlineEstimated ?: true,
                  )
              if (libBookReturnDeadline(enriched) != null)
                  saved[booking.id] =
                      LibBookLeaveRecord(
                          enriched,
                          confirmed = true,
                          notified = if (newCycle) false else prior?.notified ?: false,
                      )
              enriched
            }
            // Allow the list endpoint a brief propagation delay after a successful leave action.
            prior != null &&
                !prior.confirmed &&
                parseUbaaDateTime(prior.booking.leaveStartedAt)?.let {
                  val elapsed = now.toInstant(LIB_BOOK_ZONE) - it.toInstant(LIB_BOOK_ZONE)
                  elapsed >= kotlin.time.Duration.ZERO && elapsed < 30.seconds
                } == true -> prior.booking
            else -> {
              saved.remove(booking.id)
              booking
            }
          }
        }
    write(owner, saved.values.toList())
    return result
  }

  /**
   * Must be called with a successful, fresh server response. Missing/returned records never notify.
   */
  @Synchronized
  fun claimDue(
      owner: String,
      fresh: List<LibBookBookingDto>,
      now: LocalDateTime,
  ): List<LibBookBookingDto> {
    val eligible = fresh.filter { it.canReturnSignIn() }.map { it.id }.toSet()
    val due = mutableListOf<LibBookBookingDto>()
    val updated =
        records(owner).map { record ->
          val deadline = libBookReturnDeadline(record.booking)
          val remaining =
              deadline?.let { it.toInstant(LIB_BOOK_ZONE) - now.toInstant(LIB_BOOK_ZONE) }
          if (
              !record.notified &&
                  record.booking.id in eligible &&
                  remaining != null &&
                  remaining > kotlin.time.Duration.ZERO &&
                  remaining <= 10.minutes
          ) {
            due += record.booking
            record.copy(notified = true)
          } else record
        }
    write(owner, updated)
    return due
  }
}
