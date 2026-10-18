package cn.edu.ubaa.plus.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.ubaa.plus.api.feature.LibraryLoanDto

@Composable
fun LibraryBorrowScreen(
    viewModel: LibraryBorrowViewModel,
    onOpenUrl: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsState()
  LaunchedEffect(Unit) { viewModel.ensureLoaded() }
  val loans =
      when (state.selectedTab) {
        LibraryBorrowTab.CURRENT -> state.currentLoans
        LibraryBorrowTab.HISTORY -> state.history
      }

  Scaffold { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text = "图书借阅",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
          Icon(Icons.Default.Refresh, contentDescription = "刷新")
        }
      }

      TabRow(selectedTabIndex = state.selectedTab.ordinal) {
        LibraryBorrowTab.entries.forEach { tab ->
          Tab(
              selected = state.selectedTab == tab,
              onClick = { viewModel.selectTab(tab) },
              text = { Text(tab.label) },
          )
        }
      }

      when {
        state.isLoading && loans.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
        state.error != null && loans.isEmpty() ->
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Card(
                  colors =
                      CardDefaults.cardColors(
                          containerColor = MaterialTheme.colorScheme.errorContainer
                      )
              ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                  Text(
                      text = state.error.orEmpty(),
                      color = MaterialTheme.colorScheme.onErrorContainer,
                  )
                  OutlinedButton(onClick = viewModel::refresh) { Text("重试") }
                }
              }
              OutlinedButton(
                  onClick = { onOpenUrl("https://space.lib.buaa.edu.cn/space/") },
                  modifier = Modifier.fillMaxWidth(),
              ) {
                Text("打开图书馆网站登录")
              }
            }
        loans.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                  text = if (state.selectedTab == LibraryBorrowTab.CURRENT) "暂无当前借阅" else "暂无借阅历史",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
        else ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(loans) { loan -> LibraryLoanCard(loan) }
              item {
                OutlinedButton(
                    onClick = { onOpenUrl("https://space.lib.buaa.edu.cn/space/") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                  Text("打开图书馆网站")
                }
              }
            }
      }
    }
  }
}

@Composable
private fun LibraryLoanCard(loan: LibraryLoanDto) {
  Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Text(
          text = loan.title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      if (loan.author.isNotBlank()) {
        Text(
            text = loan.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
      }
      if (loan.callNo.isNotBlank()) {
        Text("索书号：${loan.callNo}", style = MaterialTheme.typography.bodySmall)
      }
      if (loan.loanDate.isNotBlank()) {
        Text("借阅日期：${loan.loanDate}", style = MaterialTheme.typography.bodySmall)
      }
      if (loan.dueDate.isNotBlank()) {
        Text(
            text = "应还日期：${loan.dueDate}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
      }
      if (loan.renewTimes > 0) {
        Text("续借次数：${loan.renewTimes}", style = MaterialTheme.typography.bodySmall)
      }
      if (loan.status.isNotBlank()) {
        Text(loan.status, style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}
