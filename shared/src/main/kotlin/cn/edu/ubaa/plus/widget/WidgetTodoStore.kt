package cn.edu.ubaa.plus.widget

import cn.edu.ubaa.plus.model.dto.LibBookBookingDto
import cn.edu.ubaa.plus.model.format.parseUbaaDate
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.ui.screens.libbook.libBookHomeStatusLabel
import cn.edu.ubaa.plus.ui.screens.menu.HomeTodoItem
import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class WidgetTodo(val title: String, val detail: String, val due: String?)

@Serializable
internal data class WidgetTodoSnapshot(
    val owner: String,
    val updated: String,
    val items: List<WidgetTodo>,
    val unavailable: List<String> = emptyList(),
)

internal object WidgetTodoStore {
  private var backing: Settings? = null
  var settings: Settings
    get() = backing ?: Settings().also { backing = it }
    set(value) {
      backing = value
    }

  private val json = Json { ignoreUnknownKeys = true }

  fun write(
      owner: String,
      items: List<HomeTodoItem>,
      now: LocalDateTime,
      library: LibBookBookingDto? = null,
      unavailable: List<String> = emptyList(),
  ) {
    if (ScheduleStore.account() != owner) return
    settings.putString(
        "widget_todo_snapshot",
        json.encodeToString(
            WidgetTodoSnapshot(
                owner,
                now.toString(),
                items.map {
                  WidgetTodo(
                      it.title,
                      "${it.source.label} · ${it.timeLabel} · ${it.statusLabel}",
                      it.sortTime?.toString(),
                  )
                } +
                    listOfNotNull(
                        library?.let { booking ->
                          val date = parseUbaaDate(booking.day)
                          val time = parseUbaaTime(booking.endTime)
                          WidgetTodo(
                              "图书馆座位 · ${booking.nameMerge.ifBlank { booking.seatNo }}",
                              "${libBookHomeStatusLabel(booking)} · ${booking.day} ${booking.beginTime}–${booking.endTime}",
                              if (date != null && time != null) LocalDateTime(date, time).toString()
                              else null,
                          )
                        }
                    ),
                unavailable,
            )
        ),
    )
  }

  fun read(owner: String?, now: LocalDateTime): WidgetTodoSnapshot? {
    if (owner == null) return null
    return runCatching {
          json.decodeFromString<WidgetTodoSnapshot>(settings.getString("widget_todo_snapshot", ""))
        }
        .getOrNull()
        ?.takeIf { it.owner == owner }
        ?.let { snapshot ->
          snapshot.copy(
              items =
                  snapshot.items.filter { item ->
                    item.due?.let {
                      runCatching { LocalDateTime.parse(it) >= now }.getOrDefault(false)
                    } ?: true
                  }
          )
        }
  }

  fun clearAccount(owner: String) {
    val snapshot =
        runCatching {
              json.decodeFromString<WidgetTodoSnapshot>(
                  settings.getString("widget_todo_snapshot", "")
              )
            }
            .getOrNull()
    if (snapshot?.owner == owner) clear()
  }

  fun clear() {
    settings.remove("widget_todo_snapshot")
  }
}
