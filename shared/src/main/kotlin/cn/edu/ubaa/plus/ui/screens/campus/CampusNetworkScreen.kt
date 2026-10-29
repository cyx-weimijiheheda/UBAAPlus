package cn.edu.ubaa.plus.ui.screens.campus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.feature.CampusNetworkInfoDto
import cn.edu.ubaa.plus.api.feature.CampusTrafficDto
import cn.edu.ubaa.plus.ui.common.components.CampusOnlyNotice
import cn.edu.ubaa.plus.ui.common.components.UbaaPullToRefresh
import cn.edu.ubaa.plus.ui.common.components.UbaaToastEffect
import java.util.Locale

@Composable
fun CampusNetworkScreen(
    viewModel: CampusNetworkViewModel,
    onOpenUrl: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  LaunchedEffect(Unit) { viewModel.ensureLoaded() }

  UbaaToastEffect(state.error) {}

  Scaffold(
      contentWindowInsets =
          WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) { padding ->
    UbaaPullToRefresh(
        refreshing = state.isLoading,
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
              text = "流量与在线账号信息按当前校园网 IP 查询，需要连接北航 WiFi 或有线网；WebVPN 不能替代校园网。套餐与充值可打开自助服务网页。"
          )
        }

        if (
            state.isLoading && state.info == null && state.traffic == null && state.netFee == null
        ) {
          item {
            Box(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
        }

        state.error?.let { error ->
          item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
            ) {
              Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                OutlinedButton(onClick = viewModel::refresh) { Text("重试") }
              }
            }
          }
        }

        state.info?.let { info -> item { SrunInfoCard(info) } }
        state.traffic?.let { traffic -> item { TrafficCard(traffic) } }
        state.netFee?.let { fee -> item { StatCard(title = "网费余额", value = fee, unit = "元") } }

        item {
          Button(
              onClick = { onOpenUrl("https://zfw.buaa.edu.cn") },
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("套餐 / 充值（校园网自助服务）")
          }
        }
        item {
          Text(
              text = "校园网流量按当前在线 IP 查询；离开校园网时可能无法显示。套餐与充值在深澜自助服务中完成。",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun SrunInfoCard(info: CampusNetworkInfoDto) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(
          text = info.realName.ifBlank { info.userName },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
      if (info.onlineIp.isNotBlank()) {
        Text("在线 IP：${info.onlineIp}", style = MaterialTheme.typography.bodySmall)
      }
      if (info.productsName.isNotBlank()) {
        Text("当前套餐：${info.productsName}")
      }
      Text("已用流量：${formatBytes(info.sumBytes)}")
      Text("剩余流量：${formatBytes(info.remainBytes)}")
      Text("套餐余额：${formatMoney(info.userBalance)} 元")
      Text("电子钱包：${formatMoney(info.walletBalance)} 元")
    }
  }
}

@Composable
private fun TrafficCard(traffic: CampusTrafficDto) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text("流量明细", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      traffic.freeTotal?.let {
        Text("免费流量：${formatGb(it)} GB（剩余 ${formatGb(traffic.freeRemain ?: 0.0)} GB）")
      }
      traffic.giftTotal?.let {
        Text("赠送流量：${formatGb(it)} GB（剩余 ${formatGb(traffic.giftRemain ?: 0.0)} GB）")
      }
      traffic.paidUsage?.let { Text("计费流量：${formatGb(it)} GB") }
    }
  }
}

@Composable
private fun StatCard(title: String, value: String, unit: String) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
      Text(
          text = "$value $unit",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

private fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "0 GB"
  val gb = bytes / 1024.0 / 1024.0 / 1024.0
  return if (gb >= 1) "${formatGb(gb)} GB" else "${formatGb(bytes / 1024.0 / 1024.0)} MB"
}

private fun formatGb(value: Double): String = String.format(Locale.US, "%.2f", value)

private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)
