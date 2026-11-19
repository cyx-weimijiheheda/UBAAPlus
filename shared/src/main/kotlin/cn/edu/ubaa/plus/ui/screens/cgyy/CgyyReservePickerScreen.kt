package cn.edu.ubaa.plus.ui.screens.cgyy

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.CgyyReservationSelectionDto
import cn.edu.ubaa.plus.model.dto.CgyySpaceAvailabilityDto
import cn.edu.ubaa.plus.model.dto.CgyyTimeSlotDto
import cn.edu.ubaa.plus.model.dto.CgyyVenueSiteDto
import cn.edu.ubaa.plus.model.format.formatUbaaCompactDate
import cn.edu.ubaa.plus.model.format.formatUbaaMonthDay
import cn.edu.ubaa.plus.ui.common.components.*
import cn.edu.ubaa.plus.ui.common.components.UbaaActionChip
import cn.edu.ubaa.plus.ui.common.components.UbaaButton
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaSegmentedTabs
import cn.edu.ubaa.plus.ui.theme.ubaaGlassCardColors
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun CgyyReservePickerScreen(
    viewModel: CgyyViewModel,
    onNext: () -> Unit,
    onOrdersClick: () -> Unit,
    onLockCodeClick: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  val campusOptions =
      listOf(CgyyViewModel.ALL_CAMPUSES) +
          uiState.sites.map { it.campusName }.distinct().sortedBy { campusSortKey(it) }
  val selectedCampus = uiState.selectedCampus.ifBlank { CgyyViewModel.ALL_CAMPUSES }
  val campusSites =
      if (selectedCampus == CgyyViewModel.ALL_CAMPUSES) {
        uiState.sites
      } else {
        uiState.sites.filter { it.campusName == selectedCampus }
      }
  val visibleSites = campusSites
  val availableDates = uiState.dayInfo?.availableDates.orEmpty()
  val visibleSpaces = uiState.dayInfo?.spaces.orEmpty()
  Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
      bottomBar = {
        Card(
            modifier = Modifier.ubaaGlassSurface().fillMaxWidth(),
            colors = ubaaGlassCardColors(),
        ) {
          UbaaButton(
              onClick = onNext,
              enabled = viewModel.canAdvanceToReservationForm(),
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 12.dp)
                      .height(52.dp),
          ) {
            Text("下一步")
          }
        }
      },
  ) { innerPadding ->
    UbaaPullToRefresh(
        refreshing = uiState.isInitialLoading || uiState.isDayInfoLoading,
        onRefresh = viewModel::refreshReserveData,
        modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding()),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        CgyySecondaryEntriesRow(
            onOrdersClick = onOrdersClick,
            onLockCodeClick = onLockCodeClick,
        )

        when {
          uiState.isInitialLoading && uiState.sites.isEmpty() ->
              UbaaLoadingState(
                  text = "正在加载场地与可预约信息...",
                  showIndicator = false,
                  modifier = Modifier.weight(1f),
              )
          uiState.initialError != null && uiState.sites.isEmpty() ->
              CgyyErrorState(
                  uiState.initialError,
                  onRetry = viewModel::loadInitialData,
                  modifier = Modifier.weight(1f),
              )
          else -> {
            UbaaSegmentedTabs(
                labels = campusOptions.map(::campusDisplayName),
                selectedIndex = campusOptions.indexOf(selectedCampus),
                onSelected = { viewModel.setReserveCampus(campusOptions[it]) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (availableDates.isNotEmpty()) {
              UbaaSegmentedTabs(
                  labels = availableDates.map(::formatCompactDateLabel),
                  selectedIndex = availableDates.indexOf(uiState.selectedDate),
                  onSelected = { viewModel.selectDate(availableDates[it]) },
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
              )
            }

            if (visibleSites.isNotEmpty()) {
              UbaaSegmentedTabs(
                  labels = visibleSites.map(::siteSelectionLabel),
                  selectedIndex = visibleSites.indexOfFirst { it.id == uiState.selectedSiteId },
                  onSelected = { viewModel.selectSite(visibleSites[it].id) },
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
              )
            }

            when {
              uiState.isDayInfoLoading ->
                  UbaaLoadingState(
                      text = "正在刷新可预约时段...",
                      showIndicator = false,
                      modifier = Modifier.fillMaxSize(),
                  )
              uiState.dayInfoError != null ->
                  CgyyErrorState(uiState.dayInfoError, onRetry = viewModel::refreshReserveData)
              uiState.selectedSiteId == null -> CgyyEmptyState("请选择楼栋/层", "先从上方横向列表中选择一个研讨室位置。")
              visibleSpaces.isEmpty() -> CgyyEmptyState("暂无可预约教室", "当前楼栋或搜索条件下没有匹配教室。")
              else ->
                  CgyyReservationTable(
                      spaces = visibleSpaces,
                      timeSlots = uiState.dayInfo?.timeSlots.orEmpty(),
                      selectedSelections = uiState.selections,
                      onSlotToggle = viewModel::toggleSlot,
                  )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CgyySecondaryEntriesRow(
    onOrdersClick: () -> Unit,
    onLockCodeClick: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    UbaaActionChip(
        onClick = onOrdersClick,
        label = { Text("我的预约") },
        leadingIcon = {
          Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
        },
    )
    UbaaActionChip(
        onClick = onLockCodeClick,
        label = { Text("门锁密码") },
        leadingIcon = {
          Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
        },
    )
  }
}

@Composable
private fun CgyyReservationTable(
    spaces: List<CgyySpaceAvailabilityDto>,
    timeSlots: List<CgyyTimeSlotDto>,
    selectedSelections: List<CgyyReservationSelectionDto>,
    onSlotToggle: (Int, Int, Int?) -> Unit,
) {
  val scroll = rememberScrollState()
  val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
  val roomWidth = 100.dp * scale
  val breaks = remember(timeSlots) { cgyyTimeBreaks(timeSlots) }
  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    UbaaAvailabilityLegend(includeSelected = true, modifier = Modifier.padding(vertical = 8.dp))
    BoxWithConstraints(Modifier.weight(1f)) {
      val slotWidth = maxOf(66.dp * scale, (maxWidth - roomWidth) / timeSlots.size.coerceAtLeast(1))
      Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(84.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
              "时段",
              Modifier.width(roomWidth),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            timeSlots.forEachIndexed { index, slot ->
              Column(
                  Modifier.width(slotWidth).padding(vertical = 4.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(3.dp),
              ) {
                UbaaTimelineMarker((index + 1).toString(), 20.dp * scale)
                Text(
                    slot.beginTime.ifBlank { "未提供" },
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Text(
                    slot.endTime.ifBlank { "未提供" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
              }
            }
          }
        }
        LazyColumn(Modifier.weight(1f)) {
          items(spaces, key = { it.spaceId }) { space ->
            val statuses =
                timeSlots.map { slot ->
                  when {
                    selectedSelections.any {
                      it.spaceId == space.spaceId && it.timeId == slot.id
                    } -> AvailabilityTone.SELECTED
                    space.slots.firstOrNull { it.timeId == slot.id }?.isReservable == true ->
                        AvailabilityTone.AVAILABLE
                    else -> AvailabilityTone.UNAVAILABLE
                  }
                }
            Row(
                Modifier.fillMaxWidth().height(56.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                  space.spaceName,
                  modifier = Modifier.width(roomWidth).padding(end = 12.dp),
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
              )
              Box(
                  Modifier.weight(1f).horizontalScroll(scroll),
                  contentAlignment = Alignment.CenterStart,
              ) {
                UbaaAvailabilityTrack(statuses, slotWidth, breaks)
                Row {
                  timeSlots.forEachIndexed { index, slot ->
                    val slotState = space.slots.firstOrNull { it.timeId == slot.id }
                    val selected = statuses[index] == AvailabilityTone.SELECTED
                    val enabled = slotState != null && (slotState.isReservable || selected)
                    Box(
                        Modifier.width(slotWidth)
                            .height(56.dp * scale)
                            .toggleable(
                                value = selected,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = {
                                  onSlotToggle(space.spaceId, slot.id, space.venueSpaceGroupId)
                                },
                            )
                            .semantics {
                              contentDescription =
                                  "${space.spaceName}，${slot.beginTime}至${slot.endTime}，" +
                                      if (selected) "已选" else if (enabled) "可预约" else "不可选"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                      if (selected)
                          Icon(
                              Icons.Default.Check,
                              contentDescription = null,
                              tint = MaterialTheme.colorScheme.onPrimary,
                              modifier = Modifier.size(16.dp),
                          )
                    }
                  }
                }
              }
            }
          }
          item { Spacer(Modifier.height(16.dp)) }
        }
      }
    }
  }
}

internal fun cgyyTimeBreaks(slots: List<CgyyTimeSlotDto>): Set<Int> =
    (1 until slots.size)
        .filter { index ->
          val previous = slots[index - 1].endTime.trim()
          val next = slots[index].beginTime.trim()
          previous.isBlank() || next.isBlank() || previous != next
        }
        .toSet()

private fun siteSelectionLabel(site: CgyyVenueSiteDto): String = buildString {
  append(shortVenueName(site.venueName))
  if (site.siteName.isNotBlank()) {
    append(" / ")
    append(site.siteName)
  }
}

private fun shortVenueName(name: String): String = name.removeSuffix("研讨室").ifBlank { name }

private fun campusDisplayName(name: String): String =
    when {
      name.contains("学院路") -> "学院路"
      name.contains("沙河") -> "沙河"
      else -> name
    }

private fun campusSortKey(name: String): Int =
    when {
      name.contains("学院路") -> 0
      name.contains("沙河") -> 1
      else -> 99
    }

private fun formatCompactDateLabel(date: String): String {
  val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
  return if (date == today) "${formatUbaaMonthDay(date)} 今天" else formatUbaaCompactDate(date)
}
