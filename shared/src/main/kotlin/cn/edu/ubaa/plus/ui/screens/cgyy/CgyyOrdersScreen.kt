package cn.edu.ubaa.plus.ui.screens.cgyy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.CgyyOrderDto
import cn.edu.ubaa.plus.model.dto.displayReservationDateText
import cn.edu.ubaa.plus.model.dto.displayStatus
import cn.edu.ubaa.plus.ui.common.components.UbaaLoadingState
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaToast
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect

@Composable
fun CgyyOrdersScreen(viewModel: CgyyViewModel) {
  val uiState by viewModel.uiState.collectAsState()
  var selectedOrder by remember { mutableStateOf<CgyyOrderDto?>(null) }
  LaunchedEffect(Unit) { viewModel.ensureOrdersLoaded() }
  LaunchedEffect(uiState.actionMessage) {
    uiState.actionMessage?.let { message ->
      UbaaToast.show(message)
      viewModel.clearActionMessage()
    }
  }
  UbaaToastEffect(uiState.ordersError) {}

  selectedOrder?.let { order ->
    CgyyOrderDetailDialog(
        order = order,
        onDismiss = { selectedOrder = null },
        onCancel = {
          viewModel.cancelOrder(order.id)
          selectedOrder = null
        },
    )
  }

  Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) { paddingValues ->
    UbaaPullToRefresh(
        refreshing = uiState.isOrdersLoading,
        onRefresh = viewModel::loadOrders,
        modifier = Modifier.fillMaxSize().padding(paddingValues),
    ) {
      when {
        uiState.isOrdersLoading && uiState.orders.content.isEmpty() ->
            UbaaLoadingState(
                text = "正在加载预约记录...",
                showIndicator = false,
                modifier = Modifier.fillMaxSize(),
            )
        uiState.ordersError != null && uiState.orders.content.isEmpty() ->
            CgyyErrorState(
                uiState.ordersError,
                onRetry = viewModel::loadOrders,
                modifier = Modifier.fillMaxSize(),
            )
        uiState.orders.content.isEmpty() ->
            CgyyEmptyState("当前暂无预约记录", "下拉刷新后再试。", modifier = Modifier.fillMaxSize())
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(uiState.orders.content) { order ->
                CgyyOrderCard(
                    order = order,
                    onOpenDetail = { selectedOrder = order },
                    onCancel = { viewModel.cancelOrder(order.id) },
                )
              }
            }
      }
    }
  }
}

@Composable
private fun CgyyOrderCard(
    order: CgyyOrderDto,
    onOpenDetail: () -> Unit,
    onCancel: () -> Unit,
) {
  val status = order.displayStatus()
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text =
                listOf(order.venueName.orEmpty(), order.venueSpaceName ?: order.siteName.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" / "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = status.primaryText,
            color = status.color.toComposeColor(),
            fontWeight = FontWeight.Bold,
        )
      }

      Text(order.displayReservationDateText(), color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("主题：${order.theme.orEmpty()}", maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
          "活动类型：${order.purposeTypeName ?: order.purposeType?.toString().orEmpty()}",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (order.checkContent?.isNotBlank() == true && (order.checkStatus ?: 0) < 0) {
        Text("驳回原因：${order.checkContent}", color = MaterialTheme.colorScheme.error)
      }

      Spacer(modifier = Modifier.height(4.dp))
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        AssistChip(onClick = onOpenDetail, label = { Text("查看详情") })
        if (shouldShowCancelAction(order)) {
          AssistChip(onClick = onCancel, label = { Text("取消预约") })
        }
      }
    }
  }
}
