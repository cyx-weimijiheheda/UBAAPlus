package cn.edu.ubaa.plus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import cn.edu.ubaa.plus.model.format.formatUbaaCompactDate
import cn.edu.ubaa.plus.model.format.formatUbaaCompactDateTime
import cn.edu.ubaa.plus.model.format.formatUbaaMonthDay
import cn.edu.ubaa.plus.model.format.formatUbaaWeekdayLabel
import cn.edu.ubaa.plus.repository.ScheduleStore
import cn.edu.ubaa.plus.repository.selectSavedScheduleWeek
import cn.edu.ubaa.plus.shared.R
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

open class ScheduleWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    ids.forEach { update(context, manager, it) }
  }

  override fun onAppWidgetOptionsChanged(
      context: Context,
      manager: AppWidgetManager,
      id: Int,
      options: Bundle,
  ) {
    update(context, manager, id)
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH) refreshAll(context)
    if (intent.action == ACTION_WEEK) {
      val manager = AppWidgetManager.getInstance(context)
      val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
      if (providerClasses.any { it.name == manager.getAppWidgetInfo(id)?.provider?.className }) {
        update(context, manager, id, intent.getIntExtra("step", 0))
      }
    }
  }

  override fun onDeleted(context: Context, ids: IntArray) {
    val editor = context.getSharedPreferences("schedule_widgets", Context.MODE_PRIVATE).edit()
    ids.forEach { id ->
      listOf("owner", "anchor", "term", "week", "page", "todo", "agenda-owner").forEach {
        editor.remove("$id-$it")
      }
    }
    editor.apply()
  }

  companion object {
    private val providerClasses =
        listOf(
            ScheduleWidgetProvider::class.java,
            TodayScheduleWidgetProvider::class.java,
            UpcomingScheduleWidgetProvider::class.java,
            TodoWidgetProvider::class.java,
        )
    private const val ACTION_WEEK = "cn.edu.ubaa.plus.widget.WEEK"
    private const val ACTION_REFRESH = "cn.edu.ubaa.plus.widget.REFRESH"

    fun requestRefresh(context: Context) {
      context.sendBroadcast(
          Intent(context, ScheduleWidgetProvider::class.java).setAction(ACTION_REFRESH)
      )
    }

    private fun refreshAll(context: Context) {
      val manager = AppWidgetManager.getInstance(context)
      providerClasses.forEach { provider ->
        manager.getAppWidgetIds(ComponentName(context, provider)).forEach {
          update(context, manager, it)
        }
      }
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int, step: Int? = null) {
      val provider = manager.getAppWidgetInfo(id)?.provider ?: return
      val agendaDays =
          when (provider.className) {
            TodayScheduleWidgetProvider::class.java.name -> 1
            UpcomingScheduleWidgetProvider::class.java.name -> 3
            else -> 0
          }
      val preferences = context.getSharedPreferences("schedule_widgets", Context.MODE_PRIVATE)
      val today = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai")).date
      val anchor = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY).toString()
      val owner = ScheduleStore.account()
      val keepSelection =
          agendaDays == 0 &&
              step != 0 &&
              preferences.getString("$id-owner", null) == owner &&
              preferences.getString("$id-anchor", null) == anchor
      val result = runCatching {
        val semesters = owner?.let(ScheduleStore::read).orEmpty()
        val selected =
            selectSavedScheduleWeek(
                semesters,
                today,
                if (keepSelection) preferences.getString("$id-term", null) else null,
                if (keepSelection) preferences.getInt("$id-week", -1) else null,
            )
        if (selected != null && agendaDays == 0 && step in listOf(-1, 1)) {
          val weeks = selected.semester.weeks.sortedBy { it.serialNumber }
          val index = weeks.indexOf(selected.week)
          selected.copy(week = weeks.getOrNull(index + step!!) ?: selected.week)
        } else selected
      }
      val selected = result.getOrNull()
      val views = RemoteViews(context.packageName, R.layout.schedule_widget)
      val palette = widgetPalette(context)
      val widgetOptions = manager.getAppWidgetOptions(id)
      val widgetWidth =
          widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceIn(110, 700)
      val widgetHeight =
          widgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400).coerceIn(100, 900)
      views.setImageViewBitmap(
          R.id.widget_background,
          widgetBackground(widgetWidth, widgetHeight, palette),
      )
      listOf(R.id.widget_title, R.id.widget_message, R.id.widget_footer).forEach {
        views.setTextColor(it, palette.text)
      }
      listOf(R.id.widget_previous, R.id.widget_next, R.id.widget_current).forEach {
        views.setTextColor(it, palette.accent)
      }

      val open =
          if (selected != null)
              Intent(context, ScheduleWidgetActivity::class.java)
                  .putExtra("term", selected.semester.termCode)
                  .putExtra("week", selected.week.serialNumber)
                  .putExtra("owner", owner)
          else context.packageManager.getLaunchIntentForPackage(context.packageName)!!
      views.setOnClickPendingIntent(
          R.id.widget_root,
          PendingIntent.getActivity(
              context,
              id,
              open,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          ),
      )
      listOf(R.id.widget_previous to -1, R.id.widget_next to 1, R.id.widget_current to 0).forEach {
          (view, delta) ->
        val intent =
            Intent()
                .setComponent(provider)
                .setAction(ACTION_WEEK)
                .setData(android.net.Uri.parse("ubaa-widget://week/$id/$delta"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra("step", delta)
        views.setOnClickPendingIntent(
            view,
            PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
      }
      views.setViewVisibility(
          R.id.widget_previous,
          if (agendaDays == 0) View.VISIBLE else View.GONE,
      )
      views.setViewVisibility(R.id.widget_next, if (agendaDays == 0) View.VISIBLE else View.GONE)
      views.setViewVisibility(R.id.widget_current, if (agendaDays == 0) View.VISIBLE else View.GONE)
      if (selected == null) {
        // 桌面可能复用同一布局；注销时必须显式清除上一账号的图片与文字。
        views.setViewVisibility(R.id.widget_grid, View.GONE)
        views.setImageViewBitmap(R.id.widget_grid, null)
        views.setViewVisibility(R.id.widget_message, View.VISIBLE)
        views.setTextViewText(R.id.widget_title, "UBAAPlus 周课表")
        views.setTextViewText(R.id.widget_footer, "本地课表 · 点击打开应用")
        views.setContentDescription(R.id.widget_grid, "暂无本地课表")
        views.setBoolean(R.id.widget_previous, "setEnabled", false)
        views.setBoolean(R.id.widget_next, "setEnabled", false)
        views.setTextViewText(
            R.id.widget_message,
            if (result.isFailure) "本地课表读取失败，请打开应用重新更新"
            else if (owner == null) "请先打开 UBAAPlus，登录并本地化课表" else "暂无已安排课表，请打开应用本地化课表",
        )
      } else {
        preferences
            .edit()
            .putString("$id-owner", owner)
            .putString("$id-anchor", anchor)
            .putString("$id-term", selected.semester.termCode)
            .putInt("$id-week", selected.week.serialNumber)
            .apply()
        val week = selected.week
        val schedule = selected.semester.schedules[week.serialNumber]
        val options = manager.getAppWidgetOptions(id)
        val width =
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceIn(110, 700) - 16
        val height =
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 400).coerceIn(100, 900) -
                64
        if (agendaDays == 0 && width < 220) views.setViewVisibility(R.id.widget_current, View.GONE)
        views.setTextViewText(
            R.id.widget_title,
            if (agendaDays == 1) "今日课程 · ${formatUbaaCompactDate(today.toString())}"
            else if (agendaDays == 3) "近日课程 · 今天起三天"
            else "${week.name} · ${formatUbaaMonthDay(week.startDate)}",
        )
        views.setTextViewText(
            R.id.widget_footer,
            "本地课表 · 更新于 ${formatUbaaCompactDateTime(selected.semester.updatedAt)}",
        )
        views.setViewVisibility(R.id.widget_message, View.GONE)
        views.setViewVisibility(R.id.widget_grid, View.VISIBLE)
        views.setImageViewBitmap(
            R.id.widget_grid,
            if (agendaDays == 0) renderScheduleWidget(week, schedule, width, height, palette)
            else
                renderAgendaWidget(
                    cn.edu.ubaa.plus.repository.savedAgenda(
                        owner?.let(ScheduleStore::read).orEmpty(),
                        today,
                        agendaDays,
                    ),
                    width,
                    height,
                    palette,
                ),
        )
        views.setContentDescription(
            R.id.widget_grid,
            buildString {
              append("${week.name}，点击打开周课表。")
              schedule
                  ?.arrangedList
                  ?.sortedWith(compareBy({ it.dayOfWeek }, { it.beginSection }))
                  ?.forEach {
                    append(
                        "${it.dayOfWeek?.let(::formatUbaaWeekdayLabel).orEmpty()} ${it.beginTime} ${it.courseName} ${it.placeName.orEmpty()}；"
                    )
                  }
            },
        )
        val weeks = selected.semester.weeks.sortedBy { it.serialNumber }
        views.setBoolean(R.id.widget_previous, "setEnabled", week != weeks.first())
        views.setBoolean(R.id.widget_next, "setEnabled", week != weeks.last())
      }
      val todoOnly = provider.className == TodoWidgetProvider::class.java.name
      if (agendaDays == 1 || todoOnly) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.of("Asia/Shanghai"))
        val snapshot = WidgetTodoStore.read(owner, now)
        val agenda =
            if (todoOnly) emptyList()
            else
                runCatching {
                      cn.edu.ubaa.plus.repository.savedAgenda(
                          owner?.let(ScheduleStore::read).orEmpty(),
                          today,
                          1,
                      )
                    }
                    .getOrDefault(emptyList())
        val showingTodo =
            todoOnly ||
                if (step == 0) !preferences.getBoolean("$id-todo", false)
                else preferences.getBoolean("$id-todo", false)
        val courseRows =
            agenda.map {
              WidgetAgendaRow(
                  it.course.courseName,
                  "${it.course.beginTime.orEmpty()}–${it.course.endTime.orEmpty()}\n${it.course.placeName.orEmpty()}",
                  true,
              )
            }
        val todoRows = snapshot?.items.orEmpty().map { WidgetAgendaRow(it.title, it.detail, false) }
        val rows = if (showingTodo) todoRows else courseRows + todoRows
        val emptyHint =
            if (showingTodo) {
              if (snapshot == null) "打开首页同步待办"
              else if (snapshot.unavailable.isNotEmpty()) "部分来源未更新 · 打开首页重试" else "暂无待办"
            } else "暂无课程与待办 · 打开首页同步"
        val pageHeight = widgetHeight - 64
        val previousPage =
            if (preferences.getString("$id-agenda-owner", null) == owner && step != 0)
                preferences.getInt("$id-page", 0)
            else 0
        val page =
            widgetAgendaPage(
                courseRows,
                todoRows,
                showingTodo,
                pageHeight,
                previousPage + if (step == -1 || step == 1) step else 0,
            )
        preferences
            .edit()
            .putInt("$id-page", page.page)
            .putBoolean("$id-todo", showingTodo)
            .putString("$id-agenda-owner", owner)
            .apply()
        val rendered =
            if (rows.isEmpty())
                page.copy(
                    rows =
                        listOf(
                            WidgetAgendaRow(
                                emptyHint,
                                if (showingTodo) "点击打开应用" else "先本地化课表，再更新待办",
                                false,
                            )
                        )
                )
            else page
        views.setViewVisibility(R.id.widget_grid, View.VISIBLE)
        views.setViewVisibility(R.id.widget_message, View.GONE)
        views.setImageViewBitmap(
            R.id.widget_grid,
            renderWidgetAgenda(rendered, widgetWidth - 16, pageHeight, palette),
        )
        views.setTextViewText(
            R.id.widget_title,
            (if (showingTodo) "待办 · ${todoRows.size} 项" else "今日课表 · ${courseRows.size} 门") +
                "  ${page.page + 1}/${page.pageCount}",
        )
        views.setTextColor(R.id.widget_title, palette.accent)
        views.setTextViewText(
            R.id.widget_footer,
            snapshot?.let { "快照 ${formatUbaaCompactDateTime(it.updated)} · 打开应用更新" } ?: "点击打开应用同步",
        )
        views.setContentDescription(
            R.id.widget_grid,
            rows.joinToString("；") { "${it.title} ${it.detail}" },
        )
        listOf(R.id.widget_previous, R.id.widget_next).forEach {
          views.setViewVisibility(it, View.VISIBLE)
        }
        views.setBoolean(R.id.widget_previous, "setEnabled", page.page > 0)
        views.setBoolean(R.id.widget_next, "setEnabled", page.page + 1 < page.pageCount)
        views.setFloat(R.id.widget_previous, "setAlpha", if (page.page > 0) 1f else 0.35f)
        views.setFloat(
            R.id.widget_next,
            "setAlpha",
            if (page.page + 1 < page.pageCount) 1f else 0.35f,
        )
        views.setContentDescription(R.id.widget_previous, "上一页")
        views.setContentDescription(R.id.widget_next, "下一页")
        views.setViewVisibility(R.id.widget_current, if (todoOnly) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_current, if (showingTodo) "课表" else "待办")
        views.setContentDescription(R.id.widget_current, if (showingTodo) "显示课表与待办" else "只显示待办")
        views.setBoolean(R.id.widget_current, "setEnabled", true)
        val appIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                id,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
      }
      manager.updateAppWidget(id, views)
    }
  }
}

class TodayScheduleWidgetProvider : ScheduleWidgetProvider()

class UpcomingScheduleWidgetProvider : ScheduleWidgetProvider()

class TodoWidgetProvider : ScheduleWidgetProvider()
