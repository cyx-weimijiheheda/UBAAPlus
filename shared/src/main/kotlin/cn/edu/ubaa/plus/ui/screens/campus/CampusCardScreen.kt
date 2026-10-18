package cn.edu.ubaa.plus.ui.screens.campus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import java.util.Locale

@Composable
fun CampusCardScreen(
    viewModel: CampusCardViewModel,
    onOpenUrl: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  LaunchedEffect(Unit) { viewModel.ensureLoaded() }

  Scaffold { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
              text = "校园卡",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f),
          )
          IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新")
          }
        }
      }

      if (state.isLoading && state.balance == null) {
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
                  CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
          ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
              Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
              OutlinedButton(onClick = viewModel::refresh) { Text("重试") }
            }
          }
        }
      }

      state.balance?.let { balance ->
        item {
          Card(
              modifier = Modifier.fillMaxWidth(),
              colors =
                  CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.primaryContainer
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
        Button(
            onClick = { onOpenUrl("https://pass.cc-pay.cn/mcampcard") },
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text("查看账单")
        }
      }
      item {
        OutlinedButton(
            onClick = { onOpenUrl("https://pass.cc-pay.cn/") },
            modifier = Modifier.fillMaxWidth(),
        ) {
          Text("充值（校园支付平台）")
        }
      }
      item {
        Text(
            text = "账单和充值在校园支付平台完成；本应用只展示余额，不处理支付。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f", value)
