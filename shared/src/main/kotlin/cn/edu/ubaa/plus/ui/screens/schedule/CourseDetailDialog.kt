package cn.edu.ubaa.plus.ui.screens.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.CourseClass
import cn.edu.ubaa.plus.model.dto.TodayClass
import cn.edu.ubaa.plus.model.format.formatUbaaDate
import cn.edu.ubaa.plus.model.format.formatUbaaTime
import cn.edu.ubaa.plus.model.format.formatUbaaWeekdayLabel
import cn.edu.ubaa.plus.model.format.parseUbaaDate
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import cn.edu.ubaa.plus.ui.common.components.UbaaToast
import cn.edu.ubaa.plus.ui.common.util.addCourseToCalendar
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class CourseDetailInfo(
    val courseName: String,
    val courseCode: String? = null,
    val courseSerialNo: String? = null,
    val credit: String? = null,
    val placeName: String? = null,
    val beginTime: String? = null,
    val endTime: String? = null,
    val beginSection: Int? = null,
    val endSection: Int? = null,
    val dayOfWeek: Int? = null,
    val weeksAndTeachers: String? = null,
    val teachingTarget: String? = null,
    val date: LocalDate? = null,
)

fun CourseClass.toCourseDetailInfo(weekStartDate: String?): CourseDetailInfo {
  val weekStart = parseUbaaDate(weekStartDate)
  val date = weekStart?.plus(DatePeriod(days = ((dayOfWeek ?: 1) - 1).coerceIn(0, 6)))
  return CourseDetailInfo(
      courseName = courseName,
      courseCode = courseCode,
      courseSerialNo = courseSerialNo,
      credit = credit,
      placeName = placeName,
      beginTime = beginTime,
      endTime = endTime,
      beginSection = beginSection,
      endSection = endSection,
      dayOfWeek = dayOfWeek,
      weeksAndTeachers = weeksAndTeachers,
      teachingTarget = teachingTarget,
      date = date,
  )
}

/**
 * 今日课程的真实名称。
 *
 * 上游 `shortName` 有时只返回“课”这类占位词，优先用 `bizName`；`bizName` 也是占位词时才回退。
 */
fun TodayClass.displayCourseName(): String {
  val biz = bizName.trim()
  val short = shortName?.trim().orEmpty()
  return when {
    biz.isNotEmpty() && biz !in GENERIC_TODAY_CLASS_NAMES -> biz
    short.isNotEmpty() -> short
    else -> biz.ifEmpty { "未命名课程" }
  }
}

private val GENERIC_TODAY_CLASS_NAMES = setOf("课", "课程", "课程安排", "教学班", "未命名课程")

fun TodayClass.toCourseDetailInfo(date: LocalDate): CourseDetailInfo {
  val times = time?.split("-", limit = 2).orEmpty()
  return CourseDetailInfo(
      courseName = displayCourseName(),
      placeName = place,
      beginTime = times.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() },
      endTime = times.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() },
      date = date,
  )
}

fun CourseDetailInfo.toClipboardText(): String =
    buildList {
          add(courseName)
          courseCode?.takeIf { it.isNotBlank() }?.let { add("课程代码：$it") }
          courseSerialNo?.takeIf { it.isNotBlank() }?.let { add("教学班：$it") }
          credit?.takeIf { it.isNotBlank() }?.let { add("学分：$it") }
          placeName?.takeIf { it.isNotBlank() }?.let { add("地点：$it") }
          date?.let { add("日期：${formatUbaaDate(it)}") }
          dayOfWeek?.let { add("星期：${formatUbaaWeekdayLabel(it) ?: "未知"}") }
          sectionsText()?.let { add("节次：$it") }
          timeText()?.let { add("时间：$it") }
          weeksAndTeachers?.takeIf { it.isNotBlank() }?.let { add("周次/教师：$it") }
          teachingTarget?.takeIf { it.isNotBlank() }?.let { add("教学班组成：$it") }
        }
        .joinToString("\n")

private fun CourseDetailInfo.sectionsText(): String? {
  val start = beginSection ?: return null
  val end = endSection ?: start
  return if (start == end) "第${start}节" else "第${start}-${end}节"
}

private fun CourseDetailInfo.timeText(): String? {
  val start = parseUbaaTime(beginTime) ?: return null
  val end = parseUbaaTime(endTime)
  return if (end != null) "${formatUbaaTime(start)}-${formatUbaaTime(end)}"
  else formatUbaaTime(start)
}

@Composable
fun CourseDetailDialog(info: CourseDetailInfo, onDismiss: () -> Unit) {
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(info.courseName, fontWeight = FontWeight.Bold) },
      text = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          info.courseCode?.takeIf { it.isNotBlank() }?.let { DetailLine("课程代码", it) }
          info.courseSerialNo?.takeIf { it.isNotBlank() }?.let { DetailLine("教学班", it) }
          info.credit?.takeIf { it.isNotBlank() }?.let { DetailLine("学分", it) }
          info.placeName?.takeIf { it.isNotBlank() }?.let { DetailLine("地点", it) }
          info.date?.let { DetailLine("日期", formatUbaaDate(it)) }
          info.dayOfWeek?.let { DetailLine("星期", formatUbaaWeekdayLabel(it) ?: "未知") }
          info.sectionsText()?.let { DetailLine("节次", it) }
          info.timeText()?.let { DetailLine("时间", it) }
          info.weeksAndTeachers?.takeIf { it.isNotBlank() }?.let { DetailLine("周次/教师", it) }
          info.teachingTarget?.takeIf { it.isNotBlank() }?.let { DetailLine("教学班组成", it) }
        }
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(
              onClick = {
                clipboard.setText(AnnotatedString(info.toClipboardText()))
                UbaaToast.show("课程信息已复制")
              }
          ) {
            Text("复制详细信息")
          }
          TextButton(
              onClick = {
                val ok =
                    addCourseToCalendar(
                        context = context,
                        title = info.courseName,
                        location = info.placeName,
                        description = info.toClipboardText(),
                        date = info.date,
                        beginTime = info.beginTime,
                        endTime = info.endTime,
                    )
                UbaaToast.show(if (ok) "已打开日历" else "未找到可用的日历应用")
              }
          ) {
            Text("添加到日历")
          }
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
  )
}

@Composable
private fun DetailLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "$label：",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp),
    )
    Text(text = value, style = MaterialTheme.typography.bodyMedium)
  }
}
