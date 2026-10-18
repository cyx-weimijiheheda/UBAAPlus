package cn.edu.ubaa.plus.ui.screens.menu

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CoPresent
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.storage.FeatureLayout

data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val webUrl: String? = null,
)

/** 「功能」页的全部入口，保持顺序稳定，便于测试断言。 */
internal fun regularFeatureItems(): List<FeatureItem> =
    listOf(
        FeatureItem(
            id = "schedule",
            title = "课表查询",
            description = "查看课程表，支持周视图和学期切换",
            icon = Icons.Default.CalendarToday,
            webUrl = "https://byxt.buaa.edu.cn",
        ),
        FeatureItem(
            id = "classroom",
            title = "空教室查询",
            description = "查询各校区空闲教室",
            icon = Icons.Default.MeetingRoom,
            webUrl = "https://byxt.buaa.edu.cn",
        ),
        FeatureItem(
            id = "iclass",
            title = "智慧教室",
            description = "扫码签到与考勤查询",
            icon = Icons.Default.CoPresent,
            webUrl = "https://spoc.buaa.edu.cn/bhspoc/#/pages/main/index1",
        ),
        FeatureItem(
            id = "libbook",
            title = "图书馆座位",
            description = "预约图书馆座位并管理记录",
            icon = Icons.Default.EventSeat,
            webUrl = "https://booking.lib.buaa.edu.cn",
        ),
        FeatureItem(
            id = "libservice",
            title = "图书借阅",
            description = "查看当前借阅与借阅历史",
            icon = Icons.Default.MenuBook,
            webUrl = "https://space.lib.buaa.edu.cn/space/",
        ),
        FeatureItem(
            id = "spoc",
            title = "SPOC作业",
            description = "查看当前学期作业与提交状态",
            icon = Icons.Default.AssignmentTurnedIn,
            webUrl = "https://spoc.buaa.edu.cn",
        ),
        FeatureItem(
            id = "bykc",
            title = "博雅课程",
            description = "浏览选课，查看已选，签到签退",
            icon = Icons.Default.School,
            webUrl = "https://bykc.buaa.edu.cn",
        ),
        FeatureItem(
            id = "venue",
            title = "运动场地",
            description = "查询体育场馆空闲时段并自动预约",
            icon = Icons.Default.SportsTennis,
            webUrl = "https://cgyy.buaa.edu.cn/venue/",
        ),
        FeatureItem(
            id = "ygdk",
            title = "阳光打卡",
            description = "查看记录并提交体育活动打卡",
            icon = Icons.Default.WbSunny,
            webUrl = "https://ygdk.buaa.edu.cn",
        ),
        FeatureItem(
            id = "cgyy",
            title = "研讨室预约",
            description = "查询、提交和管理研讨室预约",
            icon = Icons.Default.DateRange,
            webUrl = "https://cgyy.buaa.edu.cn/venue-zhjs/",
        ),
        FeatureItem(
            id = "dorm",
            title = "寝室用电",
            description = "查询余额、购电记录并缴费",
            icon = Icons.Default.ElectricalServices,
            webUrl = "http://shsd.buaa.edu.cn/PubBuaa",
        ),
        FeatureItem(
            id = "network",
            title = "校园网络",
            description = "查看流量、网费与套餐",
            icon = Icons.Default.Wifi,
            webUrl = "https://zfw.buaa.edu.cn",
        ),
        FeatureItem(
            id = "card",
            title = "校园卡",
            description = "查看余额、账单与充值",
            icon = Icons.Default.CreditCard,
            webUrl = "https://pass.cc-pay.cn/mcampcard",
        ),
        FeatureItem(
            id = "evaluation",
            title = "自动评教",
            description = "一键完成学期末评教任务",
            icon = Icons.Default.AssignmentTurnedIn,
            webUrl = "https://byxt.buaa.edu.cn",
        ),
        FeatureItem(
            id = "exam",
            title = "考试查询",
            description = "查看考试安排，支持学期切换",
            icon = Icons.AutoMirrored.Filled.Assignment,
            webUrl = "https://byxt.buaa.edu.cn",
        ),
        FeatureItem(
            id = "grade",
            title = "成绩查询",
            description = "查看课程成绩、学分和绩点",
            icon = Icons.Default.Grade,
            webUrl = "https://byxt.buaa.edu.cn",
        ),
        FeatureItem(
            id = "judge",
            title = "希冀作业",
            description = "聚合希冀平台作业与提交进度",
            icon = Icons.Default.Code,
            webUrl = "https://judge.buaa.edu.cn",
        ),
    )

/** 按用户顺序排列；未出现在 order 中的新功能追加到末尾。 */
internal fun orderFeatureItems(
    order: List<String>,
    all: List<FeatureItem> = regularFeatureItems(),
): List<FeatureItem> {
  val byId = all.associateBy { it.id }
  val seen = mutableSetOf<String>()
  val result = mutableListOf<FeatureItem>()
  order.forEach { id -> byId[id]?.let { if (seen.add(id)) result += it } }
  all.forEach { item -> if (seen.add(item.id)) result += item }
  return result
}

/** 应用排序与隐藏后的功能列表。 */
internal fun resolveFeatureItems(
    order: List<String>,
    hidden: Set<String>,
    all: List<FeatureItem> = regularFeatureItems(),
): List<FeatureItem> = orderFeatureItems(order, all).filter { it.id !in hidden }

@Composable
fun RegularFeaturesScreen(
    features: List<FeatureItem>,
    layout: FeatureLayout,
    onFeatureClick: (FeatureItem) -> Unit,
    onLongPressWeb: (FeatureItem) -> Unit,
    modifier: Modifier = Modifier,
) {
  var menuFeature by remember { mutableStateOf<FeatureItem?>(null) }
  var menuOffset by remember { mutableStateOf(Offset.Zero) }
  val density = LocalDensity.current

  Box(modifier = modifier.fillMaxSize()) {
    when (layout) {
      FeatureLayout.GRID ->
          LazyVerticalGrid(
              columns = GridCells.Fixed(2),
              contentPadding = PaddingValues(16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxSize(),
          ) {
            items(features, key = { it.id }) { feature ->
              FeatureCard(
                  feature = feature,
                  onClick = { onFeatureClick(feature) },
                  onLongPress = { position ->
                    menuFeature = feature
                    menuOffset = position
                  },
              )
            }
          }
      FeatureLayout.COMPACT_LIST ->
          LazyColumn(
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxSize(),
          ) {
            items(features, key = { it.id }) { feature ->
              FeatureCompactRow(
                  feature = feature,
                  onClick = { onFeatureClick(feature) },
                  onLongPress = { position ->
                    menuFeature = feature
                    menuOffset = position
                  },
              )
            }
          }
    }

    DropdownMenu(
        expanded = menuFeature != null,
        onDismissRequest = { menuFeature = null },
        offset = with(density) { DpOffset(menuOffset.x.toDp(), menuOffset.y.toDp()) },
    ) {
      DropdownMenuItem(
          text = { Text("在浏览器打开") },
          enabled = menuFeature?.webUrl != null,
          onClick = {
            menuFeature?.let(onLongPressWeb)
            menuFeature = null
          },
      )
    }
  }
}

@Composable
private fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit,
    onLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
  var cardPosition by remember { mutableStateOf(Offset.Zero) }
  Card(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = 160.dp)
              .onGloballyPositioned { cardPosition = it.positionInRoot() }
              .pointerInput(feature.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { offset -> onLongPress(cardPosition + offset) },
                )
              },
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

@Composable
private fun FeatureCompactRow(
    feature: FeatureItem,
    onClick: () -> Unit,
    onLongPress: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
  var rowPosition by remember { mutableStateOf(Offset.Zero) }
  Card(
      modifier =
          modifier
              .fillMaxWidth()
              .onGloballyPositioned { rowPosition = it.positionInRoot() }
              .pointerInput(feature.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { offset -> onLongPress(rowPosition + offset) },
                )
              },
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
          imageVector = feature.icon,
          contentDescription = null,
          modifier = Modifier.size(32.dp),
          tint = MaterialTheme.colorScheme.primary,
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
            text = feature.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = feature.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
