package cn.edu.ubaa.plus.ui.screens.classroom

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.ClassroomInfo
import cn.edu.ubaa.plus.model.format.formatUbaaDate
import cn.edu.ubaa.plus.ui.common.components.*
import cn.edu.ubaa.plus.ui.common.components.UbaaButton
import cn.edu.ubaa.plus.ui.common.components.UbaaConfirmAction
import cn.edu.ubaa.plus.ui.common.components.UbaaIconAction
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaSearchField
import cn.edu.ubaa.plus.ui.common.components.UbaaSegmentedTabs
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect
import cn.edu.ubaa.plus.ui.theme.ubaaGlassCardColors
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 教室查询功能屏幕。 结合了美观的视觉风格（圆角、配色）与紧凑的布局（一页展示14节课）。
 *
 * @param viewModel 控制查询逻辑的状态机。
 * @param onBackClick 点击返回按钮的回调。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ClassroomQueryScreen(
    viewModel: ClassroomViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val xqid by viewModel.xqid.collectAsState()
  val date by viewModel.date.collectAsState()
  val searchQuery by viewModel.searchQuery.collectAsState()
  val selectedBuilding by viewModel.selectedBuilding.collectAsState()
  val availableBuildings by viewModel.availableBuildings.collectAsState()
  val filteredData by viewModel.filteredData.collectAsState()
  var showDatePicker by remember { mutableStateOf(false) }

  UbaaToastEffect((uiState as? ClassroomUiState.Error)?.message) {}

  LaunchedEffect(Unit) { if (uiState is ClassroomUiState.Idle) viewModel.query() }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = { showDatePicker = false },
        confirmButton = {
          UbaaConfirmAction(
              onClick = {
                datePickerState.selectedDateMillis?.let {
                  viewModel.setDate(
                      Instant.fromEpochMilliseconds(it)
                          .toLocalDateTime(TimeZone.UTC)
                          .date
                          .toString()
                  )
                }
                showDatePicker = false
              },
              description = "确定",
          )
        },
        dismissButton = {
          UbaaIconAction(
              onClick = { showDatePicker = false },
              description = "取消",
              icon = Icons.Default.Close,
          )
        },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    // 校区切换改用圆角分段选择器，与图书借阅的栏目切换保持一致。
    UbaaSegmentedTabs(
        labels = listOf("学院路", "沙河", "杭州"),
        selectedIndex = (xqid - 1).coerceIn(0, 2),
        onSelected = { index -> viewModel.setXqid(index + 1) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )

    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        Arrangement.spacedBy(8.dp),
        Alignment.CenterVertically,
    ) {
      Card(
          onClick = { showDatePicker = true },
          modifier = Modifier.ubaaGlassSurface(shape = RoundedCornerShape(14.dp)).weight(1f),
          colors = ubaaGlassCardColors(),
          shape = RoundedCornerShape(14.dp),
      ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
              text = formatUbaaDate(date),
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
          Icon(
              Icons.Default.DateRange,
              null,
              Modifier.size(20.dp),
              MaterialTheme.colorScheme.primary,
          )
        }
      }
      UbaaSearchField(
          value = searchQuery,
          onValueChange = viewModel::setSearchQuery,
          hint = "搜索教室/楼栋",
          modifier = Modifier.weight(1.5f),
      )
    }

    if (uiState is ClassroomUiState.Success && availableBuildings.isNotEmpty()) {
      UbaaSegmentedTabs(
          labels = availableBuildings,
          selectedIndex = availableBuildings.indexOfFirst { it == selectedBuilding },
          onSelected = { viewModel.toggleBuilding(availableBuildings[it]) },
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
      )
    }

    UbaaAvailabilityLegend(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

    UbaaPullToRefresh(
        refreshing = uiState is ClassroomUiState.Loading,
        onRefresh = viewModel::query,
    ) {
      when (val state = uiState) {
        is ClassroomUiState.Loading ->
            UbaaLoadingState(
                text = "正在查询...",
                showIndicator = false,
                modifier = Modifier.fillMaxSize(),
            )
        is ClassroomUiState.Success ->
            if (filteredData.isEmpty())
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("未找到匹配教室") }
            else ClassroomTable(filteredData)
        is ClassroomUiState.Error ->
            Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "查询失败: ${state.message}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                UbaaButton(onClick = { viewModel.query() }) { Text("重试") }
              }
            }
        else -> {}
      }
    }
  }
}

/** Fixed room names and a shared horizontal timeline; no grid borders or repeated building tags. */
@Composable
fun ClassroomTable(list: Map<String, List<ClassroomInfo>>) {
  val scroll = rememberScrollState()
  val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
  val nameWidth = 68.dp * fontScale
  val markerSize = 18.dp * fontScale
  BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    val slotWidth = maxOf(20.dp * fontScale, (maxWidth - nameWidth) / 14)
    Column(Modifier.fillMaxSize()) {
      Row(
          Modifier.fillMaxWidth().height(44.dp * fontScale),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            "节次",
            Modifier.width(nameWidth),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.weight(1f).horizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          for (section in 1..14) {
            Box(Modifier.width(slotWidth), contentAlignment = Alignment.Center) {
              UbaaTimelineMarker(section.toString(), markerSize)
            }
          }
        }
      }
      LazyColumn(Modifier.weight(1f)) {
        list.forEach { (building, classrooms) ->
          items(classrooms, key = { "$building:${it.id}" }) { classroom ->
            val free = remember(classroom.kxsds) { classroomFreeSections(classroom.kxsds) }
            Row(
                Modifier.fillMaxWidth().height(48.dp * fontScale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  classroom.name,
                  modifier = Modifier.width(nameWidth).padding(end = 8.dp),
                  style = MaterialTheme.typography.labelMedium,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                  color = MaterialTheme.colorScheme.primary,
              )
              Box(
                  Modifier.weight(1f).horizontalScroll(scroll).semantics {
                    contentDescription =
                        if (free.isEmpty()) "没有列为空闲的节次"
                        else "空闲节次：" + free.sorted().joinToString("、")
                  },
                  contentAlignment = Alignment.CenterStart,
              ) {
                UbaaAvailabilityTrack(
                    (1..14).map {
                      if (it in free) AvailabilityTone.AVAILABLE else AvailabilityTone.UNAVAILABLE
                    },
                    slotWidth,
                )
              }
            }
          }
        }
        item { Spacer(Modifier.height(16.dp)) }
      }
    }
  }
}

internal fun classroomFreeSections(value: String): Set<Int> =
    value
        .split(',')
        .mapNotNull { it.trim().toIntOrNull()?.takeIf { section -> section in 1..14 } }
        .toSet()
