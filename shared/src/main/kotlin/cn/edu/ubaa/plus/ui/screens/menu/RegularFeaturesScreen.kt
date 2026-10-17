package cn.edu.ubaa.plus.ui.screens.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CoPresent
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

/** 「功能」页的全部入口，保持顺序稳定，便于测试断言。 */
internal fun regularFeatureItems(): List<FeatureItem> =
    listOf(
        FeatureItem(
            id = "schedule",
            title = "课表查询",
            description = "查看课程表，支持周视图和学期切换",
            icon = Icons.Default.CalendarToday,
        ),
        FeatureItem(
            id = "classroom",
            title = "空教室查询",
            description = "查询各校区空闲教室",
            icon = Icons.Default.MeetingRoom,
        ),
        FeatureItem(
            id = "iclass",
            title = "智慧教室",
            description = "扫码签到与考勤查询",
            icon = Icons.Default.CoPresent,
        ),
        FeatureItem(
            id = "libbook",
            title = "图书馆座位",
            description = "预约图书馆座位并管理记录",
            icon = Icons.Default.EventSeat,
        ),
        FeatureItem(
            id = "spoc",
            title = "SPOC作业",
            description = "查看当前学期作业与提交状态",
            icon = Icons.Default.AssignmentTurnedIn,
        ),
        FeatureItem(
            id = "bykc",
            title = "博雅课程",
            description = "浏览选课，查看已选，签到签退",
            icon = Icons.Default.School,
        ),
        FeatureItem(
            id = "venue",
            title = "运动场地",
            description = "查询体育场馆空闲时段并自动预约",
            icon = Icons.Default.SportsTennis,
        ),
        FeatureItem(
            id = "ygdk",
            title = "阳光打卡",
            description = "查看记录并提交体育活动打卡",
            icon = Icons.Default.WbSunny,
        ),
        FeatureItem(
            id = "cgyy",
            title = "研讨室预约",
            description = "查询、提交和管理研讨室预约",
            icon = Icons.Default.DateRange,
        ),
        FeatureItem(
            id = "judge",
            title = "希冀作业",
            description = "聚合希冀平台作业与提交进度",
            icon = Icons.Default.Code,
        ),
        FeatureItem(
            id = "evaluation",
            title = "自动评教",
            description = "一键完成学期末评教任务",
            icon = Icons.Default.AssignmentTurnedIn,
        ),
        FeatureItem(
            id = "exam",
            title = "考试查询",
            description = "查看考试安排，支持学期切换",
            icon = Icons.AutoMirrored.Filled.Assignment,
        ),
        FeatureItem(
            id = "grade",
            title = "成绩查询",
            description = "查看课程成绩、学分和绩点",
            icon = Icons.Default.Grade,
        ),
    )

@Composable
fun RegularFeaturesScreen(
    onScheduleClick: () -> Unit,
    onExamClick: () -> Unit,
    onGradeClick: () -> Unit,
    onBykcClick: () -> Unit,
    onClassroomClick: () -> Unit,
    onIclassClick: () -> Unit,
    onSpocClick: () -> Unit,
    onJudgeClick: () -> Unit,
    onLibBookClick: () -> Unit,
    onVenueClick: () -> Unit,
    onCgyyClick: () -> Unit,
    onYgdkClick: () -> Unit,
    onEvaluationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val features = regularFeatureItems()

  Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      items(features) { feature ->
        FeatureCard(
            feature = feature,
            onClick = {
              when (feature.id) {
                "schedule" -> onScheduleClick()
                "exam" -> onExamClick()
                "grade" -> onGradeClick()
                "bykc" -> onBykcClick()
                "classroom" -> onClassroomClick()
                "iclass" -> onIclassClick()
                "spoc" -> onSpocClick()
                "judge" -> onJudgeClick()
                "libbook" -> onLibBookClick()
                "venue" -> onVenueClick()
                "cgyy" -> onCgyyClick()
                "ygdk" -> onYgdkClick()
                "evaluation" -> onEvaluationClick()
              }
            },
        )
      }
    }
  }
}

@Composable
private fun FeatureCard(feature: FeatureItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Card(
      modifier = modifier.fillMaxWidth().heightIn(min = 160.dp).clickable { onClick() },
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Icon(
          imageVector = feature.icon,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = MaterialTheme.colorScheme.primary,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
          text = feature.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
          text = feature.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
      )
    }
  }
}
