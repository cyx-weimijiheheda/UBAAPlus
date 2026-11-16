package cn.edu.ubaa.plus.ui

import cn.edu.ubaa.plus.widget.*
import kotlin.test.*

class WidgetAgendaPageTest {
  private val courses = (1..2).map { WidgetAgendaRow("课程$it", "时间与地点", true) }
  private val todos = (1..3).map { WidgetAgendaRow("待办$it", "详情", false) }

  @Test
  fun coursesStayAheadAndEveryItemRemainsReachable() {
    val first = widgetAgendaPage(courses, todos, false, 256, 0)
    assertEquals(listOf("课程1", "课程2", "待办1", "待办2"), first.rows.map { it.title })
    val second = widgetAgendaPage(courses, todos, false, 256, 1)
    assertEquals(listOf("待办3"), second.rows.map { it.title })
    assertEquals(2, first.pageCount)
  }

  @Test
  fun smallWidgetsPageWithoutLosingCoursesAndModeSwitchFiltersTodos() {
    val all = (0..4).flatMap { widgetAgendaPage(courses, todos, false, 64, it).rows }
    assertEquals(courses + todos, all)
    assertEquals(todos, widgetAgendaPage(courses, todos, true, 256, 0).rows)
    assertEquals(0, widgetAgendaPage(emptyList(), emptyList(), false, 1, 99).page)
    assertEquals(1, widgetAgendaPage(emptyList(), emptyList(), false, 1, 99).pageCount)
  }
}
