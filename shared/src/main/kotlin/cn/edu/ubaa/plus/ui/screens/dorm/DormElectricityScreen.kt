package cn.edu.ubaa.plus.ui.screens.dorm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.model.dto.DormMeterInfoDto
import cn.edu.ubaa.plus.model.dto.DormTopUpDto
import cn.edu.ubaa.plus.model.format.formatUbaaDateTime
import cn.edu.ubaa.plus.ui.common.components.CampusOnlyNotice
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect

@Composable
fun DormElectricityScreen(
    viewModel: DormElectricityViewModel,
    onOpenUrl: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  var showConfirmDialog by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { viewModel.ensureLoaded() }
  LaunchedEffect(state.rechargeError) {
    state.rechargeError?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.clearRechargeError()
    }
  }
  LaunchedEffect(state.payUrl) {
    state.payUrl?.let { url ->
      onOpenUrl(url)
      viewModel.consumePayUrl()
    }
  }

  if (showConfirmDialog && state.meterInfo != null) {
    DormRechargeConfirmDialog(
        state = state,
        onDismiss = { showConfirmDialog = false },
        onConfirm = {
          showConfirmDialog = false
          viewModel.recharge()
        },
    )
  }

  UbaaToastEffect(state.roomsError) {}
  UbaaToastEffect(state.queryError) {}

  Scaffold(
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
      snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { padding ->
    UbaaPullToRefresh(
        refreshing = state.roomsLoading || state.isQuerying,
        onRefresh = viewModel::refresh,
        modifier = Modifier.padding(padding),
    ) {
      LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          CampusOnlyNotice(
              text = "寝室用电查询与购电需要连接校园网（北航 WiFi 或有线网）。校外访问时服务通常返回 302 跳转，查询会失败；请连接校园网后重试。"
          )
        }

        item {
          DormQueryCard(
              state = state,
              onCampusSelected = viewModel::selectCampus,
              onBuildingSelected = viewModel::selectBuilding,
              onRoomSelected = viewModel::selectRoom,
              onMeterSelected = viewModel::selectMeter,
              onReloadRooms = viewModel::loadRooms,
              onQuery = viewModel::query,
          )
        }

        if (state.isQuerying) {
          item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        state.queryError?.let { error ->
          item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = viewModel::query) { Text("重试") }
              }
            }
          }
        }

        state.meterInfo?.let { info ->
          item {
            DormMeterCard(
                info = info,
                onContinuePay = { info.payUrl?.let(onOpenUrl) },
            )
          }
          item {
            DormRechargeCard(
                state = state,
                onPowerChange = viewModel::updateRechargePower,
                onRechargeClick = { showConfirmDialog = true },
            )
          }
          if (state.topUps.isNotEmpty()) {
            item {
              Text(
                  text = "最近购电记录",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
              )
            }
            items(state.topUps) { topUp -> DormTopUpRow(topUp) }
          }
        }
      }
    }
  }
}

@Composable
private fun DormQueryCard(
    state: DormElectricityUiState,
    onCampusSelected: (String) -> Unit,
    onBuildingSelected: (String) -> Unit,
    onRoomSelected: (String) -> Unit,
    onMeterSelected: (Int) -> Unit,
    onReloadRooms: () -> Unit,
    onQuery: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "寝室用电查询",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
      }
      if (state.roomsLoading && state.rooms.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      } else if (state.roomsError != null && state.rooms.isEmpty()) {
        Text(
            text = state.roomsError,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onReloadRooms) { Text("重新加载") }
      } else {
        DormDropdown(
            label = "校区",
            selectedText = state.selectedCampus.ifBlank { "选择校区" },
            options = state.campusOptions.map { it to it },
            onSelected = onCampusSelected,
        )
        DormDropdown(
            label = "宿舍楼",
            selectedText = state.selectedBuilding.ifBlank { "选择宿舍楼" },
            options = state.buildingOptions.map { it to it },
            onSelected = onBuildingSelected,
        )
        DormDropdown(
            label = "房间",
            selectedText = state.selectedRoom.ifBlank { "选择房间" },
            options = state.roomOptions.map { it to it },
            onSelected = onRoomSelected,
        )
        if (state.meterOptions.size > 1) {
          DormDropdown(
              label = "电表",
              selectedText = state.selectedMeterLabel.ifBlank { "选择电表" },
              options =
                  state.meterOptions.map { meter ->
                    meter.id to "${meter.meterNo} · ${meter.name.ifBlank { meter.address }}"
                  },
              onSelected = onMeterSelected,
          )
        }
        Button(
            onClick = onQuery,
            enabled = state.selectedMeterId != null && !state.isQuerying,
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text("查询")
        }
      }
    }
  }
}

@Composable
private fun DormMeterCard(info: DormMeterInfoDto, onContinuePay: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
          text = info.address.ifBlank { "${info.building} ${info.room}" },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
      Text(
          text = "剩余电量：${formatNumber(info.remain)} 度",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
      )
      Text("电价：${formatNumber(info.price)} 元/度")
      Text("抄表时间：${formatUbaaDateTime(info.readingTime).ifBlank { "未知" }}")
      Text("加电状态：${payStatusText(info.payStatus)}")
      if (!info.payUrl.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = onContinuePay) { Text("继续支付未完成订单") }
      }
    }
  }
}

@Composable
private fun DormRechargeCard(
    state: DormElectricityUiState,
    onPowerChange: (String) -> Unit,
    onRechargeClick: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("购电", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      OutlinedTextField(
          value = state.rechargePowerText,
          onValueChange = onPowerChange,
          label = { Text("购电量（度）") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth(),
      )
      state.estimatedAmount?.let { amount ->
        Text(
            text = "预计金额：${formatNumber(amount)} 元（下发电量 ${state.dispatchedPower ?: 0} 度）",
            style = MaterialTheme.typography.bodyMedium,
        )
      }
      Button(
          onClick = onRechargeClick,
          enabled = state.canRecharge,
          modifier = Modifier.fillMaxWidth(),
      ) {
        if (state.isRecharging) {
          CircularProgressIndicator(
              modifier = Modifier.height(18.dp).width(18.dp),
              strokeWidth = 2.dp,
          )
          Spacer(modifier = Modifier.width(8.dp))
        }
        Text("购电")
      }
      Text(
          text = "确认后会跳转到校园支付平台完成付款；本应用不会自动扣款。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun DormRechargeConfirmDialog(
    state: DormElectricityUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
  val info = state.meterInfo ?: return
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("确认购电") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("地址：${info.address.ifBlank { "${info.building} ${info.room}" }}")
          Text("购电量：${state.dispatchedPower ?: 0} 度")
          Text("预计金额：${formatNumber(state.estimatedAmount ?: 0.0)} 元")
          Text("确认后将打开校园支付平台。")
        }
      },
      confirmButton = { TextButton(onClick = onConfirm) { Text("去支付") } },
      dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
  )
}

@Composable
private fun DormTopUpRow(topUp: DormTopUpDto) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
            formatUbaaDateTime(topUp.date).ifBlank { "未知时间" },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "电量 ${topUp.power} 度 · ${topUp.operator}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
          text = topUp.amount,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun <T> DormDropdown(
    label: String,
    selectedText: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(
        onClick = { expanded = true },
        enabled = options.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
          text = "$label：$selectedText",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
      )
      Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { (value, text) ->
        DropdownMenuItem(
            text = { Text(text) },
            onClick = {
              expanded = false
              onSelected(value)
            },
        )
      }
    }
  }
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format("%.2f", value)

private fun payStatusText(status: Int): String =
    when (status) {
      0 -> "正在加电"
      1 -> "加电成功"
      2 -> "等待加电"
      else -> "状态未知"
    }
