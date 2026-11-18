package cn.edu.ubaa.plus.ui.screens.campus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.ui.common.components.UbaaButton
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaSecondaryButton
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect
import cn.edu.ubaa.plus.ui.theme.ubaaGlassSurface

@Composable
fun CampusCardScreen(
    viewModel: CampusCardViewModel,
    onOpenUrl: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  val consumption: CampusCardConsumptionViewModel =
      androidx.lifecycle.viewmodel.compose.viewModel { CampusCardConsumptionViewModel() }
  val trades by consumption.state.collectAsState()
  var pickingStart by remember { mutableStateOf<Boolean?>(null) }
  DisposableEffect(viewModel, consumption) {
    viewModel.consumptionRefresh = consumption::query
    onDispose { viewModel.consumptionRefresh = null }
  }
  LaunchedEffect(Unit) {
    viewModel.ensureLoaded()
    consumption.ensureLoaded()
  }
  pickingStart?.let { first ->
    ConsumptionDateDialog(
        first,
        trades.start,
        trades.end,
        cn.edu.ubaa.plus.api.feature.campusToday(),
        onDismiss = { pickingStart = null },
        onSelect = { date ->
          if (first) consumption.selectStart(date) else consumption.selectEnd(date)
          pickingStart = null
        },
    )
  }

  UbaaToastEffect(state.error) {}

  Scaffold(
      containerColor = androidx.compose.ui.graphics.Color.Transparent,
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) { padding ->
    UbaaPullToRefresh(
        refreshing = state.isLoading || trades.loading,
        onRefresh = {
          viewModel.refresh()
          consumption.query()
        },
        modifier = Modifier.padding(padding),
    ) {
      LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (state.isLoading && state.balance == null) {
          item {
            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  "正在加载校园卡余额...",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        state.error?.let { error ->
          item {
            Card(
                modifier = Modifier.ubaaGlassSurface(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
                    ),
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                UbaaSecondaryButton(onClick = viewModel::refresh) { Text("重试") }
              }
            }
          }
        }

        state.balance?.let { balance ->
          item {
            Card(
                modifier = Modifier.ubaaGlassSurface().fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    ),
            ) {
              Column(
                  modifier = Modifier.fillMaxWidth().padding(20.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                    text = "卡余额",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${balance.value?.let { formatMoney(it) } ?: "—"} ${balance.unit}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                balance.unclaimedAmount?.let {
                  Text(
                      text = "未领取金额：${formatMoney(it)} 元",
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }
          }
        }

        item {
          UbaaButton(
              onClick = { onOpenUrl("https://pass.cc-pay.cn/mcampcard") },
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("校园卡管理（官网）")
          }
        }
        item {
          UbaaSecondaryButton(
              onClick = { onOpenUrl("https://pass.cc-pay.cn/") },
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("充值（校园支付平台）")
          }
        }
        item {
          UbaaSecondaryButton(
              onClick = {
                onOpenUrl("https://m.icw.buaa.edu.cn/card/#/pages/homepage/index/index?usertype=1")
              },
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("校园卡掌上服务大厅")
          }
        }
        item {
          Text(
              text = "消费记录可在下方查询，充值等操作在校园支付平台完成。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        campusConsumptionItems(trades, consumption) { pickingStart = it }
      }
    }
  }
}

private fun formatMoney(value: Double): String = cn.edu.ubaa.plus.util.formatMoney(value)
