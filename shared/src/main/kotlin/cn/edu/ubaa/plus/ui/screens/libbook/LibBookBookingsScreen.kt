package cn.edu.ubaa.plus.ui.screens.libbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.isTerminalBooking
import cn.edu.ubaa.plus.model.format.formatUbaaDateWithTimeRange
import cn.edu.ubaa.plus.ui.common.components.*

/** 已结束/取消的历史；正在使用的座位在首页与预约页管理。 */
@Composable
fun LibBookBookingsScreen(viewModel: LibBookViewModel) {
  val state by viewModel.uiState.collectAsState()
  LaunchedEffect(Unit) { viewModel.loadBookings() }
  val history = state.bookings.bookings.filter { it.isTerminalBooking() }
  UbaaPullToRefresh(
      refreshing = state.isBookingsLoading,
      onRefresh = { viewModel.loadBookings() },
      modifier = Modifier.fillMaxSize(),
  ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { UbaaSectionHeader(title = "历史预约（${history.size}）") }
      if (state.isBookingsLoading && history.isEmpty())
          item { UbaaLoadingState(text = "正在加载预约记录…") }
      else if (state.bookingsError != null)
          item {
            UbaaErrorState(
                message = state.bookingsError.orEmpty(),
                onRetry = { viewModel.loadBookings() },
            )
          }
      else if (history.isEmpty()) item { UbaaEmptyState(title = "暂无历史预约记录") }
      items(history) { booking ->
        UbaaSectionCard(
            title =
                booking.nameMerge.ifBlank {
                  listOf(booking.areaName, booking.seatNo)
                      .filter { it.isNotBlank() }
                      .joinToString(" / ")
                      .ifBlank { "图书馆座位" }
                }
        ) {
          Text(
              libBookHomeStatusLabel(booking),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Medium,
          )
          Text(
              formatUbaaDateWithTimeRange(booking.day, booking.beginTime, booking.endTime),
              style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}
