package cn.edu.ubaa.plus.widget

internal data class WidgetAgendaRow(val title: String, val detail: String, val isCourse: Boolean)

internal data class WidgetAgendaPage(
    val rows: List<WidgetAgendaRow>,
    val page: Int,
    val pageCount: Int,
)

/** Courses always precede todos; overflow stays reachable through paging. */
internal fun widgetAgendaPage(
    courses: List<WidgetAgendaRow>,
    todos: List<WidgetAgendaRow>,
    todoOnly: Boolean,
    height: Int,
    requestedPage: Int,
): WidgetAgendaPage {
  val all = if (todoOnly) todos else courses + todos
  val capacity = (height / 64).coerceAtLeast(1)
  val pageCount = ((all.size + capacity - 1) / capacity).coerceAtLeast(1)
  val page = requestedPage.coerceIn(0, pageCount - 1)
  return WidgetAgendaPage(all.drop(page * capacity).take(capacity), page, pageCount)
}
