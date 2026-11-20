package cn.edu.ubaa.plus.ui.screens.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.feature.IclassSigninLink
import cn.edu.ubaa.plus.api.feature.SigninApi
import cn.edu.ubaa.plus.model.dto.CourseClass
import cn.edu.ubaa.plus.model.dto.TodayClass
import cn.edu.ubaa.plus.model.format.formatUbaaDate
import cn.edu.ubaa.plus.model.format.formatUbaaTime
import cn.edu.ubaa.plus.model.format.formatUbaaWeekdayLabel
import cn.edu.ubaa.plus.model.format.parseUbaaDate
import cn.edu.ubaa.plus.model.format.parseUbaaTime
import cn.edu.ubaa.plus.ui.common.components.UbaaAlertDialog
import cn.edu.ubaa.plus.ui.common.components.UbaaIconButton
import cn.edu.ubaa.plus.ui.common.components.UbaaToast
import cn.edu.ubaa.plus.ui.common.util.addCourseToCalendar
import kotlinx.coroutines.launch
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
  UbaaAlertDialog(
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
          Spacer(modifier = Modifier.height(4.dp))
          SigninLinkCard()
        }
      },
      titleActions = {
        UbaaIconButton(
            onClick = {
              clipboard.setText(AnnotatedString(info.toClipboardText()))
              UbaaToast.show("课程信息已复制")
            },
            modifier = Modifier.size(48.dp),
        ) {
          Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "复制详细信息",
              modifier = Modifier.size(18.dp),
          )
        }
        UbaaIconButton(
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
            },
            modifier = Modifier.size(48.dp),
        ) {
          Icon(
              imageVector = Icons.Default.Event,
              contentDescription = "添加到日历",
              modifier = Modifier.size(18.dp),
          )
        }
      },
      confirmButton = {},
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

/**
 * 课程详情里的「课程签到链接」卡片。
 *
 * 学校每节课前会用二维码公布签到链接，除时间戳外内容固定。这里在本地把链接拼好， 复制后可以直接在浏览器或企业微信里打开——走学校原始入口，不伪造签名。
 */
@Composable
private fun SigninLinkCard() {
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val signinApi = remember { SigninApi() }
  var state by remember { mutableStateOf<SigninLinkState>(SigninLinkState.Loading) }

  LaunchedEffect(Unit) {
    state =
        signinApi
            .getCurrentSocketInfo()
            .fold(
                onSuccess = { info ->
                  if (!info.hasCurrentCourse) {
                    SigninLinkState.NoCourse
                  } else {
                    val timestamp = signinApi.currentTimestamp()
                    if (timestamp == null) {
                      SigninLinkState.Failed("无法获取服务器时间戳")
                    } else {
                      val link =
                          IclassSigninLink.build(
                              courseSchedId = info.courseSchedId,
                              timestamp = timestamp,
                          )
                      SigninLinkState.Ready(link, info.courseName)
                    }
                  }
                },
                onFailure = { SigninLinkState.Failed(it.message ?: "课时信息读取失败") },
            )
  }

  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
          text = "课程签到链接",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
      )
      val hint =
          when (val current = state) {
            SigninLinkState.Loading -> "正在获取当前课时…"
            SigninLinkState.NoCourse -> "当前不在上课时间"
            is SigninLinkState.Failed -> current.message
            is SigninLinkState.Ready -> "已带最新时间戳，可复制后打开"
          }
      Text(
          text = hint,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    UbaaIconButton(
        onClick = {
          val current = state
          if (current is SigninLinkState.Ready) {
            clipboard.setText(AnnotatedString(current.link))
            UbaaToast.show("签到链接已复制")
          } else {
            scope.launch { UbaaToast.show("当前没有可用的签到链接") }
          }
        },
        enabled = state is SigninLinkState.Ready,
        modifier = Modifier.size(48.dp),
    ) {
      Icon(
          imageVector = Icons.Default.Link,
          contentDescription = "复制签到链接",
          modifier = Modifier.size(18.dp),
      )
    }
  }
}

/** 签到链接卡片的加载状态。 */
private sealed interface SigninLinkState {
  data object Loading : SigninLinkState

  /** 当前不在课时，学校还没生成签到链接。 */
  data object NoCourse : SigninLinkState

  data class Ready(val link: String, val courseName: String) : SigninLinkState

  data class Failed(val message: String) : SigninLinkState
}
